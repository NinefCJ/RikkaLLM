package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 统一间距与形状尺度，替代页面中散落的硬编码 dp 值，确保视觉节奏一致。
 *
 * 使用方式：`Spacing.lg`、`Radius.md` 等语义化命名，避免魔法数字。
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 页面左右安全边距 */
    val screenHorizontal = 16.dp

    /** 卡片内部内边距 */
    val cardPadding = 16.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}
