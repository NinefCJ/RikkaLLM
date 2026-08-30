package com.ninef.rikkallm.data.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * 轻量编辑器的会话管理：管理打开的标签页、活动文件、内容缓冲与撤销/重做栈。
 *
 * 存储完全基于 [DocumentFile]（应用私有目录或 SAF 授权目录），不依赖 proot/rootfs，
 * 因此编辑器可在毫秒级直接读写工作区真实文件，无需 180MB 的 code-server 运行时。
 *
 * 该单例同时是 AI 工具与插件的统一会话面。
 */
class EditorSessionManager(private val context: Context) {
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _treeRefresh = MutableStateFlow(0)
    val treeRefresh: StateFlow<Int> = _treeRefresh.asStateFlow()

    private var base: DocumentFile =
        DocumentFile.fromFile(context.getDir("ide_projects", Context.MODE_PRIVATE))

    private val undoStack = ArrayDeque<Pair<String, String>>() // (tabId, previousContent)
    private val redoStack = ArrayDeque<Pair<String, String>>()

    fun getActiveTab(): EditorTab? = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun getBase(): DocumentFile = base

    /** 强制刷新文件树观察（递增信号以触发依赖 [treeRefresh] 的 UI 重组）。 */
    fun refreshTree() {
        _treeRefresh.value += 1
    }

    /** 通过 SAF 选择一个文件夹作为编辑器根目录（持久化授权）。 */
    fun setBaseFromTree(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        DocumentFile.fromTreeUri(context, uri)?.let { base = it }
    }

