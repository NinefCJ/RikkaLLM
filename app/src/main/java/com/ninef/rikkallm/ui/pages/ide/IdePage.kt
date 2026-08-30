package com.ninef.rikkallm.ui.pages.ide

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.editor.EditorTab
import com.ninef.rikkallm.data.repository.WorkspaceRepository
import com.ninef.rikkallm.data.plugin.PluginManager
import com.ninef.rikkallm.data.plugin.extensions.SidePanelContributor
import com.ninef.rikkallm.ui.context.LocalToaster
import com.ninef.rikkallm.ui.hooks.rememberUserSettingsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Folder02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import org.koin.compose.koinInject

private const val TREE_WIDTH_DP = 220
private const val PANEL_WIDTH_DP = 260
private const val COMPACT_SCREEN_MAX_DP = 600

@Composable
fun IdePage(workspaceId: String?) {
    val session: EditorSessionManager = koinInject()
    val pluginManager: PluginManager = koinInject()
    val workspaceRepository: WorkspaceRepository = koinInject()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val settingsState = rememberUserSettingsState()
    val settings = settingsState.value
    val configuration = LocalConfiguration.current
    val isCompact = remember(configuration.screenWidthDp) {
        configuration.screenWidthDp <= COMPACT_SCREEN_MAX_DP
    }

    val tabs by session.tabs.collectAsState()
    val activeId by session.activeTabId.collectAsState()
    val active = tabs.firstOrNull { it.id == activeId }

    // 编辑器内容必须与活动标签页内容保持同步。
    var editorValue by remember(activeId) { mutableStateOf(TextFieldValue(active?.content ?: "")) }
    var editorSelection by remember { mutableStateOf(TextRange.Zero) }

    var showTree by rememberSaveable { mutableStateOf(!isCompact) }
    var showTreeDrawer by remember { mutableStateOf(false) }
    var selectedPanelId by remember { mutableStateOf<String?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    val findFocusRequester = remember { FocusRequester() }
    var cursor by remember { mutableStateOf(1 to 1) }
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFileParent by remember { mutableStateOf<DocumentFile?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentFile?>(null) }

    val panels = pluginManager.sidePanels()
    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            session.setBaseFromTree(it)
            scope.launch { session.ensureWelcomeFiles() }
        }
    }

    LaunchedEffect(Unit) {
        if (workspaceId != null) {
            // 从"编程模式"进入时, 把对应工作区的 files 目录作为编辑器根, 而非默认配置目录
            val ws = runCatching { workspaceRepository.getById(workspaceId) }.getOrNull()
            if (ws != null) {
                session.setBaseFromWorkspace(ws.root)
            } else {
                session.loadDefaultWorkspace(settings.ideWorkspaceUri)
            }
        } else {
            session.loadDefaultWorkspace(settings.ideWorkspaceUri)
        }
        session.ensureWelcomeFiles()
    }

    // 当标签页内容从外部变更（打开文件/切换标签/撤销重做）时，把内容写回编辑器。
    LaunchedEffect(active?.content) {
        active?.let {
            if (editorValue.text != it.content) {
                editorValue = TextFieldValue(
                    text = it.content,
                    selection = editorSelection,
                )
            }
        }
    }

    DisposableEffect(activeId) {
        onDispose { editorSelection = editorValue.selection }
    }

    fun ensureEditorSelection() {
        editorSelection = editorValue.selection
    }

    fun doFind(forward: Boolean = true) {
        val text = editorValue.text
        if (findQuery.isBlank()) return
        val start = if (forward) {
            (editorValue.selection.end + 1).coerceAtMost(text.length)
        } else {
            (editorValue.selection.start - 1).coerceAtLeast(0)
        }
        val idx = if (forward) {
            text.indexOf(findQuery, start, ignoreCase = true)
                .takeIf { it >= 0 }
                ?: text.indexOf(findQuery, 0, ignoreCase = true)
        } else {
            text.lastIndexOf(findQuery, start, ignoreCase = true)
        }
        if (idx >= 0) {
            val end = (idx + findQuery.length).coerceAtMost(text.length)
            editorValue = editorValue.copy(selection = TextRange(idx, end))
        } else {
            toaster.show("未找到")
        }
    }

    fun doReplace() {
        val text = editorValue.text
        val sel = editorValue.selection
        val selected = text.substring(sel.min, sel.max)
        if (selected.equals(findQuery, ignoreCase = true)) {
            val newText = text.replaceRange(sel.min, sel.max, replaceText)
            editorValue = editorValue.copy(
                text = newText,
                selection = TextRange(sel.min, sel.min + replaceText.length),
            )
            session.updateActiveContent(newText)
            ensureEditorSelection()
        } else {
            doFind(true)
        }
    }

    fun replaceAll() {
        val text = editorValue.text
        if (findQuery.isBlank()) return
        val newText = text.replace(findQuery, replaceText, ignoreCase = true)
        if (newText != text) {
            editorValue = editorValue.copy(
                text = newText,
                selection = TextRange(0, 0),
            )
            session.updateActiveContent(newText)
            ensureEditorSelection()
        }
    }

    // 命令面板的可执行命令集合（含文件 / 编辑 / 视图 / 面板 / 标签页）。
    // 在手机端这是触屏触发所有 IDE 操作的主要入口，等价于桌面端的快捷键。
    val commands = buildList {
        add(
            IdeCommand(
                id = "view.command-palette",
                title = "打开命令面板",
                category = "视图",
                icon = HugeIcons.GlobalSearch,
                shortcut = "Ctrl+Shift+P",
            ) {
                showPalette = true
            },
        )
        add(
            IdeCommand("file.open-folder", "打开项目文件夹", "文件", HugeIcons.Folder02, shortcut = "Ctrl+O") {
                openTree.launch(null)
            },
        )
        add(
            IdeCommand("file.new", "新建文件", "文件", HugeIcons.File02, shortcut = "Ctrl+N") {
                newFileParent = session.getBase()
                showNewFileDialog = true
            },
        )
        if (active != null) {
            add(
                IdeCommand("file.save", "保存当前文件", "文件", HugeIcons.Tick01, shortcut = "Ctrl+S") {
                    scope.launch {
                        if (session.saveActive()) toaster.show("已保存")
                    }
                },
            )
        }
        add(
            IdeCommand("view.toggle-tree", "切换文件树", "视图", HugeIcons.Folder01) {
                if (isCompact) showTreeDrawer = true else showTree = !showTree
            },
        )
        if (active != null) {
            add(
                IdeCommand("edit.undo", "撤销", "编辑", HugeIcons.ArrowLeft01, shortcut = "Ctrl+Z") {
                    session.undo()?.let {
                        editorValue = TextFieldValue(it)
                        ensureEditorSelection()
                    }
                },
            )
            add(
                IdeCommand("edit.redo", "重做", "编辑", HugeIcons.ArrowRight01, shortcut = "Ctrl+Y") {
                    session.redo()?.let {
                        editorValue = TextFieldValue(it)
                        ensureEditorSelection()
                    }
                },
            )
            add(
                IdeCommand("edit.find", "查找", "编辑", HugeIcons.Search01, shortcut = "Ctrl+F") {
                    showFind = true
                    showReplace = false
                },
            )
            add(
                IdeCommand("edit.replace", "替换", "编辑", HugeIcons.Search01, shortcut = "Ctrl+H") {
                    showFind = true
                    showReplace = true
                },
            )
        }
        panels.forEach { panel ->
            add(
                IdeCommand(
                    id = "panel.${panel.id}",
                    title = "打开面板：${panel.title}",
                    category = "面板",
                    icon = null,
                ) {
                    selectedPanelId = if (selectedPanelId == panel.id) null else panel.id
                },
            )
        }
        tabs.forEach { tab ->
            add(
                IdeCommand(
                    id = "tab.${tab.id}",
                    title = "跳转到：${tab.name}",
                    category = "标签",
                    icon = HugeIcons.File02,
                ) {
                    session.setActive(tab.id)
                },
            )
        }
    }

    // 外接键盘快捷键：手机端接上蓝牙 / USB 键盘后, 让 IDE 支持桌面级操作。
    // 在根布局的 preview 阶段拦截, 命中组合键即消费, 避免被当成字符输入到编辑器。
    val handleIdeKeyEvent: (KeyEvent) -> Boolean = handler@{ event ->
        if (showPalette) return@handler false
        if (event.type != KeyEventType.KeyDown) return@handler false
        if (event.key == Key.Escape) {
            if (showFind) {
                showFind = false
                return@handler true
            }
            return@handler false
        }
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        if (!ctrl) return@handler false
        when (event.key) {
            Key.S -> {
                scope.launch { if (session.saveActive()) toaster.show("已保存") }
                true
            }
            Key.Z -> {
                if (event.isShiftPressed) {
                    session.redo()?.let { editorValue = TextFieldValue(it); ensureEditorSelection() }
                } else {
                    session.undo()?.let { editorValue = TextFieldValue(it); ensureEditorSelection() }
                }
                true
            }
            Key.Y -> {
                session.redo()?.let { editorValue = TextFieldValue(it); ensureEditorSelection() }
                true
            }
            Key.F -> { showFind = true; showReplace = false; true }
            Key.H -> { showFind = true; showReplace = true; true }
            Key.N -> { newFileParent = session.getBase(); showNewFileDialog = true; true }
            Key.O -> { openTree.launch(null); true }
            Key.W -> {
                activeId?.takeIf { it.isNotBlank() }?.let { session.closeTab(it) }
                true
            }
            Key.P -> { showPalette = true; true }
            Key.Tab -> {
                if (tabs.isNotEmpty()) {
                    val idx = tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
                    val next = if (event.isShiftPressed) {
                        tabs[(idx - 1 + tabs.size) % tabs.size]
                    } else {
                        tabs[(idx + 1) % tabs.size]
                    }
                    session.setActive(next.id)
                }
                true
            }
            else -> false
        }
    }

    ProvideCodeHighlighter {
        Scaffold(
            modifier = Modifier.onPreviewKeyEvent(handleIdeKeyEvent),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            active?.name ?: "IDE",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        if (isCompact) {
                            IconButton(onClick = { showTreeDrawer = true }) {
                                Icon(HugeIcons.Folder01, contentDescription = "文件树")
                            }
                        }
                    },
                    actions = {
                        if (isCompact) {
                            // 手机端：只保留最高频的 3 个动作, 其余全部收敛到命令面板,
                            // 避免 TopAppBar 在窄屏上图标溢出。
                            ToolbarAction(
                                icon = HugeIcons.Folder02,
                                label = "打开文件夹",
                                onClick = { openTree.launch(null) },
                            )
                            ToolbarAction(
                                icon = HugeIcons.Tick01,
                                label = "保存",
                                enabled = active != null,
                                onClick = {
                                    scope.launch {
                                        if (session.saveActive()) toaster.show("已保存")
                                    }
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.GlobalSearch,
                                label = "命令面板",
                                onClick = { showPalette = true },
                            )
                        } else {
                            ToolbarAction(
                                icon = HugeIcons.Folder02,
                                label = "打开文件夹",
                                onClick = { openTree.launch(null) },
                            )
                            ToolbarAction(
                                icon = HugeIcons.File02,
                                label = "新建",
                                onClick = {
                                    newFileParent = session.getBase()
                                    showNewFileDialog = true
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.Tick01,
                                label = "保存",
                                enabled = active != null,
                                onClick = {
                                    scope.launch {
                                        if (session.saveActive()) toaster.show("已保存")
                                    }
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.ArrowLeft01,
                                label = "撤销",
                                enabled = active != null && session.canUndo(),
                                onClick = {
                                    session.undo()?.let {
                                        editorValue = TextFieldValue(it)
                                        ensureEditorSelection()
                                    }
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.ArrowRight01,
                                label = "重做",
                                enabled = active != null && session.canRedo(),
                                onClick = {
                                    session.redo()?.let {
                                        editorValue = TextFieldValue(it)
                                        ensureEditorSelection()
                                    }
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.Search01,
                                label = "查找",
                                selected = showFind,
                                onClick = {
                                    showFind = !showFind
                                    showReplace = false
                                },
                            )
                            ToolbarAction(
                                icon = HugeIcons.GlobalSearch,
                                label = "命令面板",
                                onClick = { showPalette = true },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                AnimatedVisibility(visible = tabs.isNotEmpty()) {
                    TabBar(
                        tabs = tabs,
                        activeId = activeId,
                        onSelect = { session.setActive(it) },
                        onClose = { session.closeTab(it) },
                    )
                }

                AnimatedVisibility(visible = showFind) {
                    FindReplaceBar(
                        query = findQuery,
                        onQueryChange = { findQuery = it },
                        replace = replaceText,
                        onReplaceChange = { replaceText = it },
                        showReplace = showReplace,
                        onToggleReplace = { showReplace = !showReplace },
                        onFindPrev = { doFind(false) },
                        onFindNext = { doFind(true) },
                        onReplace = { doReplace() },
                        onReplaceAll = { replaceAll() },
                        focusRequester = findFocusRequester,
                    )
                }

                LaunchedEffect(showFind) {
                    if (showFind) {
                        findFocusRequester.requestFocus()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                ) {
                    Row(Modifier.fillMaxSize()) {
                    if (!isCompact) {
                        ActivityBar(
                            showTree = showTree,
                            onToggleTree = { showTree = !showTree },
                            panels = panels,
                            selectedPanelId = selectedPanelId,
                            onSelectPanel = { selectedPanelId = it },
                        )
                    }

                    if (!isCompact && showTree) {
                        FileTreePanel(
                            session = session,
                            onOpenFolder = { openTree.launch(null) },
                            onOpenFile = { scope.launch { session.openFile(it) } },
                            onNewFile = { parent ->
                                newFileParent = parent
                                showNewFileDialog = true
                            },
                            onNewFolder = { parent ->
                                newFileParent = parent
                                showNewFolderDialog = true
                            },
                            onRename = { doc ->
                                renameTarget = doc
                                showRenameDialog = true
                            },
                            onDelete = { doc ->
                                deleteTarget = doc
                                showDeleteDialog = true
                            },
                            modifier = Modifier.width(TREE_WIDTH_DP.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 4.dp),
                    ) {
                        if (active == null) {
                            EmptyEditor(onOpenFolder = { openTree.launch(null) })
                        } else {
                            CodeEditor(
                                value = editorValue,
                                onValueChange = {
                                    editorValue = it
                                    session.updateActiveContent(it.text)
                                    ensureEditorSelection()
                                    if (settings.ideAutoSave) {
                                        autoSaveJob?.cancel()
                                        autoSaveJob = scope.launch {
                                            delay(800)
                                            session.saveActive()
                                        }
                                    }
                                },
                                language = active.language,
                                fontSize = settings.ideFontSize,
                                showLineNumbers = settings.ideShowLineNumbers,
                                wordWrap = settings.ideWordWrap,
                                onCursor = { line, col ->
                                    cursor = line to col
                                },
                            )
                        }
                    }

                    if (!isCompact && selectedPanelId != null) {
                        PluginSidePanelHost(
                            panels = panels,
                            selectedPanelId = selectedPanelId,
                            workspaceId = workspaceId,
                            session = session,
                            modifier = Modifier.width(PANEL_WIDTH_DP.dp),
                        )
                    }
                    }

                    // 手机端：侧栏面板改为从底部弹出的抽屉, 否则 ActivityBar 被隐藏后用户无法打开 AI 助手等面板。
                    if (isCompact) {
                        val panelId = selectedPanelId
                        if (panelId != null) {
                            val sheetPanel = panels.firstOrNull { it.id == panelId }
                            if (sheetPanel != null) {
                                PanelSheet(
                                    panels = panels,
                                    selectedPanelId = panelId,
                                    workspaceId = workspaceId,
                                    session = session,
                                    onClose = { selectedPanelId = null },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                StatusBar(
                    active = active,
                    cursor = cursor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    CommandPalette(
        visible = showPalette,
        commands = commands,
        onDismiss = { showPalette = false },
        onRun = { cmd ->
            cmd.run()
            showPalette = false
        },
    )

    if (showTreeDrawer) {
        FileTreeDrawer(
            session = session,
            onOpenFolder = { openTree.launch(null) },
            onOpenFile = { doc ->
                scope.launch { session.openFile(doc) }
                showTreeDrawer = false
            },
            onNewFile = { parent ->
                newFileParent = parent
                showNewFileDialog = true
                showTreeDrawer = false
            },
            onNewFolder = { parent ->
                newFileParent = parent
                showNewFolderDialog = true
                showTreeDrawer = false
            },
            onRename = { doc ->
                renameTarget = doc
                showRenameDialog = true
                showTreeDrawer = false
            },
            onDelete = { doc ->
                deleteTarget = doc
                showDeleteDialog = true
                showTreeDrawer = false
            },
            onDismiss = { showTreeDrawer = false },
        )
    }

    NewFileOrFolderDialog(
        show = showNewFileDialog,
        parent = newFileParent,
        isFolder = false,
        onDismiss = { showNewFileDialog = false },
        onConfirm = { name ->
            showNewFileDialog = false
            newFileParent?.let { parent ->
                scope.launch { session.newFile(parent, name) }
            }
        },
    )

    NewFileOrFolderDialog(
        show = showNewFolderDialog,
        parent = newFileParent,
        isFolder = true,
        onDismiss = { showNewFolderDialog = false },
        onConfirm = { name ->
            showNewFolderDialog = false
            newFileParent?.let { parent ->
                scope.launch { session.newFolder(parent, name) }
            }
        },
    )

    RenameDialog(
        show = showRenameDialog,
        target = renameTarget,
        onDismiss = { showRenameDialog = false },
        onConfirm = { newName ->
            showRenameDialog = false
            renameTarget?.let { doc ->
                scope.launch {
                    val ok = session.renameFile(doc, newName)
                    if (ok) {
                        session.refreshTree()
                        toaster.show("已重命名")
                    } else {
                        toaster.show("重命名失败")
                    }
                }
            }
        },
    )

    DeleteConfirmDialog(
        show = showDeleteDialog,
        target = deleteTarget,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            deleteTarget?.let { doc ->
                scope.launch {
                    val ok = session.deleteFile(doc)
                    if (ok) {
                        session.refreshTree()
                        toaster.show("已删除")
                    } else {
                        toaster.show("删除失败")
                    }
                }
            }
        },
    )
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FileTreePanel(
    session: EditorSessionManager,
    onOpenFolder: () -> Unit,
    onOpenFile: (DocumentFile) -> Unit,
    onNewFile: (DocumentFile) -> Unit,
    onNewFolder: (DocumentFile) -> Unit,
    onRename: (DocumentFile) -> Unit,
    onDelete: (DocumentFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(end = 1.dp),
    ) {
        FileTree(
            session = session,
            actions = object : FileTreeActions {
                override fun onOpen(doc: DocumentFile) = onOpenFile(doc)
                override fun onNewFile(parent: DocumentFile) = onNewFile(parent)
                override fun onNewFolder(parent: DocumentFile) = onNewFolder(parent)
                override fun onRename(doc: DocumentFile) = onRename(doc)
                override fun onDelete(doc: DocumentFile) = onDelete(doc)
            },
            onOpenFolder = onOpenFolder,
        )
    }
}

@Composable
private fun EmptyEditor(onOpenFolder: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "从左侧文件树打开一个文件开始编辑",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenFolder) {
                Text("打开项目文件夹")
            }
        }
    }
}

@Composable
private fun TabBar(
    tabs: List<EditorTab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            val selected = tab.id == activeId
            Row(
                modifier = Modifier
                    .clickable { onSelect(tab.id) }
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                        },
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.name + if (tab.dirty) " ●" else "",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { onClose(tab.id) },
                    modifier = Modifier.size(18.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replace: String,
    onReplaceChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: () -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("查找") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(fontSize = 14.sp),
            )
            IconButton(onClick = onFindPrev) {
                Icon(HugeIcons.ArrowUp01, contentDescription = "上一个", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onFindNext) {
                Icon(HugeIcons.ArrowDown01, contentDescription = "下一个", modifier = Modifier.size(20.dp))
            }
            TextButton(onClick = onToggleReplace) {
                Text(if (showReplace) "隐藏替换" else "替换", fontSize = 13.sp)
            }
        }
        AnimatedVisibility(visible = showReplace) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = replace,
                    onValueChange = onReplaceChange,
                    placeholder = { Text("替换为") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 14.sp),
                )
                TextButton(onClick = onReplace) { Text("替换", fontSize = 13.sp) }
                TextButton(onClick = onReplaceAll) { Text("全部", fontSize = 13.sp) }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun StatusBar(
    active: EditorTab?,
    cursor: Pair<Int, Int>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            active?.name ?: "-",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (active?.dirty == true) {
            Text(
                "已修改",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Ln ${cursor.first}, Col ${cursor.second}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${active?.content?.length ?: 0} 字符",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewFileOrFolderDialog(
    show: Boolean,
    parent: DocumentFile?,
    isFolder: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show || parent == null) return
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "新建文件夹" else "新建文件") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (isFolder) "文件夹名称" else "文件名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameDialog(
    show: Boolean,
    target: DocumentFile?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show || target == null) return
    var name by remember(target.uri) { mutableStateOf(target.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotBlank(),
            ) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DeleteConfirmDialog(
    show: Boolean,
    target: DocumentFile?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show || target == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除文件") },
        text = { Text("确定要删除「${target.name ?: "?"}」吗？此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FileTreeDrawer(
    session: EditorSessionManager,
    onOpenFolder: () -> Unit,
    onOpenFile: (DocumentFile) -> Unit,
    onNewFile: (DocumentFile) -> Unit,
    onNewFolder: (DocumentFile) -> Unit,
    onRename: (DocumentFile) -> Unit,
    onDelete: (DocumentFile) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((TREE_WIDTH_DP + 44).dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {},
        ) {
            Row(Modifier.fillMaxSize()) {
                ActivityBar(
                    showTree = true,
                    onToggleTree = {},
                    panels = emptyList(),
                    selectedPanelId = null,
                    onSelectPanel = {},
                    modifier = Modifier.fillMaxHeight(),
                )
                FileTree(
                    session = session,
                    actions = object : FileTreeActions {
                        override fun onOpen(doc: DocumentFile) = onOpenFile(doc)
                        override fun onNewFile(parent: DocumentFile) = onNewFile(parent)
                        override fun onNewFolder(parent: DocumentFile) = onNewFolder(parent)
                        override fun onRename(doc: DocumentFile) = onRename(doc)
                        override fun onDelete(doc: DocumentFile) = onDelete(doc)
                    },
                    onOpenFolder = onOpenFolder,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 手机端侧栏面板的承载方式：从底部弹出的抽屉。
 *
 * 桌面/平板端通过 [ActivityBar] + 右侧常驻栏访问面板；但 [ActivityBar] 在窄屏被隐藏，
 * 若仍用常驻栏会导致编辑器可用宽度被进一步压缩。因此窄屏改为底部抽屉，
 * 触屏点击遮罩即可关闭，且不长期占用编辑区横向空间。
 */
@Composable
private fun PanelSheet(
    panels: List<SidePanelContributor>,
    selectedPanelId: String,
    workspaceId: String?,
    session: EditorSessionManager,
    onClose: () -> Unit,
) {
    val panel = panels.firstOrNull { it.id == selectedPanelId } ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(onClick = onClose),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = panel.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    PluginSidePanelHost(
                        panels = panels,
                        selectedPanelId = selectedPanelId,
                        workspaceId = workspaceId,
                        session = session,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
