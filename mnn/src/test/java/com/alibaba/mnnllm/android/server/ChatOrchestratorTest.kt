package com.alibaba.mnnllm.android.server

import com.alibaba.mnnllm.android.server.tools.ToolStreamEvent
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scripted engine for pipeline tests. */
private class FakeEngine(
    var model: String? = "test-model",
    val output: List<String>,
) : MnnEngine {
    override val loadedModel: String? get() = model
    var sampling: Triple<Float?, Float?, Int?>? = null
    var receivedMessages: List<Pair<String, String>>? = null

    override fun applySampling(temperature: Float?, topP: Float?, maxTokens: Int?) {
        sampling = Triple(temperature, topP, maxTokens)
    }

    override fun generate(messages: List<Pair<String, String>>, onToken: (String) -> Boolean): GenerationStats {
        receivedMessages = messages
        for (token in output) {
            if (onToken(token)) break
        }
        return GenerationStats(promptTokens = 10, completionTokens = output.size.toLong())
    }
}

private fun simpleRequest(stream: Boolean = false) = RequestTranslator.parse(
    JsonParser.parseString(
        """{"model":"mnn-local","stream":$stream,"temperature":0.1,"messages":[{"role":"user","content":"hi"}]}"""
    ).asJsonObject
)

class ChatOrchestratorTest {

    @Test
    fun `plain text completion`() {
        val engine = FakeEngine(output = listOf("Hello ", "there!"))
        val result = ChatOrchestrator(engine).complete(simpleRequest())
        assertEquals("Hello there!", result.content)
        assertEquals("stop", result.finishReason)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(10, result.stats.promptTokens)
        assertEquals(2, result.stats.completionTokens)
        assertEquals(0.1f, engine.sampling!!.first!!, 0.001f)
    }

    @Test
    fun `tool call output becomes structured result`() {
        val fence = "``" + "`"
        val block = fence + "tool" + "_call\n{\"name\":\"get_weather\",\"arguments\":{\"city\":\"Rome\"}}\n" + fence
        val engine = FakeEngine(output = listOf("Let me check. ", block))
        val result = ChatOrchestrator(engine).complete(simpleRequest())
        assertEquals("tool_calls", result.finishReason)
        assertEquals(1, result.toolCalls.size)
        assertEquals("get_weather", result.toolCalls[0].name)
        assertEquals("""{"city":"Rome"}""", result.toolCalls[0].argumentsJson)
        // marker text must not leak into content
        assertTrue(result.content == null || !result.content!!.contains("tool"))
        // prompt must not contain raw assistant markers from nothing
        assertTrue(engine.receivedMessages!!.any { it.first == "user" && it.second == "hi" })
    }

    @Test
    fun `stream events carry text then call then finish`() {
        val fence = "``" + "`"
        val block = fence + "tool" + "_call\n{\"name\":\"f\",\"arguments\":{}}\n" + fence
        val engine = FakeEngine(output = listOf("ok ", block, " done"))
        val events = mutableListOf<ToolStreamEvent>()
        ChatOrchestrator(engine).stream(simpleRequest(stream = true), { events.add(it) })
        assertTrue(events.first() is ToolStreamEvent.Text)
        assertTrue(events.any { it is ToolStreamEvent.ToolCall })
        assertTrue(events.last() is ToolStreamEvent.Finish.ToolCalls)
    }

    @Test(expected = ModelNotLoadedException::class)
    fun `no model loaded throws`() {
        val engine = FakeEngine(model = null, output = listOf("x"))
        ChatOrchestrator(engine).complete(simpleRequest())
    }

    @Test
    fun `cancellation aborts generation`() {
        val engine = FakeEngine(output = listOf("a", "b", "c"))
        var stopAfterFirst = false
        val events = mutableListOf<ToolStreamEvent>()
        ChatOrchestrator(engine).stream(simpleRequest(), { event ->
            events.add(event)
            if (event is ToolStreamEvent.Text) stopAfterFirst = true
        }, { stopAfterFirst })
        val text = events.filterIsInstance<ToolStreamEvent.Text>().joinToString("") { it.text }
        assertEquals("a", text)
    }
}
