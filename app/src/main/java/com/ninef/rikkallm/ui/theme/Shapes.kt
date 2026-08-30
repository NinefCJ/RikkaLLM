package com.ninef.rikkallm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 统一圆角尺度：相较 Material Expressive 默认更收敛、克制，弱化"气泡感"，
 * 让卡片 / 按钮 / 对话框 / 输入区呈现一致、安静的现代质感（呼应"简洁化"）。
 * 全部为静态 token，不引入任何运行时开销。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
