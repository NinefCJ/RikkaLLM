package com.alibaba.mnnllm.android.server.tools

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsPromptBuilderTest {

    private val tools = JsonParser.parseString(
        """
        [
          {
            "type": "function",
            "function": {
              "name": "get_weather",
              "description": "Get current weather",
              "parameters": {
                "type": "object",
                "properties": { "city": { "type": "string" } },
                "required": ["city"]
              }
            }
          }
        ]
        """.trimIndent()
    ).asJsonArray

    @Test
    fun `system prefix contains schema and format convention`() {
        val prefix = ToolsPromptBuilder.renderToolSystemPrefix(tools)
        assertTrue(prefix.contains("get_weather"))
        assertTrue(prefix.contains("```tool_call"))
        assertTrue(prefix.contains("\"arguments\""))
    }

    @Test
    fun `tools prefix is merged into existing system message`() {
        val items = ToolsPromptBuilder.toPromptItems(
            messages = listOf(
                ChatMessage(role = "system", content = "You are terse."),
                ChatMessage(role = "user", content = "hi"),
            ),
            tools = tools,
        )
        assertEquals(2, items.size)
        assertEquals("system", items[0].first)
        assertTrue(items[0].second.contains("# Tools"))
        assertTrue(items[0].second.contains("You are terse."))
        assertEquals("user" to "hi", items[1])
    }

    @Test
    fun `tools prefix injected when no system message exists`() {
        val items = ToolsPromptBuilder.toPromptItems(
            messages = listOf(ChatMessage(role = "user", content = "weather?")),
            tools = tools,
        )
        assertEquals(2, items.size)
        assertEquals("system", items[0].first)
        assertTrue(items[0].second.contains("# Tools"))
    }

    @Test
    fun `role tool becomes readable user text`() {
        val rendered = ToolsPromptBuilder.renderToolResult(
            ChatMessage(role = "tool", content = "sunny 25C", name = "get_weather", toolCallId = "call_1")
        )
        assertTrue(rendered.contains("[Tool result of get_weather (call id: call_1)]"))
        assertTrue(rendered.contains("sunny 25C"))
    }

    @Test
    fun `assistant tool calls are rendered back to marker format`() {
        val text = ToolsPromptBuilder.renderAssistantMessage(
            ChatMessage(
                role = "assistant",
                content = null,
                toolCalls = listOf(ToolCallRef(id = "call_1", name = "get_weather", argumentsJson = """{"city":"Paris"}""")),
            )
        )
        assertTrue(text.contains("```tool_call"))
        assertTrue(text.contains("\"name\":\"get_weather\""))
        assertTrue(text.contains("\"city\":\"Paris\""))
        // Round trip: rendered history must be parseable by the stream parser fallback.
        val inner = text.substringAfter("```tool_call\n").substringBefore("\n```")
        val calls = ToolCallStreamParser.tryParseCalls(inner)
        assertEquals(1, calls.size)
        assertEquals("get_weather", calls[0].name)
        assertEquals("call_1", calls[0].id)
    }

    @Test
    fun `messages without tools pass through unchanged`() {
        val items = ToolsPromptBuilder.toPromptItems(
            messages = listOf(
                ChatMessage(role = "system", content = "sys"),
                ChatMessage(role = "user", content = "hello"),
                ChatMessage(role = "assistant", content = "hi"),
            ),
            tools = null,
        )
        assertEquals(listOf("system" to "sys", "user" to "hello", "assistant" to "hi"), items)
    }
}
