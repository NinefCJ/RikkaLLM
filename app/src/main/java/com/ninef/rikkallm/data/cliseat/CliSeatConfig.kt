package com.ninef.rikkallm.data.cliseat

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 命令行席位的输入传递方式。
 * - ARG：把 `{prompt}` 占位符替换成单引号转义后的 prompt，作为命令参数传入（适合 `claude -p "..."`、`gemini -p "..."` 这类直接吃参数的 CLI）。
 * - STDIN：命令原样执行，prompt 通过标准输入传给进程（适合从 stdin 读取任务的 CLI）。
 */
@Serializable
enum class CliSeatInputMode {
    ARG,
    STDIN,
}

/**
 * 单个外部 CLI 席位配置。启用后，该命令行工具会作为"席位"参与模型议会，
 * 与其他聊天模型并列，由 [com.ninef.rikkallm.data.ai.tools.createModelCouncilTool] 并发征集其回答。
 *
 * CLI 工具（如 Claude Code、Gemini CLI、Aider 等）需要预先安装在 proot rootfs 内，
 * 席位实际通过 [me.rerere.workspace.WorkspaceManager.executeCommand] 在 rootfs 中执行。
 *
 * @param id 唯一 id
 * @param name 展示名（议会中作为席位标签）
 * @param command 命令模板；ARG 模式下可使用 `{prompt}` 占位符
 * @param inputMode 输入传递方式，见 [CliSeatInputMode]
 * @param enabled 是否启用（启用后才作为席位参与议会）
 */
@Serializable
data class CliSeatConfig(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val command: String = "",
    val inputMode: CliSeatInputMode = CliSeatInputMode.ARG,
    val enabled: Boolean = true,
)

@Serializable
data class CliSeatState(
    val seats: List<CliSeatConfig> = emptyList(),
)
