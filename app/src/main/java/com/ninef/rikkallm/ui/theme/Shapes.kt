package com.ninef.rikkallm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 统一圆角尺度：相较 Material Expressive 默认更收敛、克制，弱化"气泡感"，
 * 让卡片 / 按钮 / 对话框 / 输入区呈现一致、安静的现代质感（呼应"简洁化"）。
 * 与圆角 token [Radius] 共用同一组语义值，避免两处定义漂移导致不一致。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
