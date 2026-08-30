package com.ninef.rikkallm.data.plugin

import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.plugin.extensions.*

/**
 * 插件上下文：暴露给插件的注册入口与会话句柄。
 * 核心在启动时通过 [PluginManager] 构造并注入每个插件。
 */
interface PluginContext {
    val sessionManager: EditorSessionManager

    /** 把文本插入到当前对话（仅在 IDE 嵌入聊天页时可用，否则空实现）。 */
    fun insertIntoChat(text: String)

    fun registerSidePanel(c: SidePanelContributor)
    fun registerEditorAction(c: EditorActionContributor)
    fun registerFileAction(c: FileActionContributor)
    fun registerCompletionProvider(c: CompletionProvider)
    fun registerLanguageSupport(c: LanguageSupportProvider)
    fun registerDiagnosticProvider(c: DiagnosticProvider)
    fun registerCommandExecutor(c: CommandExecutor)
}

/**
 * 插件接口。社区方案实现此接口并在 [PluginManager] 注册即可扩展 IDE。
 */
interface IdePlugin {
    val id: String
    val name: String
    fun initialize(context: PluginContext)
}
