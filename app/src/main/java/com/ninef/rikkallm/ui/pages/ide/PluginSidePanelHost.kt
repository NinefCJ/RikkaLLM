package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.plugin.extensions.SidePanelContributor

/**
 * 渲染当前选中的插件侧栏面板。未选中任何面板时不占用空间。
 */
@Composable
fun PluginSidePanelHost(
    panels: List<SidePanelContributor>,
    selectedPanelId: String?,
    workspaceId: String?,
    session: EditorSessionManager,
    modifier: Modifier = Modifier,
) {
    val panel = panels.firstOrNull { it.id == selectedPanelId } ?: return
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        panel.Content(workspaceId = workspaceId, session = session)
    }
}
