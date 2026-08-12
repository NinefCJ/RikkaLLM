package com.alibaba.mnnllm.android.server.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val FENCE = "``" + "`"
private val TOOL_LANG = "tool" + "_call"
private val TAG_OPEN = "<tool" + "_call>"
private val TAG_CLOSE = "</tool" + "_call>"
private val THINK_OPEN = "<think" + ">"
private val THINK_CLOSE = "</think" + ">"

private fun fixedIds(): ToolCallStreamParser {
    var n = 0
    return ToolCallStreamParser(generateCallId = { "call_fixed_${n++}" })
}

private fun List<ToolStreamEvent>.texts(): String =
    filterIsInstance<ToolStreamEvent.Text>().joinToString("") { it.text }

private fun List<ToolStreamEvent>.calls(): List<ToolStreamEvent.ToolCall> =
    filterIsInstance<ToolStreamEvent.ToolCall>()

class ToolCallStreamParserTest {

    @Test
    fun `plain text passes through with stop finish`() {
        val parser = fixedIds()
        val events = parser.feed("Hello world. ") + parser.feed("How are you?") + parser.finish()
        assertEquals("Hello world. How are you?", events.texts())
        assertTrue(events.last() is ToolStreamEvent.Finish.Stop)
        assertFalse(parser.sawToolCall)
    }

    @Test
    fun `complete tool_call fence is parsed`() {
        val parser = fixedIds()
        val block = FENCE + TOOL_LANG + "\n" +
            """{"name":"get_weather","arguments":{"city":"Paris"}}""" + "\n" + FENCE
        val events = parser.feed(block) + parser.finish()
        val calls = events.calls()
        assertEquals(1, calls.size)
        assertEquals("get_weather", calls[0].name)
        assertEquals("""{"city":"Paris"}""", calls[0].arguments)
        assertEquals(0, calls[0].index)
        assertTrue(events.last() is ToolStreamEvent.Finish.ToolCalls)
        assertEquals("", events.texts().trim())
    }

    @Test
    fun `marker split across many chunks is still detected`() {
        val parser = fixedIds()
        val block = "Sure, let me check.\n" + FENCE + TOOL_LANG + "\n" +
            """{"name":"get_weather","arguments":{"city":"北京"}}""" + "\n" + FENCE
        var events = emptyList<ToolStreamEvent>()
        // feed one character at a time
        block.forEach { ch -> events = events + parser.feed(ch.toString()) }
        events = events + parser.finish()
        assertEquals("Sure, let me check.\n", events.texts())
        val calls = events.calls()
        assertEquals(1, calls.size)
        assertEquals("get_weather", calls[0].name)
        assertEquals("""{"city":"北京"}""", calls[0].arguments)
        assertTrue(events.last() is ToolStreamEvent.Finish.ToolCalls)
    }

    @Test
    fun `text after marker is emitted after call`() {
        val parser = fixedIds()
        val stream = "Prefix text " + FENCE + TOOL_LANG + "\n" +
            """{"name":"f","arguments":{}}""" + "\n" + FENCE + " trailing"
        val events = parser.feed(stream) + parser.finish()
        assertEquals("Prefix text  trailing", events.texts())
        assertEquals(1, events.calls().size)
    }

    @Test
    fun `json fence with tool call is detected`() {
        val parser = fixedIds()
        val block = FENCE + "json\n" + """{"name":"search","arguments":{"q":"kotlin"}}""" + "\n" + FENCE
        val events = parser.feed(block) + parser.finish()
        assertEquals(1, events.calls().size)
        assertEquals("search", events.calls()[0].name)
    }

    @Test
    fun `json fence without tool shape passes through`() {
        val parser = fixedIds()
        val block = FENCE + "json\n" + """{"foo": 1}""" + "\n" + FENCE
        val events = parser.feed(block) + parser.finish()
        assertTrue(events.calls().isEmpty())
        assertTrue(events.texts().contains("\"foo\""))
        assertTrue(events.last() is ToolStreamEvent.Finish.Stop)
    }

    @Test
    fun `legacy tags are parsed`() {
        val parser = fixedIds()
        val stream = TAG_OPEN + """{"name":"f","arguments":{"a":1}}""" + TAG_CLOSE
        val events = parser.feed(stream) + parser.finish()
        assertEquals(1, events.calls().size)
        assertEquals("f", events.calls()[0].name)
    }

