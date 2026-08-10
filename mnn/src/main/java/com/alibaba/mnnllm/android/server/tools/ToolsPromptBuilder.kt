// Request-side of the tools adaptation layer: renders OpenAI tools schemas into the
// prompt (system prefix injection + output format convention) and converts OpenAI
// message roles that the MNN engine does not understand (role=tool, assistant
// tool_calls) into model-readable text.
//
// The conventions here follow the text tool-calling style used by Qwen-family models
// (```tool_call fenced JSON blocks), which gives instruct models the best chance of
// emitting parseable tool calls.

package com.alibaba.mnnllm.android.server.tools

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object ToolsPromptBuilder {

    private val gson = Gson()

    const val TOOL_CALL_FENCE = "tool_call"

    /**
     * Renders the system prefix that describes available tools and the expected output
     * format. Injected at the head of the system message when a request carries tools.
     */
    fun renderToolSystemPrefix(tools: JsonArray): String {
        return buildString {
            appendLine("# Tools")
            appendLine()
            appendLine("You have access to the following functions. Their definitions (JSON Schema) are:")
            appendLine()
            appendLine("```json")
            appendLine(gson.toJson(tools))
            appendLine("```")
            appendLine()
            appendLine("## How to call tools")
            appendLine("When you need to call a tool, output EXACTLY this format, one fenced block per call:")
            appendLine()
            appendLine("```$TOOL_CALL_FENCE")
            appendLine("""{"name": "function_name", "arguments": {"key": "value"}}""")
            appendLine("```")
            appendLine()
            appendLine("Rules:")
            appendLine("1. When calling a tool, output ONLY the $TOOL_CALL_FENCE block(s) and nothing else.")
            appendLine("2. \"arguments\" MUST be a valid JSON object matching the function's parameters schema.")
            appendLine("3. Never fabricate tool results; wait for the tool result before answering.")
            appendLine("4. If no tool is needed, answer the user directly without any tool block.")
        }.trimEnd()
    }

    /**
     * Converts an OpenAI message list into (role, content) pairs consumable by the MNN
     * engine. Roles understood by the engine: system / user / assistant. Anything else
     * (role=tool, assistant tool_calls) is rendered into readable text.
     */
    fun toPromptItems(messages: List<ChatMessage>, tools: JsonArray? = null): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        val toolPrefix = tools?.takeIf { it.size() > 0 }?.let { renderToolSystemPrefix(it) }

        messages.forEach { message ->
            when (message.role.lowercase()) {
                "system" -> {
                    val body = message.content.orEmpty()
                    val merged = when {
                        toolPrefix != null && body.isBlank() -> toolPrefix
                        toolPrefix != null -> "$toolPrefix\n\n$body"
                        else -> body
                    }
                    if (merged.isNotBlank()) {
                        // Merge consecutive system messages into one to keep the
                        // engine's chat template happy.
                        if (items.isNotEmpty() && items.last().first == "system") {
                            items[items.lastIndex] = "system" to (items.last().second + "\n\n" + merged)
                        } else {
                            items.add("system" to merged)
                        }
                    }
                }

                "tool" -> {
                    items.add("user" to renderToolResult(message))
                }

                "assistant" -> {
                    val text = renderAssistantMessage(message)
                    if (text.isNotBlank()) {
                        items.add("assistant" to text)
                    }
                }

                else -> {
                    // user and any unknown role
                    val text = message.content.orEmpty()
                    if (text.isNotBlank()) {
                        items.add("user" to text)
                    }
                }
            }
        }

        // If tools were provided but the conversation has no system message at all,
        // inject the tool prefix as a leading system message.
        if (toolPrefix != null && items.none { it.first == "system" }) {
            items.add(0, "system" to toolPrefix)
        }

        return items
    }

    /** Renders a role=tool message as model-readable text. */
    fun renderToolResult(message: ChatMessage): String {
        return buildString {
            append("[Tool result")
            message.name?.let { append(" of ").append(it) }
            message.toolCallId?.let { append(" (call id: ").append(it).append(")") }
            appendLine("]")
            append(message.content.orEmpty())
        }
    }

    /**
     * Renders an assistant message, including its tool_calls (if any), back into the
     * same marker format we ask the model to produce. This keeps multi-turn tool
     * conversations consistent with the instructed output format.
     */
    fun renderAssistantMessage(message: ChatMessage): String {
        val calls = message.toolCalls.orEmpty()
        val content = message.content.orEmpty()
        if (calls.isEmpty()) return content
        return buildString {
            if (content.isNotBlank()) {
                appendLine(content)
                appendLine()
            }
            calls.forEach { call ->
                appendLine("```$TOOL_CALL_FENCE")
                appendLine(renderCallJson(call.id, call.name, call.argumentsJson))
                appendLine("```")
            }
        }.trimEnd()
    }

    /** Builds the canonical JSON marker body for one tool call. */
    fun renderCallJson(id: String?, name: String, argumentsJson: String): String {
        val obj = JsonObject()
        if (!id.isNullOrBlank()) {
            obj.addProperty("id", id)
        }
        obj.addProperty("name", name)
        obj.add("arguments", parseLenient(argumentsJson))
        return gson.toJson(obj)
    }

    /** Parses JSON leniently; falls back to a JSON string element on failure. */
    internal fun parseLenient(json: String): JsonElement {
        return try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            com.google.gson.JsonPrimitive(json)
        }
    }
}
