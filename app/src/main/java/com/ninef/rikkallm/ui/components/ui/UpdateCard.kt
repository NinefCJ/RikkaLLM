package com.ninef.rikkallm.ui.components.ui

import com.ninef.rikkallm.ui.theme.Spacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Download01
import com.ninef.rikkallm.BuildConfig
import com.ninef.rikkallm.R
import com.ninef.rikkallm.ui.components.richtext.MarkdownBlock
import com.ninef.rikkallm.ui.context.LocalToaster
import com.ninef.rikkallm.ui.hooks.useThrottle
import com.ninef.rikkallm.ui.pages.chat.ChatVM
import com.ninef.rikkallm.utils.UpdateDownload
import com.ninef.rikkallm.utils.Version
import com.ninef.rikkallm.utils.onError
import com.ninef.rikkallm.utils.onSuccess
import com.ninef.rikkallm.utils.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
@Composable
fun UpdateCard(vm: ChatVM) {
    val state by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    // 仅在成功检测到“有可用更新”时提示；检测中或出错（网络不可用等）一律不提示，
    // 避免侧边栏长期挂着“未知错误”。用户如需查看失败原因可在“设置 → 更新设置”中手动重试。
    state.onSuccess { info ->
        var showDetail by remember { mutableStateOf(false) }
        var dismissed by remember { mutableStateOf(false) }
        val current = remember { Version(BuildConfig.VERSION_NAME) }
        val latest = remember(info) { Version(info.version) }
        if (latest > current && !dismissed) {
            Card(
                onClick = {
                    showDetail = true
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.update_card_new_version_found, info.version),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { dismissed = true }) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.update_card_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    MarkdownBlock(
                        content = info.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 200.dp)
                    )
                }
            }
        }
        if (showDetail) {
            val downloadHandler = useThrottle<UpdateDownload>(500) { item ->
                vm.updateChecker.startDownload(context, item)
                showDetail = false
                toaster.show(context.getString(R.string.update_card_downloading), type = ToastType.Info)
            }
            ModalBottomSheet(
                onDismissRequest = { showDetail = false },
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = info.version,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val publishedText = info.publishedAt.takeIf { it.isNotBlank() }
                        ?.let { runCatching { Instant.parse(it).toJavaInstant().toLocalDateTime() }.getOrNull() }
                        .orEmpty()
                    Text(
                        text = publishedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MarkdownBlock(
                        content = info.changelog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    info.downloads.fastForEach { downloadItem ->
                        OutlinedCard(
                            onClick = {
                                downloadHandler(downloadItem)
                            },
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = downloadItem.name,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = downloadItem.size
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = HugeIcons.Download01,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
