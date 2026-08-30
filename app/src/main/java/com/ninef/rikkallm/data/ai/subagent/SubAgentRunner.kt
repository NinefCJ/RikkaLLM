package com.ninef.rikkallm.data.ai.subagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 子智能体执行器。
 *
 * 每次运行执行一次独立的模型补全（无工具循环），并施加：
 * - 超时保护 [DEFAULT_SUBAGENT_TIMEOUT_MS]
 * - 输出预算截断（max_output_chars）
 *
 * 工具调用循环（ReAct）为后续扩展点，当前阶段子智能体仅做受限推理。
 */
class SubAgentRunner(
    private val providerManager: ProviderManager,
    private val timeoutMs: Long = DEFAULT_SUBAGENT_TIMEOUT_MS,
) : SubAgentExecutor {
    override suspend fun execute(config: SubAgentConfig): String = withContext(Dispatchers.IO) {
        val output = withTimeout(timeoutMs) {
            val providerImpl = providerManager.getProviderByType(config.provider)
            val params = TextGenerationParams(
                model = config.model,
                temperature = 0.3f,
                maxTokens = config.maxTokens,
                tools = emptyList(),
            )
            val messages = listOf(
                UIMessage.system(buildSystemPrompt(config)),
                UIMessage.user(config.task),
            )
            val chunk = providerImpl.generateText(
                providerSetting = config.provider,
                messages = messages,
                params = params,
            )
            extractText(chunk)
        }
        applyOutputBudget(output, config.maxOutputChars)
    }

    private fun buildSystemPrompt(config: SubAgentConfig): String = buildString {
        appendLine("You are a sub-agent with role '${config.role}'. You are executing one delegated subtask.")
        appendLine(profileConstraint(config.toolProfile))
        config.systemPrompt?.let {
            appendLine()
            appendLine(it)
        }
        appendLine()
        appendLine("Instructions:")
        appendLine("- Work autonomously; do not ask clarifying questions. Make reasonable assumptions.")
        appendLine("- Respond with only the final result (no preamble).")
        appendLine("- Output is capped at ${config.maxOutputChars} characters; keep the answer concise.")
    }

    private fun profileConstraint(profile: ToolProfile): String = when (profile) {
        ToolProfile.NONE ->
            "Constraint: you may ONLY reason over the provided task text. You cannot access files, network, or any external data."
        ToolProfile.READ_ONLY ->
            "Constraint: read-only access (files and web). You may inspect data but must not modify anything."
        ToolProfile.WORKSPACE_READ ->
            "Constraint: read-only access to workspace files only. No writes, no network."
        ToolProfile.WEB_READ ->
            "Constraint: read-only web/search access only. No local file access, no writes."
        ToolProfile.HISTORY_READ ->
            "Constraint: read-only access to conversation history only. No other external data."
    }

    private fun extractText(chunk: MessageChunk): String =
        chunk.choices.firstOrNull()?.message?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Sub-agent returned an empty response")

    private fun applyOutputBudget(output: String, budget: Int): String =
        if (output.length <= budget) {
            output
        } else {
            output.take(budget) + "\n\n[... truncated at $budget chars by output budget ...]"
        }
}
