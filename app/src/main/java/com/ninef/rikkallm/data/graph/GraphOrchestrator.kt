package com.ninef.rikkallm.data.graph

import com.ninef.rikkallm.data.ai.graph.ContextAssembler
import com.ninef.rikkallm.data.ai.graph.ContextPreview
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import com.ninef.rikkallm.data.graph.model.toGraphNode
import com.ninef.rikkallm.data.model.Conversation
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * 图编排器（M3）：在图结构层实现 ThoughtDAG 四大操作 + 节点状态管理。
 *
 * 说明：本类聚焦于 **DAG 结构本身**（节点/边的增删、状态、上下文预览），
 * 不负责具体的消息体生成——消息内容由既有聊天/生成流程（[com.ninef.rikkallm.data.ai.GenerationHandler]）
 * 产生，再通过 [branch]/[merge] 把结果挂为图节点。这样与现有生成内核解耦，零侵入。
 *
 * 后续深化（P1/P2）：把 [com.ninef.rikkallm.data.ai.GenerationHandler] 的 `maxSteps`
 * 工具循环作为单步执行内核，由本编排器按拓扑依赖驱动多节点连续执行。
 */
class GraphOrchestrator(
    private val graphStore: GraphStore,
    private val contextAssembler: ContextAssembler,
) {
    /** 节点状态机（PENDING → READY → RUNNING → DONE / PRUNED）。 */
    enum class NodeState { PENDING, READY, RUNNING, DONE, PRUNED }

    /** Prune：移除一条边（不删节点数据），改变上下文构成。若是上下文边，则额外记录 [GraphEdgeType.PRUNED]，
     * 使后续 sync 在重建主干时不补回该缺口（保留用户剪枝意图）。 */
    suspend fun prune(conversationId: Uuid, edgeId: Uuid) {
        val edge = graphStore.observeGraph(conversationId).first()
            .edges.firstOrNull { it.id == edgeId } ?: return
        graphStore.removeEdge(edgeId)
        if (edge.type == GraphEdgeType.CONTEXT || edge.type == GraphEdgeType.CONTEXT_USER) {
            graphStore.upsertEdge(
                GraphEdge(
                    conversationId = edge.conversationId,
                    from = edge.from,
                    to = edge.to,
                    type = GraphEdgeType.PRUNED,
                ),
            )
        }
    }

    /** 在 [from] 与 [to] 间建立一条边（连线手势 / 依赖声明）。返回是否成功（环检测失败返回 false）。
     * 默认 [GraphEdgeType.CONTEXT_USER]：用户手动边，sync 不会自动增删，且参与上下文装配。 */
    suspend fun connect(
        conversationId: Uuid,
        from: Uuid,
        to: Uuid,
        type: GraphEdgeType = GraphEdgeType.CONTEXT_USER,
    ): Boolean = graphStore.tryAddEdge(
        GraphEdge(conversationId = conversationId, from = from, to = to, type = type)
    )

    /**
     * Branch：从 [parentNodeId] 派生一个新节点（例如用户发起重生成/新分支）。
     * [sourceRef] 由调用方提供（通常是新创建的 [com.ninef.rikkallm.data.model.MessageNode].id）。
     * 返回新建节点 id。
     */
    suspend fun branch(
        conversationId: Uuid,
        parentNodeId: Uuid,
        sourceRef: String,
        kind: GraphNodeKind = GraphNodeKind.MESSAGE,
        label: String = "",
    ): Uuid {
        val node = GraphNode(
            conversationId = conversationId,
            kind = kind,
            sourceRef = sourceRef,
            label = label,
            state = NodeState.PENDING.name,
        )
        graphStore.upsertNode(node)
        connect(conversationId, from = parentNodeId, to = node.id)
        return node.id
    }

    /**
     * Merge：将若干 [sourceNodeIds] 汇聚为一个新节点（选定策略由调用方决定内容合并，
     * 此处仅建立"多入边 → 新节点"的图结构）。
     *
     * [x]/[y] 为汇聚节点的画布坐标（由 UI 依选中质心计算）；[state] 默认 DONE，
     * 因为汇聚节点是路由点而非待执行节点。
     */
    suspend fun merge(
        conversationId: Uuid,
        sourceNodeIds: List<Uuid>,
        sourceRef: String,
        kind: GraphNodeKind = GraphNodeKind.MESSAGE,
        label: String = "",
        x: Float = 0f,
        y: Float = 0f,
        state: NodeState = NodeState.DONE,
    ): Uuid {
        val node = GraphNode(
            conversationId = conversationId,
            kind = kind,
            sourceRef = sourceRef,
            label = label,
            state = state.name,
            x = x,
            y = y,
        )
        graphStore.upsertNode(node)
        sourceNodeIds.forEach { from ->
            connect(conversationId, from = from, to = node.id)
        }
        return node.id
    }

    /** 更新节点状态（状态机推进）。 */
    suspend fun setState(conversationId: Uuid, nodeId: Uuid, state: NodeState) {
        val node = graphStore.observeGraph(conversationId).first()
            .nodes.firstOrNull { it.id == nodeId } ?: return
        graphStore.upsertNode(node.copy(state = state.name))
    }

    /** Inspect：基于装配结果生成上下文预览（不发起真实请求）。 */
    suspend fun inspect(
        conversation: Conversation,
        graph: ConversationGraph,
        targetNodeId: Uuid? = null,
    ): ContextPreview {
        val ctx = contextAssembler.assemble(conversation, graph, targetNodeId)
        return contextAssembler.preview(ctx)
    }

    /**
     * Sync（非破坏性）：把会话的 [Conversation.messageNodes] 与图谱对齐，使装配结果始终正确反映
     * 当前对话，同时**保留**用户在 UI 上做的连线 / 汇聚 / 剪枝编辑。
     *
     * 与 [rebuildFromConversation]（清空重建）不同，本方法只对齐增量，不抹除用户编辑。步骤：
     * 1. 孤立清理：移除 [GraphNodeKind.MESSAGE] 中 `sourceRef` 已不在会话里的节点（重新生成 / 删除消息导致）
     *    及其关联边，避免陈旧消息混入上下文。
     * 2. 确保每个当前 MessageNode 都有对应 MESSAGE 节点（刷新元数据；已存在则沿用原坐标）。
     * 3. 重建主干 CONTEXT 边：按会话顺序相邻连接；若某对节点已剪枝（存在 [GraphEdgeType.PRUNED]）则跳过。
     * 4. 清理过时主干边：图中类型为 CONTEXT、但不在新主干集合里的边（如重新生成遗留的旧链）予以移除。
     *    用户手动边（[GraphEdgeType.CONTEXT_USER]）与剪枝记录（[GraphEdgeType.PRUNED]）一律保留。
     */
    suspend fun syncFromConversation(conversation: Conversation) {
        val cid = conversation.id
        val msgs = conversation.messageNodes
        if (msgs.isEmpty()) return
        val convRefs = msgs.map { it.id.toString() }.toSet()

        val graph0 = graphStore.observeGraph(cid).first()

        // 1) 孤立清理
        val orphanNodeIds = graph0.nodes
            .filter { it.kind == GraphNodeKind.MESSAGE && it.sourceRef !in convRefs }
            .map { it.id }
            .toSet()
        graph0.edges
            .filter { it.from in orphanNodeIds || it.to in orphanNodeIds }
            .forEach { graphStore.removeEdge(it.id) }
        orphanNodeIds.forEach { graphStore.removeNode(it) }

        // 2) 确保当前消息都有 MESSAGE 节点
        val afterCleanup = graphStore.observeGraph(cid).first()
        val nodeByRef = afterCleanup.nodes
            .filter { it.kind == GraphNodeKind.MESSAGE }
            .associate { it.sourceRef to it.id }
        var layoutIndex = afterCleanup.nodes.count { it.kind == GraphNodeKind.MESSAGE }
        for (m in msgs) {
            val ref = m.id.toString()
            val existingNode = nodeByRef[ref]?.let { id -> afterCleanup.nodes.first { it.id == id } }
            val fresh = m.toGraphNode(cid)
            if (existingNode != null) {
                graphStore.upsertNode(existingNode.copy(label = fresh.label, kind = fresh.kind))
            } else {
                graphStore.upsertNode(fresh.copy(x = layoutX(layoutIndex), y = layoutY(layoutIndex)))
                layoutIndex++
            }
        }

        // 3) + 4) 重建主干并清理过时主干边
        val g = graphStore.observeGraph(cid).first()
        val refToNode = g.nodes.filter { it.kind == GraphNodeKind.MESSAGE }.associate { it.sourceRef to it.id }
        val prunedPairs = g.edges
            .filter { it.type == GraphEdgeType.PRUNED }
            .map { "${it.from}>${it.to}" }
            .toSet()
        val backbonePairs = mutableSetOf<String>()

        for (i in 1 until msgs.size) {
            val fromId = refToNode[msgs[i - 1].id.toString()] ?: continue
            val toId = refToNode[msgs[i].id.toString()] ?: continue
            val key = "$fromId>$toId"
            backbonePairs.add(key)
            if (key in prunedPairs) continue // 用户剪枝：不补主干边
            val exists = g.edges.any {
                it.type == GraphEdgeType.CONTEXT && it.from == fromId && it.to == toId
            }
            if (!exists) {
                graphStore.upsertEdge(
                    GraphEdge(conversationId = cid, from = fromId, to = toId, type = GraphEdgeType.CONTEXT),
                )
            }
        }

        g.edges
            .filter { it.type == GraphEdgeType.CONTEXT && "${it.from}>${it.to}" !in backbonePairs }
            .forEach { graphStore.removeEdge(it.id) }
    }

    /**
     * Rebuild：依据会话的 [Conversation.messageNodes]（扁平列表，列表顺序即线性对话）
     * 重建 DAG——每个 [com.ninef.rikkallm.data.model.MessageNode] 映射为一个 MESSAGE 节点，
     * 相邻节点间连 [GraphEdgeType.CONTEXT] 边形成一条链，并按网格布局铺开。
     *
     * 先清空旧图再重建，幂等。图谱由此呈现当前会话的上下文构成，
     * 用户可在 UI 上 prune / 重新连线以重塑上下文。
     */
    suspend fun rebuildFromConversation(conversation: Conversation) {
        val cid = conversation.id
        graphStore.clearGraph(cid)
        val nodes = conversation.messageNodes.mapIndexed { i, m ->
            m.toGraphNode(cid).copy(x = layoutX(i), y = layoutY(i))
        }
        nodes.forEach { graphStore.upsertNode(it) }
        for (i in 0 until nodes.lastIndex) {
            graphStore.upsertEdge(
                GraphEdge(
                    conversationId = cid,
                    from = nodes[i].id,
                    to = nodes[i + 1].id,
                    type = GraphEdgeType.CONTEXT,
                )
            )
        }
    }
}

/** 网格布局：按索引把节点铺成固定列数的阵列，避免全部堆叠在原点。 */
private const val LAYOUT_COLS = 4
private const val LAYOUT_DX = 200f
private const val LAYOUT_DY = 120f
private fun layoutX(i: Int) = (i % LAYOUT_COLS) * LAYOUT_DX
private fun layoutY(i: Int) = (i / LAYOUT_COLS) * LAYOUT_DY
