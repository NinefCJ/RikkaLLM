package com.ninef.rikkallm.ui.components.ui
import com.ninef.rikkallm.ui.theme.Spacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = modifier.weight(1f)
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.titleMedium
            ) {
                label()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.labelSmall.copy(
                    color = LocalContentColor.current.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    description?.invoke()
                }
            }
            content()
        }
        tail()
    }
}

@Preview(showBackground = true)
@Composable
private fun FormItemPreview() {
    FormItem(
        label = { Text("Label") },
        content = {
            OutlinedTextField(
                value = "",
                onValueChange = {}
            )
        },
        description = {
            Text("Description")
        },
        tail = {
            Switch(
                checked = true,
                onCheckedChange = {}
            )
        },
        modifier = Modifier.padding(Spacing.xs),
    )
}
