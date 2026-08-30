package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninef.rikkallm.data.plugin.extensions.SidePanelContributor

/**
 * 窄竖条活动栏。文件树开关为固定项，侧栏面板由插件 [SidePanelContributor] 贡献，
 * 因此社区插件只需注册面板即可在此获得入口。
 */
@Composable
fun ActivityBar(
    showTree: Boolean,
    onToggleTree: () -> Unit,
    panels: List<SidePanelContributor>,
    selectedPanelId: String?,
    onSelectPanel: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BarItem(selected = showTree, onClick = onToggleTree) {
            Text(
                text = "F",
                fontSize = 16.sp,
                color = if (showTree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        panels.forEach { panel ->
            val selected = selectedPanelId == panel.id
            BarItem(
                selected = selected,
                onClick = { onSelectPanel(if (selectedPanelId == panel.id) null else panel.id) },
            ) {
                Text(
                    text = panel.title.firstOrNull()?.toString() ?: "?",
                    fontSize = 16.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarItem(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(40.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
