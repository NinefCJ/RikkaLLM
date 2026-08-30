package com.ninef.rikkallm.ui.pages.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Message01

/**
 * Agent / IDE 分段切换控件。
 * 完全使用 Material 3 的 colorScheme，跟随应用主题（含暗色模式）自动适配。
 */
@Composable
fun ChatModeToggle(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeChip(
                selected = mode == ChatMode.AGENT,
                icon = HugeIcons.Message01,
                label = "Agent",
                compact = compact,
                onClick = { onModeChange(ChatMode.AGENT) },
            )
            ModeChip(
                selected = mode == ChatMode.IDE,
                icon = HugeIcons.Code,
                label = "IDE",
                compact = compact,
                onClick = { onModeChange(ChatMode.IDE) },
            )
        }
    }
}

/**
 * CodeBuddy 风格的大卡片模式选择器。
 * 在聊天空态时展示，让用户选择进入 Agent 对话模式或 IDE 编程模式。
 */
@Composable
fun ChatModeSelector(
    onModeChange: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeCard(
            icon = HugeIcons.Code,
            title = "编程模式",
            subtitle = "让智能 Agent 参与构建与交付代码",
            description = "打开代码工作区，在对话中生成、修改和运行代码。",
            onClick = { onModeChange(ChatMode.IDE) },
        )
        ModeCard(
            icon = HugeIcons.Message01,
            title = "工作模式",
            subtitle = "AI 原生工作台，支持多类型任务",
            description = "与智能助手自由对话，提问、写作、总结、翻译均可。",
            onClick = { onModeChange(ChatMode.AGENT) },
        )
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ModeChip(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (selected) colorScheme.primary else colorScheme.surfaceContainerHighest,
        contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        if (compact) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .size(20.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .width(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
