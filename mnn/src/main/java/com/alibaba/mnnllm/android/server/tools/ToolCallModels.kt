// RikkaLLM Phase 2: OpenAI-compatible tools adaptation layer for the local MNN engine.
// The MNN engine (LlmSession) is a pure text stream with no function-calling concept,
// so tool calling is implemented here entirely at the protocol adaptation layer.
//
// All classes in this package are pure Kotlin/JVM (no android.* references) so they can
// be unit tested on the JVM.

package com.alibaba.mnnllm.android.server.tools

/**
 * A normalized chat message used by the adaptation layer. Mirrors the relevant subset
 * of the OpenAI chat completions message schema.
 */
data class ChatMessage(
    val role: String,
    val content: String?,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCallRef>? = null,
)

/** A tool call referenced by an assistant message (from request history). */
data class ToolCallRef(
    val id: String?,
    val name: String,
    val argumentsJson: String,
)

/** A tool invocation parsed out of the model's raw text output. */
data class ParsedToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * Events emitted while translating the raw model output stream into the OpenAI
 * streaming delta semantics.
 */
sealed class ToolStreamEvent {
    /** Plain text that should be surfaced as delta.content. */
    data class Text(val text: String) : ToolStreamEvent()

    /**
     * A (possibly incremental) tool call delta. Because the parser only reports a call
     * once its marker block is complete, each parsed call is emitted as a single event
     * carrying id + name + full arguments.
     */
    data class ToolCall(
        val index: Int,
        val id: String,
        val name: String,
        val arguments: String,
    ) : ToolStreamEvent()

    sealed class Finish : ToolStreamEvent() {
        data object Stop : Finish()
        data object ToolCalls : Finish()

        /** The engine stopped because the requested max_tokens budget was reached. */
        data object Length : Finish()
    }
}
