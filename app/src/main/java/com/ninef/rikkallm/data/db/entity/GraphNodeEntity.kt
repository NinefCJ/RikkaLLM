package com.ninef.rikkallm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "graph_node",
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
data class GraphNodeEntity(
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("kind")
    val kind: String,        // GraphNodeKind.name
    @ColumnInfo("source_ref")
    val sourceRef: String,  // 指向 MessageNode.id / 来源片段 / Tool id
    @ColumnInfo("label")
    val label: String,
    @ColumnInfo("state")
    val state: String,
    @ColumnInfo("x")
    val x: Float,
    @ColumnInfo("y")
    val y: Float,
)
