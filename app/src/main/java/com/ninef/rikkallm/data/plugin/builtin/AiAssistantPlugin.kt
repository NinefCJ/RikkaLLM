package com.ninef.rikkallm.data.plugin.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ninef.rikkallm.data.editor.EditorSessionManager
import com.ninef.rikkallm.data.plugin.IdePlugin
import com.ninef.rikkallm.data.plugin.PluginContext
import com.ninef.rikkallm.data.plugin.extensions.EditorActionContributor
import com.ninef.rikkallm.data.plugin.extensions.SidePanelContributor

/**
 * 内置示例插件：把「AI 助手」实现为侧栏面板 + 一个编辑动作。
 *
 * 它不依赖任何核心内部实现，仅通过 [PluginContext] 与 [EditorSessionManager] 交互，
 * 因此可作为社区插件的同构接入范式：复制本类、改 id/name、注册自己的扩展点即可。
 */
class AiAssistantPlugin : IdePlugin {
    override val id: String = "builtin.ai-assistant"
    override val name: String = "AI 助手"

    override fun initialize(context: PluginContext) {
        context.registerSidePanel(AiAssistantSidePanel(context))
        context.registerEditorAction(SendToAiAction(context))
    }

    private class AiAssistantSidePanel(private val pluginContext: PluginContext) : SidePanelContributor {
        override val id: String = "ai-assistant.panel"
        override val title: String = "AI 助手"

        @Composable
        override fun Content(workspaceId: String?, session: EditorSessionManager) {
            val tab = session.getActiveTab()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("AI 助手", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                if (tab == null) {
                    Text(
                        "未打开文件。打开一个文件后即可让 AI 阅读并改进它。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tab.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val lines = tab.content.lines().size
                    Text(
                        "$lines 行 · ${tab.content.length} 字符",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val snippet = tab.content.take(8000)
                            pluginContext.insertIntoChat(
                                "请阅读并帮我改进以下文件（${tab.name}）：\n```${tab.language}\n$snippet\n```",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("把文件发给 AI")
                    }
                }
            }
        }
    }

    private class SendToAiAction(private val pluginContext: PluginContext) : EditorActionContributor {
        override val id: String = "ai-assistant.send-active"
        override val label: String = "用 AI 解释当前文件"

        override fun onInvoke(session: EditorSessionManager) {
            val tab = session.getActiveTab() ?: return
            val snippet = tab.content.take(8000)
            pluginContext.insertIntoChat(
                "请解释以下文件（${tab.name}）的作用，并指出可改进点：\n```${tab.language}\n$snippet\n```",
            )
        }
    }
}
