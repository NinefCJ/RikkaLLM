package com.ninef.rikkallm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * RAG 记忆条目（对话分块后的单条记忆）。
 * 与 [MemoryItemFtsEntity] 配合实现关键词检索；embedding_blob 预留给后续向量检索（本轮暂不启用）。
 */
@Entity(
    tableName = "memory_item",
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["status"]),
        Index(value = ["event_at"]),
    ]
)
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("content") val content: String,
    @ColumnInfo("subject_tags") val subjectTags: String = "[]",
    @ColumnInfo("importance") val importance: Int = 3,
    @ColumnInfo("status") val status: Int = Status.ACTIVE,
    @ColumnInfo("event_at") val eventAt: Long? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("last_accessed_at") val lastAccessedAt: Long,
    @ColumnInfo("embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo("embedding_model_id") val embeddingModelId: String? = null,
) {
    object Status {
        const val ACTIVE = 0
        const val SUPERSEDED = 1
        const val HIDDEN = 2
    }
}
