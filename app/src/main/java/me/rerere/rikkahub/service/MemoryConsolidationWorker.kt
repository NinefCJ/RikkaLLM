package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 后台周期性巩固 RAG 长期记忆：去重 + 裁剪最久未访问的片段，避免无限增长。
 *
 * 仅使用 SQLite FTS + 时间标签，不依赖任何嵌入模型（向量检索为后续增强），故可优雅降级。
 * 移植自 LastChat 的 MemoryConsolidationWorker.kt（保留其「定期合并冗余记忆」部分，
 * 舍弃强依赖 EmbeddingService / VectorEngine / 情景记忆的子系统的向量相关逻辑）。
 */
class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val memoryRepository: MemoryRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            memoryRepository.consolidateAll()
            Result.success()
        }.onFailure {
            Log.e(TAG, "Failed to consolidate memories", it)
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val TAG = "MemoryConsolidation"
        const val UNIQUE_NAME = "memory_consolidation"
    }
}
