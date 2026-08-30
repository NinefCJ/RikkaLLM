package com.ninef.rikkallm.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.GenerativeCardData
import me.rerere.ai.ui.GenerativeCardItem
import com.ninef.rikkallm.ui.components.richtext.HighlightCodeBlock
import com.ninef.rikkallm.ui.theme.Spacing
import com.ninef.rikkallm.utils.openUrl

/**
 * 生成式 UI 卡片渲染器。
 *
 * 渲染 [GenerativeCardData]（模型经 `:::generative-ui` 围栏输出的结构化卡片）。
 * 数据已由 [com.ninef.rikkallm.data.ai.generativeui.GenerativeUiSanitizer] 清洗：
 * 字段限长、链接仅 http/https、纯文本渲染（无 HTML/脚本执行面）。
 */
@Composable
fun GenerativeCardView(
    card: GenerativeCardData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Spacing.xxs,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // 卡片类型徽章（非默认 kind 时展示，纯文本标签）
            if (card.kind.isNotBlank() && card.kind != "card") {
                Text(
                    text = card.kind,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (card.title.isNotBlank()) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            val subtitle = card.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                )
            }

            card.items.forEach { item ->
                GenerativeCardItemView(item = item, onOpenLink = { context.openUrl(it) })
            }

            val footer = card.footer
            if (!footer.isNullOrBlank()) {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                )
            }
        }
    }
}

@Composable
private fun GenerativeCardItemView(
    item: GenerativeCardItem,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        if (item.label.isNotBlank() && item.value.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.38f),
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            if (item.label.isNotBlank()) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                )
            }
            if (item.value.isNotBlank()) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val detail = item.detail
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
            )
        }

        val code = item.code
        if (!code.isNullOrBlank()) {
            HighlightCodeBlock(
                code = code,
                language = "",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val link = item.link
        if (!link.isNullOrBlank()) {
            TextButton(
                onClick = { onOpenLink(link) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.xxs,
                    vertical = 0.dp
                ),
            ) {
                Text(
                    text = link,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
