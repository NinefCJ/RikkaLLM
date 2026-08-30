// Translates raw OpenAI chat completions request JSON into the normalized
// ChatCompletionRequest used by the adaptation layer. Pure Kotlin + Gson so it can be
// unit tested on the JVM.

package com.alibaba.mnnllm.android.server

import com.alibaba.mnnllm.android.server.tools.ChatMessage
import com.alibaba.mnnllm.android.server.tools.ToolCallRef
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class ChatCompletionRequest(
    val model: String?,
    val messages: List<ChatMessage>,
    val tools: JsonArray?,
    val stream: Boolean,
    val temperature: Float?,
    val topP: Float?,
    val maxTokens: Int?,
)

object RequestTranslator {

    private val gson = Gson()

    /** Parses a request body; throws [IllegalArgumentException] when it is unusable. */
    fun parse(body: JsonObject): ChatCompletionRequest {
        val messagesElement = body["messages"]
        if (messagesElement == null || !messagesElement.isJsonArray || messagesElement.asJsonArray.size() == 0) {
            throw IllegalArgumentException("'messages' must be a non-empty array")
        }
        val messages = messagesElement.asJsonArray.map { parseMessage(it) }

        // tool_choice: "none" disables tools entirely; other values are treated as auto
        // since the text-based adapter cannot hard-force a specific function.
        var tools = body["tools"]?.takeIf { it.isJsonArray }?.asJsonArray?.takeIf { it.size() > 0 }
        val toolChoice = body["tool_choice"]
        if (toolChoice != null && toolChoice.isJsonPrimitive && toolChoice.asString == "none") {
            tools = null
        }

        return ChatCompletionRequest(
            model = body["model"]?.takeIf { it.isJsonPrimitive }?.asString,
            messages = messages,
            tools = tools,
            stream = body["stream"]?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            temperature = body["temperature"]?.takeIf { it.isJsonPrimitive }?.asFloat,
            topP = body["top_p"]?.takeIf { it.isJsonPrimitive }?.asFloat,
            maxTokens = body["max_tokens"]?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
                ?: body["max_completion_tokens"]?.takeIf { it.isJsonPrimitive }?.asInt,
        )
    }

    private fun parseMessage(element: JsonElement): ChatMessage {
        if (!element.isJsonObject) throw IllegalArgumentException("each message must be an object")
        val obj = element.asJsonObject
        val role = obj["role"]?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("message is missing 'role'")
        return ChatMessage(
            role = role,
            content = extractContent(obj["content"]),
            name = obj["name"]?.takeIf { it.isJsonPrimitive }?.asString,
            toolCallId = obj["tool_call_id"]?.takeIf { it.isJsonPrimitive }?.asString,
            toolCalls = obj["tool_calls"]?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { parseToolCall(it) },
            images = extractImages(obj["content"]),
        )
    }

    /**
     * Collects `image_url` parts from a content array. Both the OpenAI object form
     * (`{"type":"image_url","image_url":{"url":...}}`) and a bare `{"type":"image_url","url":...}`
     * are accepted; the previous implementation silently dropped these parts.
     */
    private fun extractImages(element: JsonElement?): List<String> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { part ->
            if (!part.isJsonObject) return@mapNotNull null
            val obj = part.asJsonObject
            if (obj["type"]?.takeIf { it.isJsonPrimitive }?.asString != "image_url") return@mapNotNull null
            val url = obj["image_url"]?.let { imageUrl ->
                when {
                    imageUrl.isJsonPrimitive -> imageUrl.asString
                    imageUrl.isJsonObject -> imageUrl.asJsonObject["url"]
                        ?.takeIf { it.isJsonPrimitive }?.asString

                    else -> null
                }
            } ?: obj["url"]?.takeIf { it.isJsonPrimitive }?.asString
            url?.takeIf { it.isNotBlank() }
        }
    }

    /** content may be a string or an array of typed parts; text parts are joined. */
    private fun extractContent(element: JsonElement?): String? {
        return when {
            element == null || element.isJsonNull -> null
            element.isJsonPrimitive -> element.asString
            element.isJsonArray -> element.asJsonArray.mapNotNull { part ->
                when {
                    part.isJsonPrimitive -> part.asString
                    part.isJsonObject -> {
                        val p = part.asJsonObject
                        val type = p["type"]?.takeIf { it.isJsonPrimitive }?.asString
                        if (type == "text") p["text"]?.takeIf { it.isJsonPrimitive }?.asString else null
                    }

                    else -> null
                }
            }.joinToString("\n").ifEmpty { null }

            else -> null
        }
    }

    private fun parseToolCall(element: JsonElement): ToolCallRef? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val fn = obj["function"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val name = fn["name"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val args = fn["arguments"] ?: return ToolCallRef(
            id = obj["id"]?.takeIf { it.isJsonPrimitive }?.asString,
            name = name,
            argumentsJson = "{}",
        )
        val argsJson = when {
            args.isJsonPrimitive && args.asJsonPrimitive.isString -> args.asString
            else -> gson.toJson(args)
        }
        return ToolCallRef(
            id = obj["id"]?.takeIf { it.isJsonPrimitive }?.asString,
            name = name,
            argumentsJson = argsJson,
        )
    }
}
