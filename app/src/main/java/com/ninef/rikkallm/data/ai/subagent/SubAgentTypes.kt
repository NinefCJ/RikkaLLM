package com.ninef.rikkallm.data.ai.subagent

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting

/**
 * 子智能体工具档案。
 *
 * 用于在子智能体任务边界内限定其可接触的外部能力。
 * 当前阶段作为任务边界校验与系统提示约束（子智能体工具收窄预留扩展点）。
 */
enum class ToolProfile(val wireName: String, val description: String) {
    NONE("none", "纯文本推理，不访问任何外部数据"),
    READ_ONLY("read_only", "只读访问（文件 + 网络）"),
    WORKSPACE_READ("workspace_read", "仅只读工作区文件"),
    WEB_READ("web_read", "仅只读网络 / 搜索"),
    HISTORY_READ("history_read", "仅只读对话历史"),
    ;

    companion object {
        fun fromWireName(raw: String?): ToolProfile? = when (raw?.trim()?.lowercase()) {
            null, "" -> NONE
            else -> entries.firstOrNull { it.wireName == raw.trim().lowercase() }
        }
    }
}

/** 子智能体运行状态 */
enum class SubAgentRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    ;
}

/** 子智能体执行配置（由 subagent_start 工具参数构建，经校验后执行） */
@Serializable
data class SubAgentConfig(
    val role: String,
    val task: String,
    val systemPrompt: String? = null,
    val toolProfile: ToolProfile = ToolProfile.NONE,
    val toolAllowlist: List<String> = emptyList(),
    val allowDynamicRoles: Boolean = false,
    val maxOutputChars: Int = DEFAULT_SUBAGENT_OUTPUT_BUDGET_CHARS,
    val maxTokens: Int? = null,
    val model: Model,
    val provider: ProviderSetting,
)

/** 一次子智能体运行（后台异步执行，内存态保存） */
data class SubAgentRun(
    val runId: String,
    val config: SubAgentConfig,
    val status: SubAgentRunStatus,
    val output: String? = null,
    val error: String? = null,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
) {
    val durationMs: Long?
        get() = completedAtEpochMs?.minus(createdAtEpochMs)
}

/** 子智能体执行器抽象，便于测试注入与后续替换执行策略 */
fun interface SubAgentExecutor {
    suspend fun execute(config: SubAgentConfig): String
}

/** 默认子智能体输出预算（字符） */
const val DEFAULT_SUBAGENT_OUTPUT_BUDGET_CHARS = 20_000

/** 默认子智能体超时（毫秒） */
const val DEFAULT_SUBAGENT_TIMEOUT_MS = 120_000L

/** 子智能体并发上限 */
const val DEFAULT_SUBAGENT_MAX_CONCURRENT_RUNS = 2

/** 内存态最多保留的运行条数 */
const val MAX_SUBAGENT_RUNS = 50
