package com.ninef.rikkallm.ui.pages.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ninef.rikkallm.data.ai.graph.ContextPreview
import com.ninef.rikkallm.data.graph.GraphOrchestrator
import com.ninef.rikkallm.data.graph.GraphStore
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import com.ninef.rikkallm.data.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * 可视化画布（M4）的 ViewModel：持有会话 DAG 的实时状态，并暴露节点拖拽、连线、修剪等边操作。
 *
 * 通过 [GraphOrchestrator] 接入高层语义（Branch/Prune/Merge/Rebuild/Inspect）；本 VM 负责
 * 把 UI 意图转交编排器，并维护从会话树到 DAG 的构建（空图时自动 [rebuild]）。
 */
class GraphCanvasVM(
    private val conversationId: Uuid,
    private val graphStore: GraphStore,
    private val conversationRepo: ConversationRepository,
    private val orchestrator: GraphOrchestrator,
) : ViewModel() {

    val graph: StateFlow<ConversationGraph> = graphStore
        .observeGraph(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConversationGraph.of(conversationId, emptyList(), emptyList()),
        )

    init {
        // 首屏若图谱为空，自动依据会话树构建 DAG，避免打开即空白。
        viewModelScope.launch {
            val existing = graphStore.observeGraph(conversationId).first()
            if (existing.nodes.isEmpty()) {
                rebuild()
            }
        }
    }

    /** 拖拽结束时更新节点坐标（持久化布局）。 */
    fun updateNodePosition(nodeId: Uuid, x: Float, y: Float) {
        val node = graph.value.nodes.firstOrNull { it.id == nodeId } ?: return
        viewModelScope.launch {
            graphStore.upsertNode(node.copy(x = x, y = y))
        }
    }

    /**
     * 在 [from] 与 [to] 之间建立一条上下文边（连线手势）。
     * 默认 [GraphEdgeType.CONTEXT_USER]：用户手动边，sync 不会自动增删且参与装配。
     * 经 [GraphStore.tryAddEdge] 做无环校验，返回是否成功（false=会造成环被拒）。
     */
    suspend fun connect(from: Uuid, to: Uuid, type: GraphEdgeType = GraphEdgeType.CONTEXT_USER): Boolean {
        val edge = GraphEdge(conversationId = conversationId, from = from, to = to, type = type)
        return graphStore.tryAddEdge(edge)
    }

    /** 移除一条边（Prune）：经编排器记录剪枝，使后续 sync 不补回该缺口。 */
    fun pruneEdge(edgeId: Uuid) {
        viewModelScope.launch { orchestrator.prune(conversationId, edgeId) }
    }

    /** 移除一个节点及其关联边（UI 暂未暴露，预留给后续 Merge 结果清理）。 */
    fun removeNode(nodeId: Uuid) {
        viewModelScope.launch { graphStore.removeNode(nodeId) }
    }

    /**
     * 依据当前会话重新构建 DAG（清空后按消息顺序重建链式 CONTEXT 边）。
     * 用户在顶栏触发"重建"时调用，亦用于首屏空图自动构建。
     */
    suspend fun rebuild() {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return
        orchestrator.rebuildFromConversation(conversation)
    }

    /**
     * 预览：以 [targetNodeId]（默认图中最后一个 MESSAGE 节点）为目标，经 [GraphOrchestrator.inspect]
     * 装配上下文并返回节点数 / 消息数 / 估算 token。选中节点时由页面调用，驱动 Inspect 浮层展示。
     */
    suspend fun inspect(targetNodeId: Uuid? = null): ContextPreview? {
        val conversation = conversationRepo.getConversationById(conversationId) ?: return null
        val graph = graphStore.observeGraph(conversationId).first()
        return orchestrator.inspect(conversation, graph, targetNodeId)
    }

    /**
     * Merge（汇聚分支）：将 [ids] 指定的多个节点汇入一个新 [com.ninef.rikkallm.data.graph.model.GraphNodeKind.MERGE]
     * 节点——为每个源节点建立一条指向新节点的 CONTEXT 入边，新节点落在选中质心右侧。
     * 选中该汇聚节点作为 Inspect 目标时，装配器按拓扑序收集各分支祖先的并集上下文。
     *
     * 少于 2 个节点时返回 null（无需汇聚）。
     */
    suspend fun mergeNodes(ids: List<Uuid>, label: String = "合并"): Uuid? {
        if (ids.size < 2) return null
        val g = graphStore.observeGraph(conversationId).first()
        val selected = g.nodes.filter { it.id in ids }
        if (selected.size < 2) return null
        val cx = selected.sumOf { it.x.toDouble() } / selected.size
        val cy = selected.sumOf { it.y.toDouble() } / selected.size
        return orchestrator.merge(
            conversationId = conversationId,
            sourceNodeIds = ids,
            sourceRef = Uuid.random().toString(),
            kind = GraphNodeKind.MERGE,
            label = label,
            x = (cx + 160.0).toFloat(),
            y = cy.toFloat(),
        )
    }
}
