package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.Uuid
import com.ninef.rikkallm.data.cliseat.CliSeatConfig
import com.ninef.rikkallm.data.cliseat.CliSeatInputMode
import com.ninef.rikkallm.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingCliSeatPage(vm: CliSeatVM = koinViewModel()) {
    val navController = LocalNavController.current
    val seats by vm.seats.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外部 CLI 席位") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(HugeIcons.Add01, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("将安装在 proot rootfs 中的命令行模型（如 Claude Code、Gemini CLI、Aider 等）作为「席位」纳入模型议会（model_council）。启用后，调用模型议会时会并发征集这些 CLI 工具的答案并一起综合。")
            }
            if (seats.isEmpty()) {
                item {
                    Text("还没有任何 CLI 席位。点击右上角 + 添加。注意：对应 rootfs 需已安装，且 CLI 工具已部署在 rootfs 内。")
                }
            }
            items(seats, key = { it.id }) { seat ->
                ListItem(
                    headlineContent = { Text(seat.name.ifBlank { seat.command }) },
                    supportingContent = {
                        Text("${seat.command.ifBlank { "（命令为空）" }} · ${seat.inputMode.name} · ${if (seat.enabled) "已启用" else "已停用"}")
                    },
                    trailingContent = {
                        Row {
                            Switch(
                                checked = seat.enabled,
                                onCheckedChange = { vm.setEnabled(seat.id, it) },
                            )
                            IconButton(onClick = { vm.removeSeat(seat.id) }) {
                                Icon(HugeIcons.Delete01, null)
                            }
                        }
                    },
                )
            }
        }
    }

    if (showAdd) {
        AddCliSeatDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, command, inputMode ->
                vm.addSeat(
                    CliSeatConfig(
                        id = Uuid.random().toString(),
                        name = name,
                        command = command,
                        inputMode = inputMode,
                        enabled = true,
                    ),
                )
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCliSeatDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String, inputMode: CliSeatInputMode) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var inputMode by remember { mutableStateOf(CliSeatInputMode.ARG) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 CLI 席位") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（议会中的席位标签）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("命令模板（ARG 模式下可用 {prompt} 占位符）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("claude -p \"{prompt}\"") },
                )
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("输入方式：${inputMode.name}")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        CliSeatInputMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    inputMode = mode
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    "ARG：把 {prompt} 替换后作为命令参数；STDIN：命令原样执行，prompt 通过标准输入传入。",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (command.isNotBlank()) onConfirm(name, command, inputMode) }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
