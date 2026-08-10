package com.alibaba.mnnllm.android.server

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTranslatorTest {

    @Test
    fun `parses basic fields`() {
        val body = JsonParser.parseString(
            """
            {
              "model": "mnn-local",
              "stream": true,
              "temperature": 0.7,
              "top_p": 0.9,
              "max_tokens": 512,
              "messages": [
                {"role": "system", "content": "sys"},
                {"role": "user", "content": "hello"}
              ]
            }
            """.trimIndent()
        ).asJsonObject
        val req = RequestTranslator.parse(body)
        assertEquals("mnn-local", req.model)
        assertTrue(req.stream)
        assertEquals(0.7f, req.temperature!!, 0.001f)
        assertEquals(0.9f, req.topP!!, 0.001f)
        assertEquals(512, req.maxTokens)
        assertEquals(2, req.messages.size)
        assertNull(req.tools)
    }

    @Test
    fun `parses content parts arrays`() {
        val body = JsonParser.parseString(
            """{"messages":[{"role":"user","content":[{"type":"text","text":"a"},{"type":"image_url","image_url":{"url":"x"}},{"type":"text","text":"b"}]}]}"""
        ).asJsonObject
        val req = RequestTranslator.parse(body)
        assertEquals("a\nb", req.messages[0].content)
    }

    @Test
    fun `parses tool calls and tool results`() {
        val body = JsonParser.parseString(
            """
            {
              "messages": [
                {"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"f","arguments":{"k":1}}}]},
                {"role":"tool","tool_call_id":"c1","name":"f","content":"done"}
              ]
            }
            """.trimIndent()
        ).asJsonObject
        val req = RequestTranslator.parse(body)
        val assistant = req.messages[0]
        assertEquals(1, assistant.toolCalls!!.size)
        assertEquals("f", assistant.toolCalls!![0].name)
        assertEquals("""{"k":1}""", assistant.toolCalls!![0].argumentsJson)
        assertEquals("c1", req.messages[1].toolCallId)
    }

    @Test
    fun `tool_choice none disables tools`() {
        val body = JsonParser.parseString(
            """{"tool_choice":"none","tools":[{"type":"function","function":{"name":"f"}}],"messages":[{"role":"user","content":"hi"}]}"""
        ).asJsonObject
        assertNull(RequestTranslator.parse(body).tools)

        val auto = JsonParser.parseString(
            """{"tool_choice":"auto","tools":[{"type":"function","function":{"name":"f"}}],"messages":[{"role":"user","content":"hi"}]}"""
        ).asJsonObject
        assertEquals(1, RequestTranslator.parse(auto).tools!!.size())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing messages rejected`() {
        RequestTranslator.parse(JsonParser.parseString("""{"model":"x"}""").asJsonObject)
    }

    @Test
    fun `max_completion_tokens accepted`() {
        val body = JsonParser.parseString(
            """{"max_completion_tokens": 128, "messages":[{"role":"user","content":"hi"}]}"""
        ).asJsonObject
        assertEquals(128, RequestTranslator.parse(body).maxTokens)
        assertFalse(RequestTranslator.parse(body).stream)
    }
}
