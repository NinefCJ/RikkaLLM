package com.ninef.rikkallm.data.plugin

import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.plugin.extensions.*

/**
 * 插件注册表。
 *
 * - 内置插件在 Koin 启动期通过 `List<IdePlugin>` 注入并统一初始化（编译期注册，零启动阻塞）。
 * - 预留了动态加载接口 [loadFromAssets]，后续社区方案可把插件（dex/脚本）放到
 *   `assets/plugins` 或外部目录按清单约定加载，无需改动核心。
 */
class PluginManager(
    private val session: EditorSessionManager,
    plugins: List<IdePlugin>,
) : PluginContext {
    private val _sidePanels = mutableListOf<SidePanelContributor>()
    private val _editorActions = mutableListOf<EditorActionContributor>()
    private val _fileActions = mutableListOf<FileActionContributor>()
    private val _completionProviders = mutableListOf<CompletionProvider>()
    private val _languageSupports = mutableListOf<LanguageSupportProvider>()
    private val _diagnostics = mutableListOf<DiagnosticProvider>()
    private val _commandExecutors = mutableListOf<CommandExecutor>()

    /** 由 IDE 宿主（如嵌入聊天的 IdePane）注入，用于把编辑器内容送入对话。 */
    var chatInserter: ((String) -> Unit)? = null

    init {
        plugins.forEach { it.initialize(this) }
    }

    override val sessionManager: EditorSessionManager get() = session
    override fun insertIntoChat(text: String) {
        chatInserter?.invoke(text)
    }

    override fun registerSidePanel(c: SidePanelContributor) { _sidePanels.add(c) }
    override fun registerEditorAction(c: EditorActionContributor) { _editorActions.add(c) }
    override fun registerFileAction(c: FileActionContributor) { _fileActions.add(c) }
    override fun registerCompletionProvider(c: CompletionProvider) { _completionProviders.add(c) }
    override fun registerLanguageSupport(c: LanguageSupportProvider) { _languageSupports.add(c) }
    override fun registerDiagnosticProvider(c: DiagnosticProvider) { _diagnostics.add(c) }
    override fun registerCommandExecutor(c: CommandExecutor) { _commandExecutors.add(c) }

    fun sidePanels(): List<SidePanelContributor> = _sidePanels
    fun editorActions(): List<EditorActionContributor> = _editorActions
    fun fileActions(): List<FileActionContributor> = _fileActions
    fun completionProviders(): List<CompletionProvider> = _completionProviders
    fun languageSupports(): List<LanguageSupportProvider> = _languageSupports
    fun diagnostics(): List<DiagnosticProvider> = _diagnostics

    /** 默认命令执行器：核心不提供 shell，缺省返回友好的不可用提示。 */
    fun commandExecutor(): CommandExecutor? = _commandExecutors.firstOrNull()

    /**
     * 预留：从 assets/plugins 或外部目录按清单约定动态加载社区插件。
     * 具体加载机制（dex 合并 / 脚本沙箱）留待后续实现，此处仅声明契约。
     */
    fun loadFromAssets() {
        // TODO: 解析 assets/plugins/<pluginId>/plugin.json，按需加载实现类。
    }
}
