package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ninef.rikkallm.data.editor.EditorSessionManager
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.PencilEdit01

private fun List<DocumentFile>.sortedTree(): List<DocumentFile> =
    sortedWith(compareBy({ !it.isDirectory }, { it.name ?: "" }))

interface FileTreeActions {
    fun onOpen(doc: DocumentFile)
    fun onNewFile(parent: DocumentFile) {}
    fun onNewFolder(parent: DocumentFile) {}
    fun onRename(doc: DocumentFile) {}
    fun onDelete(doc: DocumentFile) {}
}

@Composable
fun FileTree(
    base: DocumentFile,
    actions: FileTreeActions,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roots = remember(base.uri) { base.listFiles().toList().sortedTree() }
    if (roots.isEmpty()) {
        EmptyFileTree(onOpenFolder = onOpenFolder, modifier = modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(roots, key = { it.uri.toString() }) { doc ->
            FileTreeNode(doc, actions, depth = 0, refresh = 0)
        }
    }
}

@Composable
fun FileTree(
    session: EditorSessionManager,
    actions: FileTreeActions,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh by session.treeRefresh.collectAsState()
    val base = session.getBase()
    val roots = remember(base.uri, refresh) { base.listFiles().toList().sortedTree() }
    if (roots.isEmpty()) {
        EmptyFileTree(onOpenFolder = onOpenFolder, modifier = modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(roots, key = { it.uri.toString() }) { doc ->
            FileTreeNode(doc, actions, depth = 0, refresh = refresh)
        }
    }
}

@Composable
private fun EmptyFileTree(onOpenFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "工作区为空",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "点击「打开文件夹」选择项目根目录，或点击下方按钮创建示例文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenFolder) {
            Text("打开文件夹")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeNode(
    doc: DocumentFile,
    actions: FileTreeActions,
    depth: Int,
    refresh: Int,
) {
    var expanded by remember(doc.uri, refresh) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val isDir = doc.isDirectory
    val name = doc.name ?: "?"

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (isDir) expanded = !expanded else actions.onOpen(doc) },
                    onLongClick = { showMenu = true },
                )
                .padding(start = (depth * 14 + 6).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isDir) (if (expanded) "▾ " else "▸ ") else "  ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                fontSize = 13.sp,
                color = if (isDir) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (isDir) {
                DropdownMenuItem(
                    text = { Text("新建文件") },
                    leadingIcon = { Icon(HugeIcons.FileAdd, contentDescription = null) },
                    onClick = { showMenu = false; actions.onNewFile(doc) },
                )
                DropdownMenuItem(
                    text = { Text("新建文件夹") },
                    leadingIcon = { Icon(HugeIcons.FolderAdd, contentDescription = null) },
                    onClick = { showMenu = false; actions.onNewFolder(doc) },
                )
            }
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(HugeIcons.PencilEdit01, contentDescription = null) },
                onClick = { showMenu = false; actions.onRename(doc) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { showMenu = false; actions.onDelete(doc) },
            )
        }
    }

    if (expanded && isDir) {
        val children = remember(doc.uri, refresh) { doc.listFiles().toList().sortedTree() }
        Column {
            children.forEach { FileTreeNode(it, actions, depth + 1, refresh) }
        }
    }
}
