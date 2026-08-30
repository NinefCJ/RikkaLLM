package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Cpu
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sorting01
import com.ninef.rikkallm.Screen
import com.ninef.rikkallm.data.huggingface.Framework
import com.ninef.rikkallm.data.huggingface.HfModel
import com.ninef.rikkallm.data.huggingface.LicenseType
import com.ninef.rikkallm.data.huggingface.SortOption
import com.ninef.rikkallm.data.huggingface.TaskType
import com.ninef.rikkallm.data.huggingface.formatParamsB
import com.ninef.rikkallm.data.huggingface.paramCountB
import com.ninef.rikkallm.data.huggingface.sliderPosToParamsB
import com.ninef.rikkallm.data.huggingface.taskType
import com.ninef.rikkallm.ui.components.nav.BackButton
import com.ninef.rikkallm.ui.components.ui.CardGroup
import com.ninef.rikkallm.ui.components.ui.Select
import com.ninef.rikkallm.ui.components.ui.SelectTextField
import com.ninef.rikkallm.ui.context.LocalNavController
import com.ninef.rikkallm.ui.context.LocalToaster
import com.ninef.rikkallm.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModelMarketPage() {
    val vm = koinViewModel<ModelMarketVM>()
    val navigator = LocalNavController.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var searchText by remember { mutableStateOf(vm.query.search) }
    var showFilters by remember { mutableStateOf(false) }

    val marketSource by vm.sourceState.collectAsStateWithLifecycle()
    val recommendedSource by vm.recommendedState.collectAsStateWithLifecycle()
    val probingSource by vm.probingState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { toaster.show(it) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("模型市场") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            // 模型源选择（HF / 魔搭 / 自动推荐）
            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                title = { Text("模型源") },
            ) {
                item {
                    Column(Modifier.padding(Spacing.xs)) {
                        Text(
                            "国内访问 Hugging Face 可能较慢，可切换为魔搭社区（ModelScope）以获得更快、更稳定的下载体验。选择「自动」会根据你的网络环境智能推荐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ModelSourceSelector(
                            current = marketSource,
                            onSelect = { vm.setSource(it) },
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        if (probingSource) {
                            Text(
                                "正在检测网络以推荐最佳模型源…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else if (marketSource.isAuto) {
                            Text(
                                "已根据你的网络自动选择：${recommendedSource.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // 搜索
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                placeholder = { Text("搜索模型（如 Qwen, Llama）") },
                singleLine = true,
                leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.onSearch(searchText.trim()) }),
            )

            // 控制栏：筛选开关 + 排序
            val activeCount = listOf(
                vm.sizeRange.start > 0.02f || vm.sizeRange.endInclusive < 0.98f,
                vm.vendor != null,
                vm.query.pipelineTag != null,
                vm.query.libraryName != null,
                vm.query.license != null,
                vm.query.search.isNotBlank(),
            ).count { it }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = showFilters,
                    onClick = { showFilters = !showFilters },
                    leadingIcon = { Icon(HugeIcons.Settings03, null, Modifier.size(18.dp)) },
                    label = { Text(if (activeCount > 0) "筛选 · $activeCount" else "筛选") },
                )
                Select(
                    options = SortOption.entries,
                    selectedOption = vm.sortOption,
                    onOptionSelected = { vm.setSort(it) },
                    modifier = Modifier.weight(1f),
                    optionToString = { it.label },
                    leading = { Icon(HugeIcons.Sorting01, null, Modifier.size(18.dp)) },
                )
            }

            // 可展开筛选面板
            AnimatedVisibility(visible = showFilters) {
                FilterPanel(vm = vm)
            }

            // 结果统计
            Text(
                "共 ${vm.models.size} 个模型" + if (vm.allModels.size != vm.models.size) {
                    "（已从 ${vm.allModels.size} 个候选中筛选）"
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )

            when {
                vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                vm.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.loadPopular() }) { Text("重试") }
                    }
                }

                vm.models.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有符合条件的模型", color = MaterialTheme.colorScheme.outline)
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(vm.models, key = { it.id }) { model ->
                        ModelCard(model = model) {
                            navigator.navigate(Screen.ModelMarketDetail(modelId = model.id))
                        }
                    }
                    if (vm.canLoadMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(Spacing.sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (vm.isLoadingMore) {
                                    CircularProgressIndicator()
                                } else {
                                    Button(onClick = { vm.loadMore() }) { Text("加载更多") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(vm: ModelMarketVM) {
    val taskTypes = listOf(
        TaskType.TEXT_GENERATION,
        TaskType.IMAGE_TEXT_TO_TEXT,
        TaskType.TEXT_TO_IMAGE,
        TaskType.TEXT_TO_SPEECH,
        TaskType.VIDEO_TEXT_TO_TEXT,
    )
    val frameworks = listOf(
        Framework.MNN, Framework.LLAMACPP, Framework.ONNX,
        Framework.PYTORCH, Framework.SAFETENSORS, Framework.DIFFUSERS,
    )
    val licenses = listOf(
        LicenseType.APACHE_2_0, LicenseType.MIT, LicenseType.GPL_3_0,
        LicenseType.AGPL_3_0, LicenseType.LLAMA_2, LicenseType.OTHER,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // 模型大小双头滑块
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Cpu, null, Modifier.size(16.dp))
                    Text(" 模型大小", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    sizeLabel(vm.sizeRange),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            RangeSlider(
                value = vm.sizeRange,
                onValueChange = { vm.updateSizeRange(it) },
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }

        // 厂商下拉
        SelectTextField(
            value = vm.vendor ?: "全部厂商",
            options = listOf<String?>(null) + vm.vendors,
            onOptionSelected = { vm.updateVendor(it) },
            readOnly = true,
            optionToString = { it ?: "全部厂商" },
            placeholder = { Text("厂商") },
        )

        // 模型类型
        FilterChipRow(
            title = "类型",
            icon = HugeIcons.Package,
            options = taskTypes,
            selected = vm.query.pipelineTag,
            valueOf = { it.name },
            optionToString = { it.label },
            onSelect = { vm.setTaskFilter(it?.name) },
        )

        // 框架
        FilterChipRow(
            title = "框架",
            icon = HugeIcons.Database02,
            options = frameworks,
            selected = vm.query.libraryName,
            valueOf = { it.name },
            optionToString = { it.label },
            onSelect = { vm.setFramework(it?.name) },
        )

        // 许可证
        FilterChipRow(
            title = "许可证",
            icon = HugeIcons.GlobalSearch,
            options = licenses,
            selected = vm.query.license,
            valueOf = { it.name },
            optionToString = { it.label },
            onSelect = { vm.setLicense(it?.name) },
        )

        Button(onClick = { vm.resetFilters() }, modifier = Modifier.align(Alignment.End)) {
            Text("重置筛选")
        }
    }
}

@Composable
private fun <T> FilterChipRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<T>,
    selected: String?,
    valueOf: (T) -> String,
    optionToString: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(" $title", style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            androidx.compose.material3.AssistChip(
                onClick = { onSelect(null) },
                label = { Text("全部") },
            )
            options.forEach { opt ->
                val name = optionToString(opt)
                val isSelected = selected == valueOf(opt)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else opt) },
                    label = { Text(name) },
                )
            }
        }
    }
}

