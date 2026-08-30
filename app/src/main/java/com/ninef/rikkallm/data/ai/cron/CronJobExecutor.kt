package com.ninef.rikkallm.data.ai.cron

import com.ninef.rikkallm.data.ai.subagent.SubAgentConfig
import com.ninef.rikkallm.data.ai.subagent.SubAgentExecutor
import com.ninef.rikkallm.data.ai.subagent.ToolProfile
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.datastore.findModelById
import com.ninef.rikkallm.data.datastore.findProvider
import com.ninef.rikkallm.data.datastore.getAssistantById
import com.ninef.rikkallm.data.datastore.getCurrentAssistant
import com.ninef.rikkallm.data.db.entity.CronJobEntity
import kotlin.uuid.Uuid

/**
 * Cron 无人值守执行器。
 *
 * 复用子智能体执行链路（[SubAgentExecutor]）完成一次受限推理补全：
 * - 无工具循环，纯文本输出，带超时与输出预算
 * - 模型与 Provider 从任务关联的 assistant 解析（fallback 到当前 assistant）
 */
class CronJobExecutor(
    private val settingsStore: SettingsStore,
    private val subAgentExecutor: SubAgentExecutor,
) {
    /** 执行单个任务，返回输出文本；异常向上抛，由调度方标记失败 */
    suspend fun execute(job: CronJobEntity): String {
        val settings = settingsStore.settingsFlow.value
        val assistantId = runCatching { Uuid.parse(job.assistantId) }.getOrNull()
        val assistant = assistantId?.let { settings.getAssistantById(it) }
            ?: settings.getCurrentAssistant()

        val model = settings.findModelById(assistant.chatModelId, fallback = settings.chatModelId)
            ?: error("未找到可用的聊天模型，请在设置中配置")
        val provider = model.findProvider(settings.providers)
            ?: error("未找到模型对应的 Provider 配置")

        val config = SubAgentConfig(
            role = if (job.type == CronJobType.BOARD.name) "dashboard" else "cron-task",
            task = job.prompt,
            systemPrompt = assistant.systemPrompt.takeIf { it.isNotBlank() },
            toolProfile = ToolProfile.NONE,
            maxOutputChars = DEFAULT_CRON_OUTPUT_BUDGET_CHARS,
            model = model,
            provider = provider,
        )
        return subAgentExecutor.execute(config)
    }

    private companion object {
        const val DEFAULT_CRON_TIMEOUT_MS: Long = 120_000L
        const val DEFAULT_CRON_OUTPUT_BUDGET_CHARS: Int = 8_000
    }
}
