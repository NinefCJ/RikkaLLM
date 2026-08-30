package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.ninef.rikkallm.data.webmount.WebMountAuthType
import com.ninef.rikkallm.data.webmount.WebMountConfig
import com.ninef.rikkallm.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingWebMountPage(vm: WebMountVM = koinViewModel()) {
    val navController = LocalNavController.current
    val mounts by vm.mounts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页挂载") },
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
            if (mounts.isEmpty()) {
                item {
                    Text("还没有任何站点挂载。点击右上角 + 添加（例如 GitHub 个人访问令牌）。启用后，对应站点会变成一组可读写工具注入给 Agent。")
                }
            }
            items(mounts, key = { it.id }) { mount ->
                ListItem(
                    headlineContent = { Text(mount.name.ifBlank { mount.siteId }) },
                    supportingContent = {
                        Text("${mount.siteId} · ${if (mount.enabled) "已启用" else "已停用"}")
                    },
                    trailingContent = {
                        Row {
                            Switch(
                                checked = mount.enabled,
                                onCheckedChange = { vm.setEnabled(mount.id, it) },
                            )
                            IconButton(onClick = { vm.removeMount(mount.id) }) {
                                Icon(HugeIcons.Delete01, null)
                            }
                        }
                    },
                )
            }
        }
    }

    if (showAdd) {
        AddGitHubDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, token, username, baseUrl ->
                vm.addMount(
                    WebMountConfig(
                        id = Uuid.random().toString(),
                        siteId = "github",
                        name = name,
                        authType = WebMountAuthType.PAT,
                        token = token,
                        username = username,
                        baseUrl = baseUrl,
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
private fun AddGitHubDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, token: String, username: String, baseUrl: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 GitHub 挂载") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token（必填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名 / 组织（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("自定义 API 地址（可选，企业版）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (token.isNotBlank()) onConfirm(name, token, username, baseUrl) }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
