package com.ninef.rikkallm.ui.pages.ide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.ninef.rikkallm.data.plugin.PluginManager
import org.koin.compose.koinInject

/**
 * 轻量 IDE 面板，供聊天页 [ChatMode.IDE] 内嵌。
 * 把「插入对话」回调注入插件管理器，使 AI 助手侧栏能把文件内容送入当前对话。
 */
@Composable
fun IdePane(
    modifier: Modifier = Modifier,
    workspaceId: String? = null,
    onInsertToChat: (String) -> Unit = {},
) {
    val pluginManager: PluginManager = koinInject()

    SideEffect { pluginManager.chatInserter = onInsertToChat }
    DisposableEffect(Unit) {
        onDispose { pluginManager.chatInserter = null }
    }

    androidx.compose.foundation.layout.Box(modifier) {
        IdePage(workspaceId = workspaceId)
    }
}
