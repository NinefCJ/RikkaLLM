package com.ninef.rikkallm.ui.components.ui
import com.ninef.rikkallm.ui.theme.Spacing

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SwitchSize {
    Small,
    Medium,
    Large
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: SwitchSize = SwitchSize.Medium,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primary,
    trackColorUnchecked: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    thumbColorUnchecked: Color = MaterialTheme.colorScheme.outline
) {
    val dimensions = when (size) {
        SwitchSize.Small -> SwitchDimensions(
            trackWidth = 36.dp,
            trackHeight = 20.dp,
            thumbSize = Spacing.lg,
            thumbPadding = Spacing.xxs
        )

        SwitchSize.Medium -> SwitchDimensions(
            trackWidth = 44.dp,
            trackHeight = Spacing.xl,
            thumbSize = 20.dp,
            thumbPadding = Spacing.xxs
        )

        SwitchSize.Large -> SwitchDimensions(
            trackWidth = 52.dp,
            trackHeight = 28.dp,
            thumbSize = Spacing.xl,
            thumbPadding = Spacing.xxs
        )
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            dimensions.trackWidth - dimensions.thumbSize - dimensions.thumbPadding * 2
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 150),
        label = "thumbOffset"
    )

    val currentTrackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        checked -> trackColor
        else -> trackColorUnchecked
    }

    val currentThumbColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        checked -> thumbColor
        else -> thumbColorUnchecked
    }

    Box(
        modifier = modifier
            .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
            .clip(RoundedCornerShape(50))
            .background(currentTrackColor)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(dimensions.thumbPadding)
                .offset(x = thumbOffset)
                .size(dimensions.thumbSize)
                .shadow(
                    elevation = if (enabled) Spacing.xxs else 0.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(currentThumbColor)
        )
    }
}

private data class SwitchDimensions(
    val trackWidth: Dp,
    val trackHeight: Dp,
    val thumbSize: Dp,
    val thumbPadding: Dp
)

@Composable
@Preview(showBackground = true)
private fun SwitchPreview() {
    Column(
        modifier = Modifier.padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checkedSmall by remember { mutableStateOf(true) }
        var checkedMedium by remember { mutableStateOf(true) }
        var checkedLarge by remember { mutableStateOf(true) }
        var unchecked by remember { mutableStateOf(false) }

        Text("Small", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedSmall,
            onCheckedChange = { checkedSmall = it },
            size = SwitchSize.Small
        )

        Text("Medium (Default)", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedMedium,
            onCheckedChange = { checkedMedium = it },
            size = SwitchSize.Medium
        )

        Text("Large", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedLarge,
            onCheckedChange = { checkedLarge = it },
            size = SwitchSize.Large
        )

        Text("Unchecked", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = unchecked,
            onCheckedChange = { unchecked = it },
            size = SwitchSize.Medium
        )

        Text("Disabled", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = true,
            onCheckedChange = {},
            size = SwitchSize.Medium,
            enabled = false
        )
    }
}