    /**
     * 把某个应用内工作区（[root] 为 WorkspaceEntity.root）的 files 目录直接作为编辑器根目录。
     * 经由 [com.ninef.rikkallm.data.provider.WorkspaceDocumentsProvider] 暴露的 SAF 树 URI 访问，
     * 无需 SAF 持久化授权（同 UID 即可访问），从而让"编程模式"能直接打开对应工作区。
     */
    fun setBaseFromWorkspace(root: String) {
        val authority = context.packageName + ".documents"
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "ws/$root")
        DocumentFile.fromTreeUri(context, treeUri)?.let { base = it }
        refreshTree()
    }

    /**
     * 从设置中恢复持久化的默认工作区目录。
     * 返回 true 表示已成功加载默认工作区，false 表示未配置或解析失败（应使用内置工作区）。
     */
    fun loadDefaultWorkspace(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return false
        base = doc
        return true
    }

    suspend fun openFile(doc: DocumentFile) = withContext(Dispatchers.IO) {
        val uri = doc.uri
        val existing = _tabs.value.firstOrNull { it.uri == uri }
        if (existing != null) {
            _activeTabId.value = existing.id
            return@withContext
        }
        val content = readUri(uri) ?: ""
        val tab = EditorTab(
            id = uri.toString(),
            name = doc.name ?: "untitled",
            path = doc.name ?: "untitled",
            uri = uri,
            content = content,
            dirty = false,
            language = languageOf(doc.name ?: ""),
        )
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
    }

    fun setActive(id: String) {
        _activeTabId.value = id
    }

    fun updateActiveContent(newContent: String) {
        val id = _activeTabId.value ?: return
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        if (tab.content == newContent) return
        undoStack.addLast(tab.content to id)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        _tabs.value = _tabs.value.map {
            if (it.id == id) it.copy(content = newContent, dirty = true) else it
        }
    }

    fun undo(): String? {
        val last = if (undoStack.isNotEmpty()) undoStack.removeLast() else null
        val (prevContent, id) = last ?: return null
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return null
        redoStack.addLast(tab.content to id)
        _tabs.value = _tabs.value.map {
            if (it.id == id) it.copy(content = prevContent, dirty = true) else it
        }
        _activeTabId.value = id
        return prevContent
    }

    fun redo(): String? {
        val last = if (redoStack.isNotEmpty()) redoStack.removeLast() else null
        val (nextContent, id) = last ?: return null
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return null
        undoStack.addLast(tab.content to id)
        _tabs.value = _tabs.value.map {
            if (it.id == id) it.copy(content = nextContent, dirty = true) else it
        }
        _activeTabId.value = id
        return nextContent
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    suspend fun saveActive(): Boolean = withContext(Dispatchers.IO) {
        val tab = getActiveTab() ?: return@withContext false
        val ok = writeUri(tab.uri, tab.content)
        if (ok) {
            _tabs.value = _tabs.value.map { if (it.id == tab.id) it.copy(dirty = false) else it }
        }
        ok
    }

    fun closeTab(id: String) {
        _tabs.value = _tabs.value.filter { it.id != id }
        if (_activeTabId.value == id) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
    }

    suspend fun newFile(parent: DocumentFile, name: String) = withContext(Dispatchers.IO) {
        val doc = parent.createFile("text/plain", name) ?: return@withContext
        writeUri(doc.uri, "")
        openFile(doc)
        _treeRefresh.value += 1
    }

    suspend fun newFolder(parent: DocumentFile, name: String) = withContext(Dispatchers.IO) {
        parent.createDirectory(name)
        _treeRefresh.value += 1
    }

    suspend fun renameFile(doc: DocumentFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching { doc.renameTo(newName) }.getOrDefault(false)
        if (ok) {
            val uri = doc.uri
            _tabs.value = _tabs.value.map {
                if (it.uri == uri) it.copy(name = newName, path = newName) else it
            }
            _treeRefresh.value += 1
        }
        ok
    }

    suspend fun deleteFile(doc: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        val uri = doc.uri
        val ok = runCatching { doc.delete() }.getOrDefault(false)
        if (ok) {
            _tabs.value = _tabs.value.filter { it.uri != uri }
            if (_activeTabId.value?.let { id -> _tabs.value.none { it.id == id } } == true) {
                _activeTabId.value = _tabs.value.lastOrNull()?.id
            }
            _treeRefresh.value += 1
        }
        ok
    }

    /** 在空项目根目录下创建一组欢迎示例文件，避免首次进入时文件树完全空白。 */
    suspend fun ensureWelcomeFiles() = withContext(Dispatchers.IO) {
        if (base.listFiles().isNotEmpty()) return@withContext
        val readme = base.createFile("text/plain", "README.md") ?: return@withContext
        writeUri(readme.uri, WELCOME_README)
        val sample = base.createFile("text/plain", "main.kt") ?: return@withContext
        writeUri(sample.uri, WELCOME_KOTLIN)
        _treeRefresh.value += 1
    }

    private fun readUri(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    private fun writeUri(uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
        true
    }.getOrDefault(false)
}

data class EditorTab(
    val id: String,
    val name: String,
    val path: String,
    val uri: Uri,
    val content: String,
    val dirty: Boolean,
    val language: String,
)

/** 根据文件名推断高亮语言（与 highlight 模块的语言标识对齐）。 */
private const val WELCOME_README = """# Rikka IDE 工作区

欢迎使用内置轻量 IDE。

- 从左侧文件树点击文件即可在编辑器中打开。
- 使用顶部工具栏的「保存」按钮或 Ctrl+S 保存修改。
- 支持 Kotlin、Java、Python、JavaScript/TypeScript、JSON、XML 等语法高亮。
- 通过「查找/替换」可以快速定位并替换文本。
"""

private const val WELCOME_KOTLIN = """fun main() {
    println("Hello, Rikka IDE!")
}
"""

fun languageOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js", "mjs", "cjs" -> "javascript"
        "ts" -> "typescript"
        "tsx", "jsx" -> "tsx"
        "json" -> "json"
        "xml", "html", "htm" -> "xml"
        "css" -> "css"
        "scss" -> "scss"
        "md", "markdown" -> "markdown"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp" -> "cpp"
        "go" -> "go"
        "rs" -> "rust"
        "rb" -> "ruby"
        "sh", "bash", "zsh" -> "shell"
        "sql" -> "sql"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "gradle" -> "gradle"
        "dart" -> "dart"
        "php" -> "php"
        "swift" -> "swift"
        "lua" -> "lua"
        "r" -> "r"
        "pl" -> "perl"
        "vue" -> "vue"
        // 以下为常见但此前未覆盖的扩展名, 提升移动端编辑时的高亮覆盖面
        "dockerfile" -> "dockerfile"
        "makefile", "mk" -> "makefile"
        "properties" -> "properties"
        "ini", "cfg", "conf" -> "ini"
        "editorconfig" -> "ini"
        "gitignore" -> "gitignore"
        "env" -> "dotenv"
        "csv" -> "csv"
        "log", "trace" -> "log"
        "tf", "tfvars", "hcl" -> "hcl"
        "proto" -> "proto"
        "graphql", "gql" -> "graphql"
        "ps1", "psm1" -> "powershell"
        "bat", "cmd" -> "batch"
        "tex" -> "latex"
        "scala" -> "scala"
        "groovy" -> "groovy"
        "clj", "cljs" -> "clojure"
        "hs" -> "haskell"
        "ex", "exs" -> "elixir"
        "nim" -> "nim"
        "zig" -> "zig"
        else -> "text"
    }
}
