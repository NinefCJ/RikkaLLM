package com.ninef.rikkallm.data.cliseat

import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceCommandResult

/**
 * 在 proot rootfs 内执行一个 CLI 席位，把 stdout 作为该席位的"回答"返回给模型议会。
 *
 * 复用 [WorkspaceManager.executeCommand]（底层为 [me.rerere.workspace.ProotShellRunner]），
 * 因此 CLI 工具必须先安装在对应 root 的 rootfs 中。若 rootfs 未安装，返回友好的错误提示而非崩溃。
 */
class CliSeatRunner(
    private val workspaceManager: WorkspaceManager,
    private val root: String = "cli-seats",
    private val timeoutMillis: Long = 180_000L,
) {
    suspend fun runSeat(seat: CliSeatConfig, prompt: String): String {
        if (seat.command.isBlank()) {
            return "⚠️ 席位「${seat.name}」命令为空，请先在设置中配置命令。"
        }
        if (!workspaceManager.hasRootfs(root)) {
            return "⚠️ 席位「${seat.name}」无法执行：proot rootfs 未安装（root=$root）。请先在工作区中安装 rootfs，并在其中部署该 CLI 工具。"
        }
        runCatching { workspaceManager.ensureWorkspace(root) }

        val (command, stdin) = when (seat.inputMode) {
            CliSeatInputMode.ARG -> seat.command.replace("{prompt}", prompt.toShellSingleQuoted()) to null
            CliSeatInputMode.STDIN -> seat.command to prompt.toByteArray(Charsets.UTF_8)
        }

        val result: WorkspaceCommandResult = runCatching {
            workspaceManager.executeCommand(
                root = root,
                command = command,
                cwd = "",
                timeoutMillis = timeoutMillis,
                stdin = stdin,
            )
        }.getOrElse {
            return "⚠️ 席位「${seat.name}」执行异常：${it.message}"
        }

        val body = buildString {
            if (result.stdout.isNotBlank()) append(result.stdout)
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("[stderr]\n").append(result.stderr)
            }
        }.trim()

        if (result.timedOut) {
            return "⚠️ 席位「${seat.name}」执行超时（>${timeoutMillis}ms），已截断输出。\n$body"
        }
        if (result.exitCode != 0) {
            return "⚠️ 席位「${seat.name}」以非零状态退出（code=${result.exitCode}）。\n$body"
        }
        if (body.isEmpty()) {
            return "（席位「${seat.name}」无输出）"
        }
        return body
    }

    private fun String.toShellSingleQuoted(): String =
        // 单引号转义标准做法：把 ' 替换成 '\'' 并整体用单引号包裹，避免 prompt 中的特殊字符破坏命令
        "'" + replace("'", "'\\''") + "'"
}
