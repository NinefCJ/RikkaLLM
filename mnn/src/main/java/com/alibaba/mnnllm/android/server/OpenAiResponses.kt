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

    fun contentChunk(id: String, model: String, created: Long, text: String): String {
        val delta = JsonObject().apply { addProperty("content", text) }
        return chunkShell(id, model, created, delta, null)
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
        val usage = JsonObject().apply {
            addProperty("prompt_tokens", stats.promptTokens)
            addProperty("completion_tokens", stats.completionTokens)
            addProperty("total_tokens", stats.promptTokens + stats.completionTokens)
        }
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
