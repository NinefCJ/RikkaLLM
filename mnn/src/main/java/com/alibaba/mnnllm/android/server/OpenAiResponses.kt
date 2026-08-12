// Pure JSON builders for OpenAI-compatible responses (stream chunks, full completions,
// models list, error envelopes). Built with Gson, JVM-testable without Ktor.

package com.alibaba.mnnllm.android.server

import com.alibaba.mnnllm.android.server.tools.ParsedToolCall
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

object OpenAiResponses {

    private val gson = Gson()

    fun completionId(): String = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)

    /**
     * Escapes a string as a JSON string literal (including the surrounding quotes).
     * Used by the per-token [contentChunk] path so we can build the SSE payload with
     * plain string concatenation instead of allocating a Gson object graph every token.
     */
    private fun jsonEscape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') sb.append("\\u%04x".format(c.code))
                    else sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // streaming chunks
    // ------------------------------------------------------------------

    private fun chunkShell(id: String, model: String, created: Long, delta: JsonObject, finishReason: String?): String {
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
            if (finishReason != null) addProperty("finish_reason", finishReason) else add("finish_reason", null)
        }
        val root = JsonObject().apply {
            addProperty("id", id)
            addProperty("object", "chat.completion.chunk")
            addProperty("created", created)
            addProperty("model", model)
            add("choices", JsonArray().apply { add(choice) })
        }
        return gson.toJson(root)
    }

    fun roleChunk(id: String, model: String, created: Long): String {
        val delta = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", "")
        }
        return chunkShell(id, model, created, delta, null)
    }

    /**
     * Delta carrying `delta.content`. This is invoked once per streamed token, so it
     * builds the JSON string directly (via [jsonEscape]) instead of allocating a Gson
     * JsonObject/JsonArray graph on every token, cutting GC pressure on the hot path.
     */
    fun contentChunk(id: String, model: String, created: Long, text: String): String {
        return (
                "{\"id\":" + jsonEscape(id) +
                ",\"object\":\"chat.completion.chunk\"" +
                ",\"created\":" + created +
                ",\"model\":" + jsonEscape(model) +
                ",\"choices\":[{\"index\":0,\"delta\":{\"content\":" + jsonEscape(text) +
                "},\"finish_reason\":null}]}"
                )
    }

    /**
     * delta.tool_calls entry. Our parser reports a call only once complete, so each
     * call is emitted as one chunk carrying index + id + name + full arguments.
     */
    fun toolCallChunk(id: String, model: String, created: Long, index: Int, callId: String, name: String, arguments: String): String {
        val function = JsonObject().apply {
            addProperty("name", name)
            addProperty("arguments", arguments)
        }
        val entry = JsonObject().apply {
            addProperty("index", index)
            addProperty("id", callId)
            addProperty("type", "function")
            add("function", function)
        }
        val delta = JsonObject().apply {
            add("tool_calls", JsonArray().apply { add(entry) })
        }
        return chunkShell(id, model, created, delta, null)
    }

    fun finishChunk(id: String, model: String, created: Long, finishReason: String): String {
        return chunkShell(id, model, created, JsonObject(), finishReason)
    }

    /**
     * Builds the `usage` object for the local-engine telemetry extension. Returns the
     * standard OpenAI token counters plus prefill/decode timing and peak memory, so the
     * host UI (RikkaHub's per-message indicator) can render
     * "Prefill: 1.33s, 43 tokens, 32.34 tokens/s · Decode: 43.46s, 479 tokens,
     * 11.02 tokens/s · Peak: 2.3 GB".
     */
    private fun usageObject(stats: GenerationStats): JsonObject = JsonObject().apply {
        addProperty("prompt_tokens", stats.promptTokens)
        addProperty("completion_tokens", stats.completionTokens)
        addProperty("total_tokens", stats.promptTokens + stats.completionTokens)
        addProperty("prefill_ms", stats.prefillMs)
        addProperty("decode_ms", stats.decodeMs)
        stats.prefillTokensPerSecond?.let { addProperty("prefill_tokens_per_second", it) }
        stats.decodeTokensPerSecond?.let { addProperty("decode_tokens_per_second", it) }
        // Peak resident set size during inference (KiB). The standard OpenAI `usage`
        // block reports bytes via `prompt_tokens_details` only; we keep our KiB field
        // simple and self-describing.
        addProperty("peak_memory_kb", stats.memoryKb)
    }

    /**
     * Final streaming chunk carrying `usage` only. Sent after [finishChunk] so SSE
     * clients that follow the OpenAI convention of a `usage` chunk with empty
     * choices also receive the local-engine prefill/decode timing extension.
     */
    fun usageChunk(id: String, model: String, created: Long, stats: GenerationStats): String {
        val root = JsonObject().apply {
            addProperty("id", id)
            addProperty("object", "chat.completion.chunk")
            addProperty("created", created)
            addProperty("model", model)
            add("choices", JsonArray())
            add("usage", usageObject(stats))
        }
        return gson.toJson(root)
    }

    // ------------------------------------------------------------------
    // non-stream completion
    // ------------------------------------------------------------------

    fun fullCompletion(
        id: String,
        model: String,
        created: Long,
        content: String?,
        toolCalls: List<ParsedToolCall>,
        finishReason: String,
        stats: GenerationStats,
    ): String {
        val message = JsonObject().apply {
            addProperty("role", "assistant")
            if (content != null) addProperty("content", content) else add("content", null)
            if (toolCalls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    toolCalls.forEach { call ->
                        add(JsonObject().apply {
                            addProperty("id", call.id)
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", call.name)
                                addProperty("arguments", call.argumentsJson)
                            })
                        })
                    }
                })
            }
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("message", message)
            addProperty("finish_reason", finishReason)
        }
        val usage = usageObject(stats)
        val root = JsonObject().apply {
            addProperty("id", id)
            addProperty("object", "chat.completion")
            addProperty("created", created)
            addProperty("model", model)
            add("choices", JsonArray().apply { add(choice) })
            add("usage", usage)
        }
        return gson.toJson(root)
    }

    // ------------------------------------------------------------------
    // misc
    // ------------------------------------------------------------------

    fun modelsList(modelId: String): String {
        val entry = JsonObject().apply {
            addProperty("id", modelId)
            addProperty("object", "model")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("owned_by", "rikkallm-mnn")
        }
        val root = JsonObject().apply {
            addProperty("object", "list")
            add("data", JsonArray().apply { add(entry) })
        }
        return gson.toJson(root)
    }

    fun errorBody(message: String, type: String, code: String? = null): String {
        val error = JsonObject().apply {
            addProperty("message", message)
            addProperty("type", type)
            if (code != null) addProperty("code", code)
        }
        return gson.toJson(JsonObject().apply { add("error", error) })
    }
}
