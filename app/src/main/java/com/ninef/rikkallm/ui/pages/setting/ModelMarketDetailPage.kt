package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Cpu
import com.ninef.rikkallm.data.mnn.CompatibilityLevel
import com.ninef.rikkallm.data.mnn.DownloadState
import com.ninef.rikkallm.data.huggingface.*
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.CardGroup
import com.ninef.rikkallm.ui.context.LocalToaster
import com.ninef.rikkallm.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModelMarketDetailPage(modelId: String) {
    val vm = koinViewModel<ModelMarketVM>()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("模型说明", "配置预览", "环境要求")

    LaunchedEffect(modelId) { vm.openDetail(modelId) }
    LaunchedEffect(Unit) { vm.events.collect { toaster.show(it) } }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(vm.detail?.displayName ?: "模型详情") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val m = vm.detail
            if (m != null) {
                Button(
                    onClick = vm::loadOrConfigure,
                    enabled = !vm.isConfiguring && vm.downloadState !is DownloadState.Progress,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                ) {
                    val label = when {
                        vm.isConfiguring -> "配置中…"
                        vm.downloadState is DownloadState.Progress -> {
                            val p = vm.downloadState as DownloadState.Progress
                            val percent = if (p.progress.overallTotal > 0)
                                (p.progress.overallBytes * 100 / p.progress.overallTotal).toInt()
                            else 0
                            "下载中 $percent%"
                        }
                        m.isMnnFormat -> "一键下载并加载"
                        else -> "一键自动配置"
                    }
                    Text(label)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> DescriptionTab(vm)
                1 -> ConfigPreviewTab(vm)
                2 -> EnvironmentTab(vm)
            }
        }
    }
}

@Composable
private fun DescriptionTab(vm: ModelMarketVM) {
    val model = vm.detail
    if (model == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (vm.isDetailLoading) CircularProgressIndicator() else Text("无数据")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            CardGroup(title = { Text("基本信息") }) {
                item(headlineContent = { InfoRow("作者", model.author ?: model.repoId.substringBefore('/')) })
                item(headlineContent = { InfoRow("任务类型", model.taskType().label) })
                item(headlineContent = { InfoRow("框架", model.framework()?.label ?: "未知") })
                item(headlineContent = { InfoRow("许可证", model.licenseType().label) })
                item(headlineContent = { InfoRow("参数量", "%.1f B".format(model.paramCountB())) })
                item(headlineContent = { InfoRow("下载量", formatCount(model.downloads)) })
                item(headlineContent = { InfoRow("点赞数", "${model.likes}") })
                item(headlineContent = { InfoRow("最后更新", model.lastModified ?: "未知") })
                item(headlineContent = { InfoRow("MNN 格式", if (model.isMnnFormat) "是（可离线运行）" else "否") })
            }
        }
        item {
            CardGroup(title = { Text("标签") }) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(Spacing.sm),
                    ) {
                        model.tags.take(20).forEach { TagChip(it) }
                    }
                }
            }
        }
        item {
            CardGroup(title = { Text("模型说明 (README)") }) {
                item {
                    Text(
                        vm.readme?.lines()?.take(80)?.joinToString("\n") ?: "暂无说明",
                        modifier = Modifier.padding(Spacing.sm),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigPreviewTab(vm: ModelMarketVM) {
    val model = vm.detail ?: return
    val toaster = LocalToaster.current
    val clipboard = LocalClipboardManager.current
    LazyColumn(contentPadding = PaddingValues(Spacing.sm)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(model.toConfigPreview()))
                        toaster.show("已复制配置到剪贴板")
                    },
                ) {
                    Text("复制配置")
                }
            }
        }
        item {
            CardGroup {
                item {
                    Text(
                        model.toConfigPreview(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentTab(vm: ModelMarketVM) {
    val report = vm.envReport
    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (vm.isDetailLoading) CircularProgressIndicator() else Text("检测中…")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            val (color, label) = when (report.compatibility) {
                CompatibilityLevel.COMPATIBLE -> MaterialTheme.colorScheme.primary to "兼容，可运行"
                CompatibilityLevel.NEEDS_ATTENTION -> MaterialTheme.colorScheme.tertiary to "可运行，但需注意"
                CompatibilityLevel.INCOMPATIBLE -> MaterialTheme.colorScheme.error to "不满足运行要求"
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
            ) {
                Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (report.compatibility == CompatibilityLevel.INCOMPATIBLE) HugeIcons.Alert01 else HugeIcons.Cpu,
                        null,
                        tint = color,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            CardGroup(title = { Text("设备环境") }) {
                item(headlineContent = { InfoRow("设备内存", "${report.deviceRamMb} MB") })
                item(headlineContent = { InfoRow("可用存储", "${report.availableStorageMb} MB") })
                item(headlineContent = { InfoRow("CPU 架构", report.abi.joinToString()) })
                item(headlineContent = { InfoRow("CPU 推理", if (report.supportsCpu) "支持" else "不支持") })
                item(headlineContent = { InfoRow("Vulkan 后端", if (report.supportsVulkan) "可用" else "不可用") })
                item(headlineContent = { InfoRow("NNAPI 后端", if (report.supportsNnapi) "可用" else "不可用") })
                item(headlineContent = { InfoRow("MNN 引擎", if (report.mnnAvailable) "已集成" else "未集成") })
                item(headlineContent = { InfoRow("模型体积", "${report.estimatedModelSizeMb} MB") })
                item(headlineContent = { InfoRow("建议内存", "≥ ${report.minRamMb} MB") })
            }
        }
        if (report.issues.isNotEmpty()) item {
            CardGroup(title = { Text("存在问题") }) {
                item {
                    Column(Modifier.padding(Spacing.sm)) {
                        report.issues.forEach { Text("• $it") }
                    }
                }
            }
        }
        if (report.suggestions.isNotEmpty()) item {
            CardGroup(title = { Text("依赖与运行建议") }) {
                item {
                    Column(Modifier.padding(Spacing.sm)) {
                        report.suggestions.forEach { Text("• $it") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TagChip(tag: String) {
    AssistChip(onClick = {}, enabled = false, label = { Text(tag) })
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
