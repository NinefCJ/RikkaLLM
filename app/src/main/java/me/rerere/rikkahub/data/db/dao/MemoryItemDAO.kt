package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryItemEntity

@Dao
interface MemoryItemDAO {
    @Insert
    suspend fun insertItem(item: MemoryItemEntity): Long

    @Query(
        "SELECT * FROM memory_item " +
            "WHERE assistant_id = :assistantId AND status = 0 " +
            "ORDER BY last_accessed_at DESC, created_at DESC"
    )
    fun getItemsFlow(assistantId: String): Flow<List<MemoryItemEntity>>

    @Query(
        "SELECT m.* FROM memory_item m " +
            "WHERE m.id IN (SELECT docid FROM memory_item_fts WHERE memory_item_fts MATCH :query) " +
            "AND m.assistant_id = :assistantId AND m.status = 0 " +
            "AND m.created_at BETWEEN :startMillis AND :endMillis " +
            "ORDER BY m.created_at DESC"
    )
    suspend fun searchItems(
        assistantId: String,
        query: String,
        startMillis: Long,
        endMillis: Long,
    ): List<MemoryItemEntity>

    @Query(
        "SELECT * FROM memory_item " +
            "WHERE assistant_id = :assistantId AND status = 0 " +
            "ORDER BY created_at DESC LIMIT :limit"
    )
    suspend fun getRecentItems(assistantId: String, limit: Int): List<MemoryItemEntity>

    @Query("SELECT DISTINCT assistant_id FROM memory_item")
    suspend fun getAllAssistantIds(): List<String>

    @Query("DELETE FROM memory_item WHERE id = :id")
    suspend fun deleteItem(id: Int)

    @Query("UPDATE memory_item SET last_accessed_at = :ts WHERE id = :id")
    suspend fun touchItem(id: Int, ts: Long)
}
