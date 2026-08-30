package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.GlobalSearch

/**
 * 命令面板里的单条命令。
 *
 * [run] 在命令被选中时执行；[category] 仅用于展示分组提示，不参与匹配逻辑之外的语义。
 */
data class IdeCommand(
    val id: String,
    val title: String,
    val category: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val run: () -> Unit,
)

/**
 * 可搜索的命令面板。手机端无实体键盘快捷键，这个面板把所有 IDE 操作（以及打开的标签页）
 * 收敛到一个触屏可搜索的列表里，避免在窄屏上堆满工具栏图标。
 *
 * 输入框支持键盘快捷键：Enter 执行首项，Esc 关闭（桌面/外接键盘场景）。
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    commands: List<IdeCommand>,
    onDismiss: () -> Unit,
    onRun: (IdeCommand) -> Unit,
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    val filtered = remember(commands, query) {
        if (query.isBlank()) {
            commands
        } else {
            commands.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
        }
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        query = ""
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("输入命令或文件名…") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = HugeIcons.GlobalSearch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    textStyle = TextStyle(fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter -> {
                                    filtered.firstOrNull()?.let { onRun(it) }
                                    true
                                }
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        },
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = "无匹配命令",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.id }) { cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRun(cmd) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                cmd.icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = cmd.title,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = cmd.shortcut ?: cmd.category,
                                    fontSize = 11.sp,
                                    color = if (cmd.shortcut != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
