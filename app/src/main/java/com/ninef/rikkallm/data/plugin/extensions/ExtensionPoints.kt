package com.ninef.rikkallm.data.plugin.extensions

import androidx.compose.runtime.Composable
import com.ninef.rikkallm.data.editor.EditorSessionManager

/**
 * 插件扩展点集合。
 *
 * 这些接口为「社区方案」预留了标准接入方式：任何插件只要实现对应扩展点并在
 * [com.ninef.rikkallm.data.plugin.PluginManager] 注册，即可向 IDE 贡献侧栏面板、编辑动作、
 * 文件动作、代码补全、语言支持、诊断与命令执行能力，无需修改核心代码。
 */

/** 右侧/底部侧面板贡献者（如 AI 助手、搜索、终端占位）。 */
interface SidePanelContributor {
    val id: String
    val title: String
    @Composable
    fun Content(workspaceId: String?, session: EditorSessionManager)
}

/** 编辑器工具栏/右键动作贡献者（如「用 AI 解释」）。 */
interface EditorActionContributor {
    val id: String
    val label: String
    fun onInvoke(session: EditorSessionManager)
}

/** 文件树动作贡献者（如在文件上右键时出现的项）。 */
interface FileActionContributor {
    val id: String
    val label: String
    fun onInvoke(session: EditorSessionManager, fileUri: android.net.Uri)
}

/** 代码补全贡献者（无 LSP，基于片段/关键字）。 */
interface CompletionProvider {
    val id: String
    fun completions(prefix: String, language: String): List<String>
}

/** 语言支持贡献者（语法高亮/格式化等语言相关能力）。 */
interface LanguageSupportProvider {
    val id: String
    fun supports(language: String): Boolean
}

/** 诊断贡献者（错误/警告标记）。 */
interface DiagnosticProvider {
    val id: String
    fun diagnostics(session: EditorSessionManager): List<EditorDiagnostic>
}

/** 命令执行扩展点。核心不内置 shell；终端/CLI 由社区插件通过此接口提供。 */
interface CommandExecutor {
    val id: String
    suspend fun execute(command: String, cwd: String): String
}

data class EditorDiagnostic(
    val message: String,
    val severity: String, // "error" | "warning" | "info"
    val line: Int = -1,
)
