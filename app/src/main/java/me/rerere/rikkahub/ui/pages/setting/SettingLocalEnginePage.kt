package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.mnnllm.android.server.LocalMnnManager
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cpu
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

/**
 * 本地模型引擎设置页：管理 :mnn 模块的 OpenAI 兼容本地服务
 * （127.0.0.1 + 每次启动随机 bearer token）。
 */
@Composable
fun SettingLocalEnginePage() {
    val manager: LocalMnnManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current

    var pendingStart by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionNotification)
            }
        },
    )
    PermissionManager(permissionState = permissionState)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (pendingStart && permissionState.allPermissionsGranted) {
            pendingStart = false
            manager.startServer()
        }
    }

    fun copy(text: String) {
        clipboardManager.setText(AnnotatedString(text))
        toaster.show("已复制到剪贴板")
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("本地模型引擎") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.starting) return@ExtendedFloatingActionButton
                    if (!state.running) {
                        if (permissionState.allPermissionsGranted) {
                            manager.startServer()
                        } else {
                            pendingStart = true
                            permissionState.requestPermissions()
                        }
                    } else {
                        manager.stopServer()
                    }
                },
                icon = {
                    if (state.starting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.running) HugeIcons.Stop else HugeIcons.Play,
                            contentDescription = null,
                        )
                    }
                },
                text = {
                    Text(if (state.running) "停止服务" else "启动服务")
                },
                containerColor = if (state.running) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text("服务状态") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Cpu, null) },
                        headlineContent = { Text("运行状态") },
                        supportingContent = {
                            Text(
                                when {
                                    state.starting -> "正在启动…"
                                    state.running -> "运行中（仅本机 127.0.0.1 可访问）"
                                    else -> "已停止"
                                }
                            )
                        },
                    )
                    if (state.running) {
                        val baseUrl = "http://127.0.0.1:${state.port}/v1"
                        item(
                            onClick = { copy(baseUrl) },
                            headlineContent = { Text("服务地址") },
                            supportingContent = { Text(baseUrl) },
                        )
                        item(
                            headlineContent = { Text("访问令牌") },
                            supportingContent = {
                                Text(if (tokenVisible) state.token else "•".repeat(16))
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    if (tokenVisible) {
                                        tokenVisible = false
                                    } else {
                                        tokenVisible = true
                                        copy(state.token)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (tokenVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                    item(
                        headlineContent = { Text("当前模型") },
                        supportingContent = {
                            Text(
                                when {
                                    state.modelLoading -> "正在加载模型…"
                                    state.currentModel != null -> state.currentModel!!
                                    else -> "未加载（通过引擎目录加载 MNN 模型后即可对话）"
                                }
                            )
                        },
                    )
                    if (state.error != null) {
                        item(
                            headlineContent = {
                                Text(
                                    text = "引擎错误",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = state.error ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
