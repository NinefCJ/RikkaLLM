package com.ninef.rikkallm.data.graph

import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * [GraphStore] 的内存实现，供 JVM 单测使用（无需 Room / Android）。
 * 行为与原 [GraphStoreImpl] 一致：写入边时做无环校验、按会话清空等。
 */
class FakeGraphStore : GraphStore {
    private val nodes = mutableMapOf<Uuid, GraphNode>()
    private val edges = mutableMapOf<Uuid, GraphEdge>()
    private val graphs = MutableStateFlow<Map<Uuid, ConversationGraph>>(emptyMap())

    private fun sync(cid: Uuid) {
        val g = ConversationGraph.of(
            conversationId = cid,
            nodes = nodes.values.filter { it.conversationId == cid },
            edges = edges.values.filter { it.conversationId == cid },
        )
        graphs.value = graphs.value + (cid to g)
    }

    suspend fun snapshot(conversationId: Uuid): ConversationGraph =
        observeGraph(conversationId).first()

    override fun observeGraph(conversationId: Uuid): Flow<ConversationGraph> =
        graphs.map { it[conversationId] ?: ConversationGraph.of(conversationId, emptyList(), emptyList()) }

    override suspend fun upsertNode(node: GraphNode) {
        nodes[node.id] = node
        sync(node.conversationId)
    }

    override suspend fun upsertEdge(edge: GraphEdge) {
        edges[edge.id] = edge
        sync(edge.conversationId)
    }

    override suspend fun removeEdge(edgeId: Uuid) {
        edges.remove(edgeId)?.let { sync(it.conversationId) }
    }

    override suspend fun removeNode(nodeId: Uuid) {
        nodes.remove(nodeId)?.let { sync(it.conversationId) }
    }

    override suspend fun clearGraph(conversationId: Uuid) {
        nodes.values.removeIf { it.conversationId == conversationId }
        edges.values.removeIf { it.conversationId == conversationId }
        sync(conversationId)
    }

    override suspend fun tryAddEdge(edge: GraphEdge): Boolean {
        val current = observeGraph(edge.conversationId).first()
        val next = current.withEdge(edge) ?: return false
        edges[edge.id] = edge
        sync(edge.conversationId)
        return true
    }
}
