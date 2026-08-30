package com.ninef.rikkallm.data.graph

import android.util.Log
import com.ninef.rikkallm.data.repository.ConversationRepository
import com.ninef.rikkallm.service.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private const val TAG = "GraphAutoSync"

/**
 * 图谱自动同步观察者（M6 收尾）：收集 [ChatService.generationDoneFlow]，
 * 每当某会话生成完成时，对该会话执行 [GraphOrchestrator.syncFromConversation]，
 * 把新产生的消息追加进 DAG——**非破坏性**，不触达用户已有的 prune / 连线编辑。
 *
 * 在 [com.ninef.rikkallm.RikkaHubApp] 启动时经 [AppScope] 常驻收集，
 * 因此无论用户是否正在查看图谱页，图谱都会随对话演进保持最新。
 */
class GraphAutoSync(
    private val chatService: ChatService,
    private val orchestrator: GraphOrchestrator,
    private val conversationRepo: ConversationRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            chatService.generationDoneFlow.collect { cid: Uuid ->
                runCatching {
                    val conversation = conversationRepo.getConversationById(cid) ?: return@runCatching
                    orchestrator.syncFromConversation(conversation)
                }.onFailure {
                    Log.e(TAG, "auto-sync failed for conversation $cid", it)
                }
            }
        }
    }
}
