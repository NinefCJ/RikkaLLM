package com.ninef.rikkallm.data.repository

import com.ninef.rikkallm.data.db.entity.MemoryItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryConsolidationTest {

    private fun item(id: Int, content: String, lastAccessedAt: Long): MemoryItemEntity =
        MemoryItemEntity(
            id = id,
            assistantId = "a1",
            content = content,
            createdAt = lastAccessedAt,
            lastAccessedAt = lastAccessedAt,
        )

    @Test
    fun `no deletions when within keep limit and no duplicates`() {
        val items = listOf(
            item(1, "alpha", 100),
            item(2, "beta", 200),
            item(3, "gamma", 300),
        )
        assertEquals(emptyList<Int>(), computeConsolidationDeletions(items, 200))
    }

    @Test
    fun `duplicates removed regardless of keep limit`() {
        val items = listOf(
            item(1, "same", 100),
            item(2, "same", 200),
            item(3, "same", 300),
        )
        // 重复片段（id 2,3）全部删除，仅保留首次出现的 id 1
        assertEquals(listOf(2, 3), computeConsolidationDeletions(items, 200))
    }

    @Test
    fun `oldest accessed trimmed when over keep limit`() {
        val items = (1..5).map { item(it, "c$it", it * 10L) }
        // keep=3：去重后剩 5 条，删除最久未访问的 2 条（id 1,2）
        assertEquals(listOf(1, 2), computeConsolidationDeletions(items, 3))
    }

    @Test
    fun `duplicates plus overflow both removed`() {
        val items = listOf(
            item(1, "dup", 100),
            item(2, "dup", 110),
            item(3, "x", 120),
            item(4, "y", 130),
            item(5, "z", 140),
        )
        // keep=2：重复 id2 删除；去重后剩 4 条（含首个 dup=id1），
        // 按 lastAccessedAt 删最旧 2 条 = id1(100)、id3(120)
        assertEquals(listOf(2, 1, 3), computeConsolidationDeletions(items, 2))
    }

    @Test
    fun `keep of zero removes all non-duplicate items`() {
        val items = listOf(
            item(1, "a", 100),
            item(2, "b", 200),
        )
        // keep=0：去重后剩 2 条，全部（最久未访问优先）删除 => 删 id1,id2
        assertEquals(listOf(1, 2), computeConsolidationDeletions(items, 0))
    }
}
