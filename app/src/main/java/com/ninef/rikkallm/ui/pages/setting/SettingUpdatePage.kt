package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninef.rikkallm.BuildConfig
import com.ninef.rikkallm.ui.context.LocalNavController
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.datastore.UpdateChannel
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.CardGroup
import com.ninef.rikkallm.ui.theme.CustomColors
import com.ninef.rikkallm.ui.theme.Spacing
import com.ninef.rikkallm.utils.DownloadProgress
import com.ninef.rikkallm.utils.UpdateChecker
import com.ninef.rikkallm.utils.UiState
import com.ninef.rikkallm.utils.Version
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun SettingUpdatePage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsStore: SettingsStore = koinInject()
    val checker: UpdateChecker = koinInject()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val updateState by checker.updateState.collectAsStateWithLifecycle()
    val downloadState by checker.downloadState.collectAsStateWithLifecycle()

    val channel = settings?.updateChannel ?: UpdateChannel.STABLE

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeFlexibleTopAppBar(
                title = { Text("更新设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 当前安装版本
            CardGroup(modifier = Modifier.padding(horizontal = Spacing.sm)) {
                item(
                    onClick = {},
                    headlineContent = { Text("当前版本") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }

            // 更新渠道选择
            CardGroup(modifier = Modifier.padding(horizontal = Spacing.sm)) {
                item(
                    onClick = {
                        scope.launch {
                            settingsStore.update { it.copy(updateChannel = UpdateChannel.STABLE) }
                        }
                    },
                    leadingContent = {
                        RadioButton(selected = channel == UpdateChannel.STABLE, onClick = null)
                    },
                    headlineContent = { Text("仅正式 Release") },
                    supportingContent = { Text("只接收稳定版更新，忽略 Debug / 预发布。") },
                )
                item(
                    onClick = {
                        scope.launch {
                            settingsStore.update { it.copy(updateChannel = UpdateChannel.INCLUDE_DEBUG) }
                        }
                    },
                    leadingContent = {
                        RadioButton(selected = channel == UpdateChannel.INCLUDE_DEBUG, onClick = null)
                    },
                    headlineContent = { Text("同时获取 Debug 更新") },
                    supportingContent = { Text("也会接收 GitHub 的预发布（prerelease）构建。") },
                )
            }

            // 更新状态 / 下载 / 安装
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("更新状态", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { checker.refresh() }) {
                            Text("立即检查")
                        }
                    }

                    when (val state = updateState) {
                        is UiState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                CircularProgressIndicator()
                                Text("正在检查更新…")
                            }
                        }

                        is UiState.Error -> {
                            Text(
                                "检查更新失败：${state.error.message ?: "未知错误"}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        is UiState.Success -> {
                            val info = state.data
                            val hasUpdate = Version(info.version) > Version(BuildConfig.VERSION_NAME)
                            Text("最新版本：${info.version}${if (info.isPrerelease) "（调试 / 预发布）" else ""}")

                            if (hasUpdate) {
                                val download = info.downloads.first()
                                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))

                                when (val dp = downloadState) {
                                    is DownloadProgress.Idle -> {
                                        Button(onClick = { checker.startDownload(context, download) }) {
                                            Text("下载更新（${download.size}）")
                                        }
                                    }

                                    is DownloadProgress.Downloading -> {
                                        if (dp.progress >= 0f) {
                                            LinearProgressIndicator(
                                                progress = { dp.progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "下载中：${formatBytes(dp.bytes)} / ${formatBytes(dp.total)}（${String.format("%.0f%%", dp.progress * 100)}）",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        } else {
                                            LinearProgressIndicator(Modifier.fillMaxWidth())
                                            Text(
                                                "下载中：${formatBytes(dp.bytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }

                                    is DownloadProgress.Completed -> {
                                        Text(
                                            "下载完成，点击下方按钮前往安装。",
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Button(onClick = { checker.openInstall(context) }) {
                                            Text("前往安装")
                                        }
                                    }

                                    is DownloadProgress.Failed -> {
                                        Text(
                                            dp.reason ?: "下载失败",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Button(onClick = { checker.startDownload(context, download) }) {
                                            Text("重试下载")
                                        }
                                    }
                                    else -> Unit
                                }

                                if (downloadState !is DownloadProgress.Completed) {
                                    InstallGuide()
                                }
                            } else {
                                Text("已是最新版本，无需更新。")
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallGuide() {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text("安装指引", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Text(
            "1. 下载完成后点击“前往安装”。\n" +
                "2. 若系统提示“禁止安装未知应用”，请在弹窗中允许本应用安装未知应用，再返回继续。\n" +
                "3. 安装完成后建议重新启动应用。\n" +
                "注意：出于安全限制，Android 不会静默安装，必须手动确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val idx = digitGroups.coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(idx.toDouble())) + " " + units[idx]
}
