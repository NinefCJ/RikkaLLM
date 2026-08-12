// Ktor routing for the local OpenAI-compatible API. Kept as a thin layer over the
// JVM-testable ChatOrchestrator / OpenAiResponses / RequestTranslator pieces.

package com.alibaba.mnnllm.android.server

import android.util.Log
import com.alibaba.mnnllm.android.server.tools.ToolStreamEvent
import com.google.gson.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MnnOpenAiRoutes"

/** Everything the HTTP layer needs from the host app / engine manager. */
interface MnnServerBackend {
    /** Bearer token required on every request. */
    val token: String

    /** Model id exposed through the API, e.g. "mnn-local". */
    val modelId: String

    val engine: MnnEngine

    /** Single-generation mutex: returns false when another request is in flight. */
    fun tryAcquireGeneration(): Boolean

    fun releaseGeneration()
}

fun Application.mnnOpenAiRoutes(backend: MnnServerBackend) {
    val orchestrator = ChatOrchestrator(backend.engine)

    routing {
        get("/v1/models") {
            if (!call.isAuthorized(backend)) {
                call.respondUnauthorized()
                return@get
            }
            call.respondText(
                OpenAiResponses.modelsList(backend.modelId),
                contentType = ContentType.Application.Json,
            )
        }

        post("/v1/chat/completions") {
            if (!call.isAuthorized(backend)) {
                call.respondUnauthorized()
                return@post
            }

            val request = try {
                RequestTranslator.parse(JsonParser.parseString(call.receiveText()).asJsonObject)
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.BadRequest, "Malformed request body: ${e.message}", "invalid_request_error")
                return@post
            }

            if (backend.engine.loadedModel == null) {
                call.respondError(HttpStatusCode.ServiceUnavailable, "No local model is loaded", "model_not_loaded")
                return@post
            }
            if (!backend.tryAcquireGeneration()) {
                call.respondError(HttpStatusCode.TooManyRequests, "The local model engine is busy with another request", "engine_busy")
                return@post
            }

            try {
                if (request.stream) {
                    handleStream(orchestrator, backend, request)
                } else {
                    handleFull(orchestrator, backend, request)
                }
            } finally {
                backend.releaseGeneration()
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.handleFull(
    orchestrator: ChatOrchestrator,
    backend: MnnServerBackend,
    request: ChatCompletionRequest,
) {
    // Engine exceptions must surface as an OpenAI error envelope, never as a bare
    // 500 text/plain response escaping from the route.
    val result = try {
        withContext(Dispatchers.IO) { orchestrator.complete(request) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "non-stream generation failed", e)
        when (e) {
            is ModelNotLoadedException ->
                call.respondError(HttpStatusCode.ServiceUnavailable, "No local model is loaded", "model_not_loaded")

            is EngineBusyException ->
                call.respondError(HttpStatusCode.TooManyRequests, "The local model engine is busy with another request", "engine_busy")

            else ->
                call.respondError(HttpStatusCode.InternalServerError, "Engine generation failed: ${e.message}", "engine_error")
        }
        return
    }
    call.respondText(
        OpenAiResponses.fullCompletion(
            id = OpenAiResponses.completionId(),
            model = request.model ?: backend.modelId,
            created = System.currentTimeMillis() / 1000,
            content = result.content,
            toolCalls = result.toolCalls,
            finishReason = result.finishReason,
            stats = result.stats,
        ),
        contentType = ContentType.Application.Json,
    )
}

/** Signals flowing from the engine producer coroutine to the SSE writer. */
private sealed class StreamSignal {
    class Event(val event: ToolStreamEvent) : StreamSignal()
    class Failed(val error: Throwable) : StreamSignal()
}

private suspend fun io.ktor.server.routing.RoutingContext.handleStream(
    orchestrator: ChatOrchestrator,
    backend: MnnServerBackend,
    request: ChatCompletionRequest,
) {
    val completionId = OpenAiResponses.completionId()
    val model = request.model ?: backend.modelId
    val created = System.currentTimeMillis() / 1000

    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeStringUtf8("data: ${OpenAiResponses.roleChunk(completionId, model, created)}\n\n")

        val aborted = AtomicBoolean(false)
        val statsRef = AtomicReference<GenerationStats?>(null)
        val signals = Channel<StreamSignal>(Channel.UNLIMITED)
        var finishReason = "stop"
        var engineError: Throwable? = null
        coroutineScope {
            val producer = launch(Dispatchers.IO) {
                try {
                    val stats = orchestrator.stream(request, { signals.trySend(StreamSignal.Event(it)) }, { aborted.get() })
                    statsRef.set(stats)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Engine failures must not be swallowed into a fake "successful
                    // empty" stream: forward them as an explicit failure signal.
                    Log.e(TAG, "stream generation failed", e)
                    signals.trySend(StreamSignal.Failed(e))
                } finally {
                    signals.close()
                }
            }

            try {
                for (signal in signals) {
                    when (signal) {
                        is StreamSignal.Event -> when (signal.event) {
                            is ToolStreamEvent.Text ->
                                writeStringUtf8("data: ${OpenAiResponses.contentChunk(completionId, model, created, signal.event.text)}\n\n")

                            is ToolStreamEvent.ToolCall ->
                                writeStringUtf8(
                                    "data: ${
                                        OpenAiResponses.toolCallChunk(completionId, model, created, signal.event.index, signal.event.id, signal.event.name, signal.event.arguments)
                                    }\n\n"
                                )

                            is ToolStreamEvent.Finish.ToolCalls -> finishReason = "tool_calls"
                            is ToolStreamEvent.Finish.Length -> finishReason = "length"
                            is ToolStreamEvent.Finish.Stop -> Unit
                        }

                        is StreamSignal.Failed -> engineError = signal.error
                    }
                }
                if (engineError == null) {
                    writeStringUtf8("data: ${OpenAiResponses.finishChunk(completionId, model, created, finishReason)}\n\n")
                    // Local-engine extension: surface prefill/decode timing in a dedicated
                    // usage chunk so SSE clients can render the per-message performance
                    // indicator (Prefill/Decode duration + tokens/s).
                    statsRef.get()?.let { stats ->
                        writeStringUtf8("data: ${OpenAiResponses.usageChunk(completionId, model, created, stats)}\n\n")
                    }
                    writeStringUtf8("data: [DONE]\n\n")
                } else {
                    // Chunks already produced stay as-is; the stream terminates with an
                    // error event instead of pretending to finish_reason=stop.
                    writeStringUtf8(
                        "data: ${OpenAiResponses.errorBody("Engine generation failed: ${engineError!!.message}", "engine_error")}\n\n"
                    )
                    writeStringUtf8("data: [DONE]\n\n")
                }
            } catch (e: CancellationException) {
                // Client disconnected: stop the engine as soon as possible.
                aborted.set(true)
                producer.cancel()
                throw e
            } catch (e: Exception) {
                aborted.set(true)
                producer.cancel()
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.isAuthorized(backend: MnnServerBackend): Boolean {
    val header = request.header(HttpHeaders.Authorization) ?: return false
    // RFC 7235: auth-schemes are case-insensitive ("bearer <token>" must work too).
    if (!header.startsWith("Bearer ", ignoreCase = true)) return false
    return constantTimeEquals(header.substring("Bearer ".length).trim(), backend.token)
}

private fun constantTimeEquals(a: String, b: String): Boolean {
    if (b.isEmpty()) return false
    var diff = a.length xor b.length
    val n = minOf(a.length, b.length)
    for (i in 0 until n) {
        diff = diff or (a[i].code xor b[i].code)
    }
    return diff == 0
}

private suspend fun io.ktor.server.application.ApplicationCall.respondUnauthorized() {
    response.header(HttpHeaders.WWWAuthenticate, "Bearer")
    respondText(
        OpenAiResponses.errorBody("Invalid or missing bearer token", "authentication_error"),
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.Unauthorized,
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
    type: String,
) {
    respondText(OpenAiResponses.errorBody(message, type), contentType = ContentType.Application.Json, status = status)
}
