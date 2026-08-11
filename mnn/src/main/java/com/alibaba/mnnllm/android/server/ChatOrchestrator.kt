// Orchestration of one chat completion: applies sampling params, renders the request
// into engine prompt items, runs the blocking engine generation and pipes the raw
// output through the tool-call stream parser. Depends only on the MnnEngine
// interface, so the whole pipeline is JVM-testable with a fake engine.

package com.alibaba.mnnllm.android.server

import com.alibaba.mnnllm.android.server.tools.ParsedToolCall
import com.alibaba.mnnllm.android.server.tools.ToolCallStreamParser
import com.alibaba.mnnllm.android.server.tools.ToolStreamEvent
import com.alibaba.mnnllm.android.server.tools.ToolsPromptBuilder

data class CompletedResult(
    val content: String?,
    val toolCalls: List<ParsedToolCall>,
    val finishReason: String,
    val stats: GenerationStats,
)

class ChatOrchestrator(private val engine: MnnEngine) {

    /**
     * Runs a generation, emitting translated [ToolStreamEvent]s (Text / ToolCall and
     * the terminal Finish event) through [emit]. Returns engine usage stats.
     */
    fun stream(
        request: ChatCompletionRequest,
        emit: (ToolStreamEvent) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): GenerationStats {
        if (engine.loadedModel == null) throw ModelNotLoadedException()

        engine.applySampling(request.temperature, request.topP, request.maxTokens)
        val promptItems = ToolsPromptBuilder.toPromptItems(request.messages, request.tools)
        val parser = ToolCallStreamParser()

        val stats = engine.generate(promptItems) { token ->
            if (isCancelled()) {
                true
            } else {
                parser.feed(token).forEach(emit)
                false
            }
        }
        // The native engine does not report a truncation reason; approximate
        // finish_reason=length by comparing the produced token count against the
        // requested max_tokens budget (streaming and non-streaming stay consistent).
        val reachedTokenLimit = request.maxTokens != null && stats.completionTokens >= request.maxTokens
        parser.finish().forEach { event ->
            emit(if (reachedTokenLimit && event is ToolStreamEvent.Finish.Stop) ToolStreamEvent.Finish.Length else event)
        }
        return stats
    }

    /** Non-streaming variant: collects everything into a single result. */
    fun complete(
        request: ChatCompletionRequest,
        isCancelled: () -> Boolean = { false },
    ): CompletedResult {
        val content = StringBuilder()
        val calls = mutableListOf<ParsedToolCall>()
        var finishReason = "stop"
        val stats = stream(request, { event ->
            when (event) {
                is ToolStreamEvent.Text -> content.append(event.text)
                is ToolStreamEvent.ToolCall -> calls.add(
                    ParsedToolCall(id = event.id, name = event.name, argumentsJson = event.arguments)
                )

                is ToolStreamEvent.Finish.ToolCalls -> finishReason = "tool_calls"
                is ToolStreamEvent.Finish.Length -> if (finishReason != "tool_calls") finishReason = "length"
                is ToolStreamEvent.Finish.Stop -> Unit
            }
        }, isCancelled)
        return CompletedResult(
            // No trim(): leading/trailing whitespace of a completion is meaningful
            // output (e.g. when the caller concatenates chunks) and must survive.
            content = content.toString().ifEmpty { null },
            toolCalls = calls,
            finishReason = finishReason,
            stats = stats,
        )
    }
}
