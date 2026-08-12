package com.alibaba.mnnllm.android.server

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponsesTest {

    private val gson = Gson()

    private fun contentOf(json: String): String {
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("chat.completion.chunk", root["object"].asString)
        val choice = root["choices"].asJsonArray[0].asJsonObject
        return choice["delta"].asJsonObject["content"].asString
    }

    @Test
    fun `contentChunk is valid json and round-trips content`() {
        val cases = listOf(
            "hello world",
            "",
            "line1\nline2",
            "quote \" inside",
            "backslash \\ here",
            "tab\there",
            "carriage\rreturn",
            "unicode ☃ and 中文",
            "{\"json\": true}",
        )
        for (text in cases) {
            val json = OpenAiResponses.contentChunk("id1", "model-x", 123L, text)
            assertEquals(text, contentOf(json))
        }
    }

    @Test
    fun `contentChunk matches gson-based structure for control chars`() {
        val text = "a\tb\nc\re\"f\\g"
        val json = OpenAiResponses.contentChunk("id", "m", 1L, text)
        val root = JsonParser.parseString(json).asJsonObject
        assertTrue(root.has("id"))
        assertTrue(root.has("created"))
        assertTrue(root.has("model"))
        assertEquals(text, contentOf(json))
    }

    @Test
    fun `usageChunk surfaces prefill and decode timing extension`() {
        val stats = GenerationStats(
            promptTokens = 43,
            completionTokens = 479,
            prefillMs = 1330L,
            decodeMs = 43460L,
            memoryKb = 2355200L,
        )
        val json = OpenAiResponses.usageChunk("cmpl-1", "mnn-x", 1L, stats)
        val root = JsonParser.parseString(json).asJsonObject
        val usage = root["usage"].asJsonObject
        assertEquals(43, usage["prompt_tokens"].asLong)
        assertEquals(479, usage["completion_tokens"].asLong)
        assertEquals(522, usage["total_tokens"].asLong)
        // Local-engine extension fields: prefill/decode ms, tokens/s and peak memory.
        assertEquals(1330L, usage["prefill_ms"].asLong)
        assertEquals(43460L, usage["decode_ms"].asLong)
        assertEquals(43 * 1000.0 / 1330.0, usage["prefill_tokens_per_second"].asDouble, 1e-3)
        assertEquals(479 * 1000.0 / 43460.0, usage["decode_tokens_per_second"].asDouble, 1e-3)
        assertEquals(2355200L, usage["peak_memory_kb"].asLong)
        // Stream convention: this chunk carries only usage, no choices.
        assertEquals(0, root["choices"].asJsonArray.size())
    }

    @Test
    fun `usageChunk omits tps when timing is zero`() {
        val json = OpenAiResponses.usageChunk("id", "m", 1L, GenerationStats(promptTokens = 0, completionTokens = 0))
        val usage = JsonParser.parseString(json).asJsonObject["usage"].asJsonObject
        // No division-by-zero: tps keys must be absent when there is no timing.
        assertTrue(!usage.has("prefill_tokens_per_second"))
        assertTrue(!usage.has("decode_tokens_per_second"))
    }

    @Test
    fun `fullCompletion includes timing and memory extension in usage`() {
        val stats = GenerationStats(
            promptTokens = 43,
            completionTokens = 479,
            prefillMs = 1330L,
            decodeMs = 43460L,
            memoryKb = 2355200L,
        )
        val json = OpenAiResponses.fullCompletion(
            id = "cmpl-1",
            model = "mnn-x",
            created = 1L,
            content = "hi",
            toolCalls = emptyList(),
            finishReason = "stop",
            stats = stats,
        )
        val usage = JsonParser.parseString(json).asJsonObject["usage"].asJsonObject
        assertEquals(1330L, usage["prefill_ms"].asLong)
        assertEquals(43460L, usage["decode_ms"].asLong)
        assertEquals(2355200L, usage["peak_memory_kb"].asLong)
    }
}