private fun sizeLabel(range: ClosedFloatingPointRange<Float>): String {
    val minStr = if (range.start <= 0.02f) "最小" else formatParamsB(sliderPosToParamsB(range.start))
    val maxStr = if (range.endInclusive >= 0.98f) "最大" else formatParamsB(sliderPosToParamsB(range.endInclusive))
    return "$minStr – $maxStr"
}

@Composable
private fun ModelCard(model: HfModel, onClick: () -> Unit) {
    CardGroup {
        item(
            onClick = onClick,
            leadingContent = {
                Icon(HugeIcons.Cpu, null, Modifier.size(28.dp))
            },
            headlineContent = {
                Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        model.repoId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 关键信息：厂商 · 大小 · 类型
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        model.author?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(HugeIcons.Database02, null, Modifier.size(14.dp))
                                Text(" $it", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Cpu, null, Modifier.size(14.dp))
                            Text(
                                " ${if (model.paramCountB() > 0) formatParamsB(model.paramCountB()) else "未知"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Package, null, Modifier.size(14.dp))
                            Text(" ${model.taskType().label}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // 统计：下载量 · 点赞 · 更新时间
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Database02, null, Modifier.size(14.dp))
                            Text(" ${formatCount(model.downloads)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.InLove, null, Modifier.size(14.dp))
                            Text(" ${model.likes}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Clock02, null, Modifier.size(14.dp))
                            Text(" ${model.lastModified?.take(10) ?: "—"}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
        )
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}
