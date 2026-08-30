package com.ninef.rikkallm.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ninef.rikkallm.ui.theme.Radius
import com.ninef.rikkallm.ui.theme.Spacing

/**
 * 统一的内容卡片：轻量容器色 + 大圆角，替代各处手写 Surface/Card。
 *
 * @param onClick 若提供则整卡可点击（带按压反馈）。
 * @param filled 是否为填充强调卡片（用于突出展示），默认 false 使用 surfaceContainerLow。
 */
@Composable
fun RikkaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.lg),
    filled: Boolean = false,
    containerColor: Color = if (filled) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = if (filled) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
    contentAlignment: Alignment = Alignment.TopStart,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * 分组标题：统一区块（Settings/列表区块）的小标题样式，弱化次要层级、突出主操作。
 */
@Composable
fun SectionTitle(
    modifier: Modifier = Modifier,
    text: String,
) {
    ProvideTextStyle(
        value = MaterialTheme.typography.titleSmallEmphasized.copy(
            color = MaterialTheme.colorScheme.primary,
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.lg,
                    bottom = Spacing.sm,
                )
        ) {
            androidx.compose.material3.Text(text = text)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RikkaCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionTitle(text = "示例分组")
            RikkaCard {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    androidx.compose.material3.Text(
                        text = "卡片内容示例，统一圆角与容器色。",
                        color = LocalContentColor.current,
                    )
                }
            }
            RikkaCard(
                filled = true,
                onClick = {},
            ) {
                androidx.compose.material3.Text(text = "可点击的强调卡片")
            }
        }
    }
}
