package com.ninef.rikkallm.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ninef.rikkallm.data.ai.rag.MemoryChunker
import com.ninef.rikkallm.data.db.dao.MemoryDAO
import com.ninef.rikkallm.data.db.dao.MemoryItemDAO
import com.ninef.rikkallm.data.db.entity.MemoryEntity
import com.ninef.rikkallm.data.db.entity.MemoryItemEntity
import com.ninef.rikkallm.data.model.AssistantMemory
import com.ninef.rikkallm.util.MemorySearchTimeRange

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val memoryItemDAO: MemoryItemDAO,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .map { AssistantMemory(it.id, it.content) }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(GLOBAL_MEMORY_ID)
            .map { AssistantMemory(it.id, it.content) }
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val newMemory = old.copy(
            content = content
        )
        memoryDAO.updateMemory(newMemory)
        return AssistantMemory(
            id = newMemory.id,
            content = newMemory.content,
        )
    }

    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
        val memory = AssistantMemory(
            id = 0,
            content = content,
        )
        val newMemory = memory.copy(
            id = memoryDAO.insertMemory(
                MemoryEntity(
                    assistantId = assistantId,
                    content = memory.content
                )
            ).toInt()
        )
        return newMemory
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
    }

    // ---- RAG 记忆（分块 + FTS 关键词检索 + 时间范围检索）----
    // 优雅降级：不依赖任何嵌入模型，仅用 SQLite FTS + 时间标签即可工作；
    // embedding_blob 预留给后续向量检索（本轮启用 FTS + 时间检索）。

    /** 把文本分块后写入记忆条目 */
    suspend fun addMemoryItems(assistantId: String, texts: List<String>) {
        val now = System.currentTimeMillis()
        texts.forEach { text ->
            MemoryChunker.chunkText(text).forEach { chunk ->
                memoryItemDAO.insertItem(
                    MemoryItemEntity(
                        assistantId = assistantId,
                        content = chunk,
                        createdAt = now,
                        lastAccessedAt = now,
                    )
                )
            }
        }
    }

    /** 检索记忆：空 query 返回最近片段；否则 FTS 关键词匹配，并按时间范围过滤 */
    suspend fun searchMemoryItems(
        assistantId: String,
        query: String?,
        timeRange: MemorySearchTimeRange?,
    ): List<MemoryItemEntity> {
        val start = timeRange?.startMillis ?: 0L
        val end = timeRange?.endMillis ?: Long.MAX_VALUE
        return if (query.isNullOrBlank()) {
            memoryItemDAO.getRecentItems(assistantId, 50)
        } else {
            memoryItemDAO.searchItems(assistantId, query, start, end)
        }
    }

    fun getMemoryItemsFlow(assistantId: String): Flow<List<MemoryItemEntity>> =
        memoryItemDAO.getItemsFlow(assistantId)

    suspend fun deleteMemoryItem(id: Int) = memoryItemDAO.deleteItem(id)

    suspend fun touchMemoryItem(id: Int) =
        memoryItemDAO.touchItem(id, System.currentTimeMillis())

    /** 巩固单个助手记忆：去重（相同内容保留最新）+ 超过上限时删除最久未访问的片段 */
    suspend fun consolidate(assistantId: String, keep: Int = 200) {
        val items = memoryItemDAO.getRecentItems(assistantId, Int.MAX_VALUE)
        computeConsolidationDeletions(items, keep).forEach { memoryItemDAO.deleteItem(it) }
    }

    /** 巩固全部助手的记忆（供后台 Worker 调用） */
    suspend fun consolidateAll(keepPerAssistant: Int = 200) {
        memoryItemDAO.getAllAssistantIds().forEach { consolidate(it, keepPerAssistant) }
    }
}

/**
 * 纯函数：计算需要删除的记忆条目 id，供 [MemoryRepository.consolidate] 与后台巩固 Worker 调用。
 * 规则：(1) 内容完全相同的重复片段只保留其一（删除其余）；
 * (2) 去重后若仍超过 [keep]，删除最久未访问的片段。
 * 内部暴露为 internal 便于单测，不含任何 DB 操作。
 */
internal fun computeConsolidationDeletions(items: List<MemoryItemEntity>, keep: Int): List<Int> {
    val seen = mutableSetOf<String>()
    val duplicates = items.filter { !seen.add(it.content) }.map { it.id }
    val remaining = items.filter { it.id !in duplicates }
    return if (remaining.size > keep) {
        duplicates + remaining
            .sortedBy { it.lastAccessedAt }
            .take(remaining.size - keep)
            .map { it.id }
    } else {
        duplicates
    }
}
