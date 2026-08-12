package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.mnnllm.android.server.LocalMnnManager
import me.rerere.rikkahub.data.mnn.DownloadState
import me.rerere.rikkahub.data.mnn.LocalModelManager
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

    var pendingStartPort by remember { mutableStateOf<Int?>(null) }
    var tokenVisible by remember { mutableStateOf(false) }
    var portText by remember { mutableStateOf(state.port.toString()) }
    var modelDirText by remember { mutableStateOf(state.modelDir.orEmpty()) }

    val modelManager: LocalModelManager = koinInject()
    val installed by modelManager.installed.collectAsStateWithLifecycle()
    val downloadState by modelManager.downloadState.collectAsStateWithLifecycle()

    // Populate the installed-models list from disk when the page opens.
    LaunchedEffect(Unit) {
        modelManager.refresh()
    }

    // Keep the port field in sync when the manager adopts a new port (service start).
    LaunchedEffect(state.port) {
        portText = state.port.toString()
    }

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionNotification)
            }
        },
    )
    PermissionManager(permissionState = permissionState)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        val port = pendingStartPort
        if (port != null && permissionState.allPermissionsGranted) {
            pendingStartPort = null
            manager.startServer(port)
        }
    }

    /** Starts (or restarts on a new port) the server, requesting permissions first. */
    fun startOnPort(port: Int) {
        if (state.running || permissionState.allPermissionsGranted) {
            manager.startServer(port)
        } else {
            pendingStartPort = port
            permissionState.requestPermissions()
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
                        startOnPort(portText.toIntOrNull() ?: state.port)
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
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text("引擎设置") },
                ) {
                    item(
                        headlineContent = { Text("服务端口") },
                        supportingContent = {
                            Column {
                                Text("默认 8090（避免与内置网页服务的 8080 冲突），修改后点击「应用」生效")
                                OutlinedTextField(
                                    value = portText,
                                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    val port = portText.toIntOrNull()
                                    if (port == null || port !in 1..65535) {
                                        toaster.show("端口需在 1 到 65535 之间")
                                        return@TextButton
                                    }
                                    startOnPort(port)
                                },
                                enabled = !state.starting,
                            ) {
                                Text("应用")
                            }
                        },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text("手动加载模型（高级）") },
                ) {
                    item(
                        headlineContent = { Text("模型目录") },
                        supportingContent = {
                            Column {
                                Text("填写包含 MNN 模型 config.json 的本地目录路径")
                                OutlinedTextField(
                                    value = modelDirText,
                                    onValueChange = { modelDirText = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                    item(
                        headlineContent = { Text("加载 / 卸载模型") },
                        supportingContent = {
                            Text(
                                when {
                                    state.modelLoading -> "正在加载模型…"
                                    state.currentModel != null -> "当前模型：${state.currentModel}"
                                    else -> "尚未加载模型"
                                }
                            )
                        },
                        trailingContent = {
                            Row {
                                TextButton(
                                    onClick = {
                                        if (modelDirText.isBlank()) {
                                            toaster.show("请先填写模型目录")
                                            return@TextButton
                                        }
                                        manager.loadModel(modelDirText)
                                        modelManager.refresh()
                                    },
                                    enabled = !state.modelLoading,
                                ) {
                                    Text("加载")
                                }
                                TextButton(
                                    onClick = {
                                        manager.unloadModel()
                                        modelManager.refresh()
                                    },
                                    enabled = !state.modelLoading && state.currentModel != null,
                                ) {
                                    Text("卸载")
                                }
                            }
                        },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text("已安装模型") },
                ) {
                    if (installed.isEmpty()) {
                        item(
                            headlineContent = { Text("暂无已安装模型") },
                            supportingContent = {
                                Text("在下方「模型市场」下载，或到「手动加载模型」指定目录")
                            },
                        )
                    }
                    installed.forEach { info ->
                        item(
                            headlineContent = { Text(info.catalogEntry?.name ?: info.id) },
                            supportingContent = {
                                Column {
                                    Text(
                                        "版本 ${info.version ?: "未知"} · " +
                                            if (info.sizeBytes > 0) {
                                                info.sizeBytes.humanSize()
                                            } else {
                                                "未知大小"
                                            },
                                    )
                                    if (info.isLoaded) {
                                        Text(
                                            "已加载并运行",
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            manager.loadModel(
                                                manager.modelsRoot().resolve(info.id).absolutePath,
                                            )
                                            modelManager.refresh()
                                        },
                                        enabled = !state.modelLoading && !info.isLoaded,
                                    ) {
                                        Text("加载")
                                    }
                                    TextButton(
                                        onClick = {
                                            manager.unloadModel()
                                            modelManager.refresh()
                                        },
                                        enabled = !state.modelLoading && info.isLoaded,
                                    ) {
                                        Text("卸载")
                                    }
                                    TextButton(
                                        onClick = {
                                            if (info.isLoaded) manager.unloadModel()
                                            modelManager.delete(info.id)
                                            modelManager.refresh()
                                        },
                                    ) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    title = { Text("模型市场（下载 / 更新）") },
                ) {
                    val ds = downloadState
                    modelManager.catalog.forEach { model ->
                        val isInstalled = installed.any { it.id == model.id }
                        val updating = modelManager.needsUpdate(model.id)
                        val progress =
                            if (ds is DownloadState.Progress && ds.modelId == model.id) {
                                ds.progress
                            } else {
                                null
                            }
                        val isDownloading = progress != null
                        item(
                            headlineContent = { Text(model.name) },
                            supportingContent = {
                                Column {
                                    Text(model.description)
                                    val approx = model.fileSizes.values.sum()
                                    Text(
                                        buildString {
                                            append("版本 ${model.version}")
                                            if (approx > 0) append(" · 约 ${approx.humanSize()}")
                                            model.minRamMb?.let { append(" · 建议内存 ≥ ${it}MB") }
                                        },
                                    )
                                    if (progress != null) {
                                        val p = progress
                                        Spacer(Modifier.height(8.dp))
                                        if (p.overallTotal > 0) {
                                            val frac = (p.overallBytes.toFloat() / p.overallTotal)
                                                .coerceIn(0f, 1f)
                                            LinearProgressIndicator(
                                                progress = { frac },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        Text(
                                            "下载中：文件 ${p.fileIndex + 1}/${p.fileCount} · " +
                                                "${p.overallBytes.humanSize()}/" +
                                                (if (p.overallTotal > 0) p.overallTotal.humanSize() else "?") +
                                                " · ${p.speedBytesPerSec.humanSize()}/s",
                                        )
                                    }
                                    if (ds is DownloadState.Error && ds.modelId == model.id) {
                                        Text(
                                            "下载失败：${ds.message}",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row {
                                    if (isDownloading) {
                                        TextButton(onClick = { modelManager.cancel() }) {
                                            Text("取消")
                                        }
                                    } else if (isInstalled && !updating) {
                                        TextButton(onClick = { }, enabled = false) {
                                            Text("已安装")
                                        }
                                    } else {
                                        TextButton(onClick = { modelManager.download(model) }) {
                                            Text(if (updating) "更新" else "下载")
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Long.humanSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var i = 0
    while (size >= 1024 && i < units.lastIndex) {
        size /= 1024
        i++
    }
    return "%.1f %s".format(size, units[i])
}
