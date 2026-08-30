package com.ninef.rikkallm.data.graph

import com.ninef.rikkallm.data.db.dao.GraphDAO
import com.ninef.rikkallm.data.db.dao.toDomain
import com.ninef.rikkallm.data.db.dao.toEntity
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * 图持久化仓库接口：把 [GraphDAO] 的实体流聚合成内存 [ConversationGraph]，
 * 并在写入边时做无环校验（拒绝会造成环的边）。
 *
 * 定义为接口以便单元测试用内存双实现（[GraphStoreImpl] / 测试 Fake）替换。
 */
interface GraphStore {
    /** 观测某会话的实时 DAG（节点流 + 边流合并为 [ConversationGraph]）。 */
    fun observeGraph(conversationId: Uuid): Flow<ConversationGraph>

    suspend fun upsertNode(node: GraphNode)
    suspend fun upsertEdge(edge: GraphEdge)
    suspend fun removeEdge(edgeId: Uuid)
    suspend fun removeNode(nodeId: Uuid)

    /** 清空某会话的全部节点与边（重建前调用）。 */
    suspend fun clearGraph(conversationId: Uuid)

    /**
     * 新增一条边：先在当前图基础上做无环校验，通过才落库。
     * @return true 成功；false 表示会造成环被拒绝。
     */
    suspend fun tryAddEdge(edge: GraphEdge): Boolean
}

/**
 * [GraphStore] 的 Room 实现。
 */
class GraphStoreImpl(private val graphDAO: GraphDAO) : GraphStore {

    override fun observeGraph(conversationId: Uuid): Flow<ConversationGraph> =
        combine(
            graphDAO.getNodesFlow(conversationId.toString()),
            graphDAO.getEdgesFlow(conversationId.toString()),
        ) { nodeEntities, edgeEntities ->
            ConversationGraph.of(
                conversationId = conversationId,
                nodes = nodeEntities.map { it.toDomain() },
                edges = edgeEntities.map { it.toDomain() },
            )
        }

    override suspend fun upsertNode(node: GraphNode) = graphDAO.upsertNode(node.toEntity())

    override suspend fun upsertEdge(edge: GraphEdge) = graphDAO.upsertEdge(edge.toEntity())

    override suspend fun removeEdge(edgeId: Uuid) = graphDAO.removeEdge(edgeId.toString())

    override suspend fun removeNode(nodeId: Uuid) = graphDAO.removeNode(nodeId.toString())

    override suspend fun clearGraph(conversationId: Uuid) {
        graphDAO.deleteEdgesByConversation(conversationId.toString())
        graphDAO.deleteNodesByConversation(conversationId.toString())
    }

    override suspend fun tryAddEdge(edge: GraphEdge): Boolean {
        val current = observeGraph(edge.conversationId).first()
        val next = current.withEdge(edge) ?: return false
        graphDAO.upsertEdge(edge.toEntity())
        return true
    }
}
