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
        parser.finish().forEach(emit)
        return stats
    }

    /** Non-streaming variant: collects everything into a single result. */
    fun complete(
        request: ChatCompletionRequest,
        isCancelled: () -> Boolean = { false },
    ): CompletedResult {
        val content = StringBuilder()
        val calls = mutableListOf<ParsedToolCall>()
        val stats = stream(request, { event ->
            when (event) {
                is ToolStreamEvent.Text -> content.append(event.text)
                is ToolStreamEvent.ToolCall -> calls.add(
                    ParsedToolCall(id = event.id, name = event.name, argumentsJson = event.arguments)
                )

                is ToolStreamEvent.Finish -> Unit
            }
        }, isCancelled)
        return CompletedResult(
            content = content.toString().trim().ifEmpty { null },
            toolCalls = calls,
            finishReason = if (calls.isNotEmpty()) "tool_calls" else "stop",
            stats = stats,
        )
    }
}