    @Test
    fun `think tag content is stripped and inner marker parsed`() {
        val parser = fixedIds()
        val stream = THINK_OPEN + "I need weather info.\n" + FENCE + TOOL_LANG + "\n" +
            """{"name":"get_weather","arguments":{}}""" + "\n" + FENCE + THINK_CLOSE
        val events = parser.feed(stream) + parser.finish()
        assertEquals(1, events.calls().size)
        assertFalse(events.texts().contains("I need weather info"))
    }

    @Test
    fun `bare json output detected at finish`() {
        val parser = fixedIds()
        val events = parser.feed("""{"name":"calc",""") + parser.feed(""""arguments":{"expr":"1+1"}}""") + parser.finish()
        val calls = events.calls()
        assertEquals(1, calls.size)
        assertEquals("calc", calls[0].name)
        assertEquals("""{"expr":"1+1"}""", calls[0].arguments)
        assertTrue(events.last() is ToolStreamEvent.Finish.ToolCalls)
        // nothing should have leaked as text
        assertEquals("", events.texts())
    }

    @Test
    fun `multiple calls in one array`() {
        val parser = fixedIds()
        val block = FENCE + TOOL_LANG + "\n" +
            """[{"name":"a","arguments":{}},{"name":"b","arguments":{"x":2}}]""" + "\n" + FENCE
        val events = parser.feed(block) + parser.finish()
        val calls = events.calls()
        assertEquals(2, calls.size)
        assertEquals("a", calls[0].name)
        assertEquals(0, calls[0].index)
        assertEquals("b", calls[1].name)
        assertEquals(1, calls[1].index)
    }

    @Test
    fun `ordinary prose with angle brackets and backticks survives`() {
        val parser = fixedIds()
        val prose = "a < b and use `code` here, also 1 << 2"
        val events = parser.feed(prose) + parser.finish()
        assertEquals(prose, events.texts())
        assertFalse(parser.sawToolCall)
    }

    @Test
    fun `unparseable marker body degrades to text`() {
        val parser = fixedIds()
        val stream = FENCE + TOOL_LANG + "\nnot a json at all\n" + FENCE
        val events = parser.feed(stream) + parser.finish()
        assertTrue(events.calls().isEmpty())
        assertTrue(events.texts().contains("not a json at all"))
        assertTrue(events.last() is ToolStreamEvent.Finish.Stop)
    }

    @Test
    fun `tryParseCalls handles envelope and string arguments`() {
        val envelope = ToolCallStreamParser.tryParseCalls(
            """{"tool_calls":[{"id":"c1","name":"f","arguments":"{\"k\":1}"}]}"""
        )
        assertEquals(1, envelope.size)
        assertEquals("c1", envelope[0].id)
        assertEquals("""{"k":1}""", envelope[0].argumentsJson)

        val none = ToolCallStreamParser.tryParseCalls("hello world")
        assertTrue(none.isEmpty())
    }

    @Test
    fun `bare json without tool shape passes through as text when holding`() {
        // Starts with '{' so the parser holds during streaming, then the end-of-stream
        // bare-JSON fallback must reject it (no "name"/"tool_calls") and emit as text.
        val parser = fixedIds()
        val events = parser.feed("""{"foo": 1}""") + parser.finish()
        assertTrue(events.calls().isEmpty())
        assertEquals("""{"foo": 1}""", events.texts().trim())
    }

    @Test
    fun `long object-like stream gives up holding and passes through as text`() {
        // Starts with '{' but is prose longer than the hold budget, so the parser must
        // stop holding and emit it as text rather than keeping everything buffered.
        // Regression guard for the streaming hot path (no per-token raw copy).
        val parser = fixedIds()
        val big = "{" + "a".repeat(20000) + " not json"
        val events = parser.feed(big) + parser.finish()
        assertTrue(events.calls().isEmpty())
        assertTrue(events.texts().contains("not json"))
    }

    @Test
    fun `large plain stream is preserved exactly`() {
        // Smoke test: streaming many small chunks must not lose or reorder content.
        val parser = fixedIds()
        val big = "x".repeat(20000) + " final"
        var events: List<ToolStreamEvent> = emptyList()
        big.forEach { ch -> events = events + parser.feed(ch.toString()) }
        events = events + parser.finish()
        assertEquals(big, events.texts())
    }
}
