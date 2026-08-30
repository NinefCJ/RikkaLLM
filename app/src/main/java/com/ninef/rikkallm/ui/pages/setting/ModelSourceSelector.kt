package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ninef.rikkallm.data.huggingface.ModelMarketSource
import com.ninef.rikkallm.ui.theme.Spacing

/**
 * 模型源切换选择器，供设置页与模型市场页复用。
 */
@Composable
fun ModelSourceSelector(
    current: ModelMarketSource,
    onSelect: (ModelMarketSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ModelMarketSource.entries.forEach { src ->
            FilterChip(
                selected = current == src,
                onClick = { onSelect(src) },
                label = { Text(src.shortLabel) },
            )
        }
    }
}
