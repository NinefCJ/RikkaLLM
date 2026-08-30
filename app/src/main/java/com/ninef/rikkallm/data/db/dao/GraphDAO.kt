package com.ninef.rikkallm.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ninef.rikkallm.data.db.entity.GraphEdgeEntity
import com.ninef.rikkallm.data.db.entity.GraphNodeEntity
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface GraphDAO {
    @Query("SELECT * FROM graph_node WHERE conversation_id = :conversationId")
    fun getNodesFlow(conversationId: String): Flow<List<GraphNodeEntity>>

    @Query("SELECT * FROM graph_edge WHERE conversation_id = :conversationId")
    fun getEdgesFlow(conversationId: String): Flow<List<GraphEdgeEntity>>

    @Upsert
    suspend fun upsertNode(node: GraphNodeEntity)

    @Upsert
    suspend fun upsertEdge(edge: GraphEdgeEntity)

    @Upsert
    suspend fun upsertNodes(nodes: List<GraphNodeEntity>)

    @Upsert
    suspend fun upsertEdges(edges: List<GraphEdgeEntity>)

    @Query("DELETE FROM graph_edge WHERE id = :edgeId")
    suspend fun removeEdge(edgeId: String)

    @Query("DELETE FROM graph_node WHERE id = :nodeId")
    suspend fun removeNode(nodeId: String)

    @Query("DELETE FROM graph_node WHERE conversation_id = :conversationId")
    suspend fun deleteNodesByConversation(conversationId: String)

    @Query("DELETE FROM graph_edge WHERE conversation_id = :conversationId")
    suspend fun deleteEdgesByConversation(conversationId: String)
}

// ---- 实体 ↔ 领域模型映射 ----

fun GraphNode.toEntity(): GraphNodeEntity = GraphNodeEntity(
    id = id.toString(),
    conversationId = conversationId.toString(),
    kind = kind.name,
    sourceRef = sourceRef,
    label = label,
    state = state,
    x = x,
    y = y,
)

fun GraphNodeEntity.toDomain(): GraphNode = GraphNode(
    id = Uuid.parse(id),
    conversationId = Uuid.parse(conversationId),
    kind = runCatching { GraphNodeKind.valueOf(kind) }.getOrDefault(GraphNodeKind.MESSAGE),
    sourceRef = sourceRef,
    label = label,
    state = state,
    x = x,
    y = y,
)

fun GraphEdge.toEntity(): GraphEdgeEntity = GraphEdgeEntity(
    id = id.toString(),
    conversationId = conversationId.toString(),
    fromNode = from.toString(),
    toNode = to.toString(),
    type = type.name,
)

fun GraphEdgeEntity.toDomain(): GraphEdge = GraphEdge(
    id = Uuid.parse(id),
    conversationId = Uuid.parse(conversationId),
    from = Uuid.parse(fromNode),
    to = Uuid.parse(toNode),
    type = runCatching { GraphEdgeType.valueOf(type) }.getOrDefault(GraphEdgeType.CONTEXT),
)
