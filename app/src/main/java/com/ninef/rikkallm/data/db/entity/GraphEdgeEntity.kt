package com.ninef.rikkallm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "graph_edge",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class GraphEdgeEntity(
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("from_node")
    val fromNode: String,
    @ColumnInfo("to_node")
    val toNode: String,
    @ColumnInfo("type")
    val type: String,        // GraphEdgeType.name
)
