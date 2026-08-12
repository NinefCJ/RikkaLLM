package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/** 记忆条目的 FTS4 外部内容索引表，由 Room 触发器与 [MemoryItemEntity] 自动同步。 */
@Fts4(contentEntity = MemoryItemEntity::class)
@Entity(tableName = "memory_item_fts")
data class MemoryItemFtsEntity(
    @ColumnInfo("content") val content: String,
    @ColumnInfo("subject_tags") val subjectTags: String,
)
