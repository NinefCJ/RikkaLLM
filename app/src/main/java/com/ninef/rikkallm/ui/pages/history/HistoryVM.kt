package com.ninef.rikkallm.ui.pages.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.model.Conversation
import com.ninef.rikkallm.data.repository.ConversationRepository
import com.ninef.rikkallm.data.repository.WorkspaceRepository
import kotlin.uuid.Uuid

private const val TAG = "HistoryVM"

/**
 * 按工作区聚合后的会话分组。
 *
 * @param workspaceId 工作区 id；为 null 表示该组会话未绑定任何工作区
 * @param workspaceName 工作区名称；工作区已被删除时可能为 null
 * @param conversations 该工作区下的会话，按置顶 + 更新时间排序
 */
data class WorkspaceSection(
    val workspaceId: String?,
    val workspaceName: String?,
    val conversations: List<Conversation>,
)

class HistoryVM(
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val workspaceRepo: WorkspaceRepository,
) : ViewModel() {

    /**
     * 全部会话按工作区分组。
     *
     * 会话所属工作区通过其 assistant 的 workspaceId 推断：
     * IDE 模式与 Agent 模式共用同一对话历史与工作区，因此同一工作区下
     * 所有 assistant 的会话会聚合在同一分组中。
     * 未绑定工作区的会话归入「未分配工作区」分组（置于列表末尾）。
     */
    val sections = combine(
        conversationRepo.getAllConversations(),
        settingsStore.settingsFlow,
        workspaceRepo.listFlow(),
    ) { conversations, settings, workspaces ->
        val workspaceById = workspaces.associate { it.id to it }
        val workspaceIdByAssistant = settings.assistants.associate { it.id to it.workspaceId?.toString() }

        conversations
            .groupBy { workspaceIdByAssistant[it.assistantId] }
            .map { (workspaceId, conversationList) ->
                WorkspaceSection(
                    workspaceId = workspaceId,
                    workspaceName = workspaceId?.let { workspaceById[it]?.name },
                    conversations = conversationList.sortedWith(
                        compareByDescending<Conversation> { it.isPinned }
                            .thenByDescending { it.updateAt }
                    ),
                )
            }
            .sortedWith(
                compareBy<WorkspaceSection> { it.workspaceId == null }
                    .thenByDescending { it.workspaceName ?: "" }
            )
    }.catch {
        Log.e(TAG, "Error: ${it.message}")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            conversationRepo.deleteAllConversations()
        }
    }

    fun togglePinStatus(conversationId: Uuid) {
        viewModelScope.launch {
            conversationRepo.togglePinStatus(conversationId)
        }
    }

    fun getPinnedConversations(): Flow<List<Conversation>> =
        conversationRepo.getPinnedConversations()

    fun restoreConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.insertConversation(conversation)
        }
    }

    suspend fun getFullConversation(conversationId: Uuid): Conversation? {
        return conversationRepo.getConversationById(conversationId)
    }
}
