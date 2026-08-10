// Response-side of the tools adaptation layer: a streaming parser that watches the raw
// token stream of the local model for tool-call markers and converts them into
// structured ToolStreamEvents.
//
// Supported marker formats (models differ, we accept several):
//   1. fenced "tool_call" JSON block (the format we instruct models to use)
//   2. fenced "json" / language-less block whose content parses as
//      {"name": .., "arguments": ..}
//   3. Qwen2.5-style think-tag markers and legacy tags around a JSON payload
//   4. (finish fallback) a bare JSON object/array carrying name + arguments
//
// When no marker is ever recognized the text passes through unchanged: tool support
// degrades safely into plain text completion.

package com.alibaba.mnnllm.android.server.tools

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/**
 * Feed raw model output chunks into [feed] and consume the translated events. Call
 * [finish] when generation ends. The parser buffers partial markers so markers split
 * across chunk boundaries are still detected.
 */
class ToolCallStreamParser(
    private val generateCallId: () -> String = { defaultCallId() },
) {
    private val buffer = StringBuilder()

    // Full raw output, kept for the end-of-stream bare-JSON fallback.
    private val raw = StringBuilder()

    private var nextIndex = 0
    private var emittedText = false

    // While re-parsing the inside of a think tag: reasoning prose is stripped, only
    // nested tool-call markers are extracted.
    private var suppressText = false

    var sawToolCall: Boolean = false
        private set

    private val detected = mutableListOf<ParsedToolCall>()
    val toolCalls: List<ParsedToolCall> get() = detected.toList()

    fun feed(chunk: String): List<ToolStreamEvent> {
        if (chunk.isEmpty()) return emptyList()
        raw.append(chunk)
        buffer.append(chunk)
        return drain()
    }

    fun finish(): List<ToolStreamEvent> {
        val events = mutableListOf<ToolStreamEvent>()

        // Fallback: no marker was ever seen and the whole output is a bare JSON call.
        if (!sawToolCall && !emittedText) {
            val calls = tryParseCalls(raw.toString().trim())
            if (calls.isNotEmpty()) {
                buffer.setLength(0)
                calls.forEach { events.add(emitCall(it)) }
            }
        }

        if (buffer.isNotEmpty()) {
            events.add(ToolStreamEvent.Text(buffer.toString()))
            buffer.setLength(0)
        }
        events.add(if (sawToolCall) ToolStreamEvent.Finish.ToolCalls else ToolStreamEvent.Finish.Stop)
        return events
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun drain(): List<ToolStreamEvent> {
        val events = mutableListOf<ToolStreamEvent>()
        while (true) {
            if (buffer.isEmpty()) break

            // Holding strategy: nothing emitted yet and the whole output still looks
            // like a bare JSON payload -> keep holding for the finish() fallback.
            if (!emittedText && !sawToolCall) {
                val t = raw.toString().trim()
                if (t.startsWith("{") || t.startsWith("[")) {
                    if (buffer.length <= MAX_JSON_HOLD) break
                    emittedText = true // too large to be a call payload, give up holding
                }
            }

            val candidate = findMarkerStart()
            if (candidate < 0) {
                flushAll(events)
                break
            }
            if (candidate > 0) {
                flushText(buffer.substring(0, candidate), events)
                buffer.delete(0, candidate)
            }
            if (!tryConsumeMarker(events)) {
                // Marker may be forming but is incomplete: wait for more chunks,
                // unless the pending tail grew absurdly large (false alarm).
                if (buffer.length > MAX_PENDING) {
                    flushAll(events)
                }
                break
            }
        }
        return events
    }

    /** Earliest index of a possible marker start character ('<' or backtick), or -1. */
    private fun findMarkerStart(): Int {
        val lt = buffer.indexOf("<")
        val bt = buffer.indexOf("`")
        return when {
            lt < 0 -> bt
            bt < 0 -> lt
            else -> minOf(lt, bt)
        }
    }

    /**
     * Attempts to consume a marker at buffer[0]. Returns true when progress was made
     * (a marker was handled or a false-alarm character was flushed), false when more
     * input is required.
     */
    private fun tryConsumeMarker(events: MutableList<ToolStreamEvent>): Boolean {
        val c = buffer[0]

        if (c == '<') {
            // Qwen2.5-style think tag: strip it, its content is handled recursively.
            if (buffer.startsWith(THINK_OPEN)) {
                val end = buffer.indexOf(THINK_CLOSE)
                if (end < 0) return false
                val inner = buffer.substring(THINK_OPEN.length, end)
                buffer.delete(0, end + THINK_CLOSE.length)
                reinsertForParsing(inner, events)
                return true
            }
            if (buffer.startsWith(TAG_OPEN)) {
                val end = buffer.indexOf(TAG_CLOSE)
                if (end < 0) return false
                val body = buffer.substring(TAG_OPEN.length, end)
                buffer.delete(0, end + TAG_CLOSE.length)
                parseMarkerBody(body, events)
                return true
            }
            if (couldStillBeTag()) return false
            // Ordinary '<' in prose: flush it.
            flushText("<", events)
            buffer.deleteCharAt(0)
            return true
        }

        // Backtick handling.
        if (!buffer.startsWith("```")) {
            // Ambiguous: 1-2 backticks may still grow into a fence, wait for more.
            if (buffer.length < 3) return false
            // 3+ chars and not a fence: the first backtick is inline code, flush it.
            flushText("`", events)
            buffer.deleteCharAt(0)
            return true
        }
        return tryConsumeFence(events)
    }

    private fun couldStillBeTag(): Boolean {
        // buffer starts with '<'; hold only while it is still a true prefix of a known
        // tag, so ordinary prose like "a < b" is flushed immediately.
        val head = buffer.toString()
        return TAG_OPEN.startsWith(head) || THINK_OPEN.startsWith(head)
    }

    /** buffer starts with triple backticks. */
    private fun tryConsumeFence(events: MutableList<ToolStreamEvent>): Boolean {
        val newline = buffer.indexOf('\n')
        if (newline < 0) return false // info string incomplete
        val lang = buffer.substring(3, newline).trim().lowercase()
        val contentStart = newline + 1
        val closing = buffer.indexOf(FENCE, contentStart)
        if (closing < 0) return false // block not finished yet

        val content = buffer.substring(contentStart, closing)
        val fullLen = closing + FENCE.length
        when {
            lang == FENCE_LANG_TOOL -> {
                buffer.delete(0, fullLen)
                parseMarkerBody(content, events)
            }

            else -> {
                // json / language-less fences are tool calls only if they parse as
                // such; anything else is passed through verbatim (including fences).
                val calls = tryParseCalls(content.trim())
                buffer.delete(0, fullLen)
                if (calls.isNotEmpty()) {
                    calls.forEach { events.add(emitCall(it)) }
                } else {
                    flushText(FENCE + (if (lang.isEmpty()) "" else lang) + "\n" + content + FENCE, events)
                }
            }
        }
        return true
    }

    /**
     * Parsed content inside a marker. May be a single call object, an array of calls,
     * or an envelope like {"tool_calls": [...]}. Unparseable content degrades to text.
     */
    private fun parseMarkerBody(body: String, events: MutableList<ToolStreamEvent>) {
        val calls = tryParseCalls(body.trim())
        if (calls.isEmpty()) {
            flushText(body, events)
        } else {
            calls.forEach { events.add(emitCall(it)) }
        }
    }

    /**
     * Content inside a think tag is internal reasoning: strip the prose, but extract
     * any nested tool-call markers so models that emit calls inside think still work.
     */
    private fun reinsertForParsing(inner: String, events: MutableList<ToolStreamEvent>) {
        if (inner.isBlank()) return
        val calls = tryParseCalls(inner.trim())
        if (calls.isNotEmpty()) {
            calls.forEach { events.add(emitCall(it)) }
            return
        }
        if (inner.contains(FENCE) || inner.contains("<")) {
            suppressText = true
            buffer.insert(0, inner)
            events.addAll(drain())
            suppressText = false
            // Drop any incomplete marker residue left inside the think content.
            if (buffer.isNotEmpty()) buffer.setLength(0)
        }
    }

    private fun emitCall(call: ParsedToolCall): ToolStreamEvent {
        sawToolCall = true
        val id = call.id.ifEmpty { generateCallId() }
        detected.add(call.copy(id = id))
        return ToolStreamEvent.ToolCall(
            index = nextIndex++,
            id = id,
            name = call.name,
            arguments = call.argumentsJson,
        )
    }

    private fun flushText(text: String, events: MutableList<ToolStreamEvent>) {
        if (text.isEmpty()) return
        emittedText = true
        if (suppressText) return
        events.add(ToolStreamEvent.Text(text))
    }

    private fun flushAll(events: MutableList<ToolStreamEvent>) {
        flushText(buffer.toString(), events)
        buffer.setLength(0)
    }

    companion object {
        private const val FENCE = "``" + "`"
        private const val FENCE_LANG_TOOL = "tool" + "_call"
        private const val TAG_OPEN = "<tool" + "_call>"
        private const val TAG_CLOSE = "</tool" + "_call>"
        private const val THINK_OPEN = "<think" + ">"
        private const val THINK_CLOSE = "</think" + ">"
        private const val MAX_PENDING = 4096
        private const val MAX_JSON_HOLD = 16384

        fun defaultCallId(): String =
            "call_" + UUID.randomUUID().toString().replace("-", "").take(24)

        private val gson = Gson()

        /**
         * Attempts to interpret [text] as one or more tool calls. Returns an empty list
         * when it is not recognizable as tool-call JSON.
         */
        fun tryParseCalls(text: String): List<ParsedToolCall> {
            if (text.isEmpty()) return emptyList()
            val first = text.first()
            if (first != '{' && first != '[') return emptyList()
            val element = try {
                JsonParser.parseString(text)
            } catch (e: Exception) {
                return emptyList()
            }
            return when {
                element.isJsonArray -> element.asJsonArray.mapNotNull { callFromElement(it) }
                element.isJsonObject -> callsFromObject(element.asJsonObject)
                else -> emptyList()
            }
        }

        private fun callsFromObject(obj: JsonObject): List<ParsedToolCall> {
            // Envelope: {"tool_calls": [ ... ]}
            if (obj.has("tool_calls") && obj["tool_calls"].isJsonArray) {
                return obj.getAsJsonArray("tool_calls").mapNotNull { callFromElement(it) }
            }
            val call = callFromElement(obj) ?: return emptyList()
            return listOf(call)
        }

        private fun callFromElement(element: JsonElement): ParsedToolCall? {
            if (!element.isJsonObject) return null
            val obj = element.asJsonObject
            val nameElement = obj["name"] ?: obj.get("function")?.let { fn ->
                if (fn.isJsonObject) fn.asJsonObject["name"] else null
            } ?: return null
            val name = nameElement.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
            if (name.isEmpty()) return null

            val argsElement = obj["arguments"]
                ?: obj["parameters"]
                ?: obj.get("function")?.let { fn ->
                    if (fn.isJsonObject) fn.asJsonObject["arguments"] else null
                }
            val argsJson = when {
                argsElement == null || argsElement.isJsonNull -> "{}"
                argsElement.isJsonPrimitive && argsElement.asJsonPrimitive.isString -> {
                    // Arguments may be a JSON-encoded string.
                    argsElement.asString
                }

                else -> gson.toJson(argsElement)
            }

            val id = obj["id"]?.takeIf { it.isJsonPrimitive }?.asString
            return ParsedToolCall(id = id.orEmpty(), name = name, argumentsJson = argsJson)
        }
    }
}
