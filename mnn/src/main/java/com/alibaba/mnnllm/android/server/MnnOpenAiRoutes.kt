// Ktor routing for the local OpenAI-compatible API. Kept as a thin layer over the
// JVM-testable ChatOrchestrator / OpenAiResponses / RequestTranslator pieces.

package com.alibaba.mnnllm.android.server

import com.alibaba.mnnllm.android.server.tools.ToolStreamEvent
import com.google.gson.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                call.respondError(HttpStatusCode.Unauthorized, "Invalid or missing bearer token", "authentication_error")
                return@get
            }
            call.respondText(
                OpenAiResponses.modelsList(backend.modelId),
                contentType = ContentType.Application.Json,
            )
        }

        post("/v1/chat/completions") {
            if (!call.isAuthorized(backend)) {
                call.respondError(HttpStatusCode.Unauthorized, "Invalid or missing bearer token", "authentication_error")
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
    val result = withContext(Dispatchers.IO) { orchestrator.complete(request) }
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
        val events = Channel<ToolStreamEvent>(Channel.UNLIMITED)
        var finishReason = "stop"
        coroutineScope {
            val producer = launch(Dispatchers.IO) {
                runCatching {
                    orchestrator.stream(request, { events.trySend(it) }, { aborted.get() })
                }
                events.close()
            }

            try {
                for (event in events) {
                    when (event) {
                        is ToolStreamEvent.Text ->
                            writeStringUtf8("data: ${OpenAiResponses.contentChunk(completionId, model, created, event.text)}\n\n")

                        is ToolStreamEvent.ToolCall ->
                            writeStringUtf8(
                                "data: ${
                                    OpenAiResponses.toolCallChunk(completionId, model, created, event.index, event.id, event.name, event.arguments)
                                }\n\n"
                            )

                        is ToolStreamEvent.Finish.ToolCalls -> finishReason = "tool_calls"
                        is ToolStreamEvent.Finish.Stop -> Unit
                    }
                }
                writeStringUtf8("data: ${OpenAiResponses.finishChunk(completionId, model, created, finishReason)}\n\n")
                writeStringUtf8("data: [DONE]\n\n")
            } catch (e: kotlinx.coroutines.CancellationException) {
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
    if (!header.startsWith("Bearer ")) return false
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

private suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
    type: String,
) {
    respondText(OpenAiResponses.errorBody(message, type), contentType = ContentType.Application.Json, status = status)
}
