package com.ninef.rikkallm.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 统一间距与形状尺度，替代页面中散落的硬编码 dp 值，确保视觉节奏一致。
 *
 * 使用方式：`Spacing.lg`、`Radius.md` 等语义化命名，避免魔法数字。
 * 间距以 4.dp 为基准递进（xxs=2, xs=4, sm=8, md=12, lg=16, xl=24, xxl=32, xxxl=48），
 * 圆角与 [AppShapes] 共用同一组语义，保证卡片 / 按钮 / 气泡 / 对话框视觉一致。
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** 页面左右安全边距 */
    val screenHorizontal = 16.dp

    /** 页面上下安全边距 */
    val screenVertical = 16.dp

    /** 卡片内部内边距 */
    val cardPadding = 16.dp
}

object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp

    /** 聊天气泡圆角：在 lg 基础上略增，更柔和、现代 */
    val chat = 18.dp

    val xl = 24.dp
    val pill = 999.dp
}
