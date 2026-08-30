package com.ninef.rikkallm.data.ai.cron

import com.ninef.rikkallm.data.db.dao.CronJobDAO
import com.ninef.rikkallm.data.db.entity.CronJobEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cron 任务调度器：负责任务 CRUD、下次触发时间计算与到期扫描。
 *
 * 执行动作本身由 [CronJobExecutor] 承担，调度器只负责"谁该跑、什么时候跑"。
 */
class CronScheduler(
    private val dao: CronJobDAO,
) {
    val jobs: Flow<List<CronJobEntity>> = dao.listFlow()

    /** 创建任务；nextRunAt 由表达式自动计算 */
    suspend fun createJob(
        name: String,
        cronExpr: String,
        prompt: String,
        assistantId: String,
        type: CronJobType = CronJobType.CRON,
        enabled: Boolean = true,
    ): Result<CronJobEntity> = runCatching {
        require(CronParser.isValidExpression(cronExpr)) { "cron 表达式非法" }
        val now = System.currentTimeMillis()
        val nextRun = CronParser(cronExpr).nextRunAfter(now)
            ?: error("cron 表达式在有效期内无触发时间")
        val job = CronJob(
            name = name,
            cronExpr = cronExpr,
            prompt = prompt,
            assistantId = assistantId,
            type = type,
            enabled = enabled,
            nextRunAtEpochMs = nextRun,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val entity = CronJobEntity.from(job)
        dao.upsert(entity)
        entity
    }

    suspend fun updateJob(job: CronJobEntity): Result<CronJobEntity> = runCatching {
        val now = System.currentTimeMillis()
        val updated = job.copy(updatedAtEpochMs = now)
        dao.upsert(updated)
        updated
    }

    suspend fun deleteJob(jobId: String) {
        dao.deleteById(jobId)
    }

    suspend fun setEnabled(jobId: String, enabled: Boolean) {
        dao.setEnabled(jobId, enabled, System.currentTimeMillis())
    }

    suspend fun getJob(jobId: String): CronJobEntity? = dao.getById(jobId)

    suspend fun listAll(): List<CronJobEntity> = dao.listAll()

    /** 手动立即执行（将 nextRunAt 置为当前时间，交由执行器拾取） */
    suspend fun triggerNow(jobId: String) {
        dao.updateNextRun(jobId, System.currentTimeMillis(), System.currentTimeMillis())
    }

    /** 扫描到期的启用任务（供执行器/Worker 调用） */
    suspend fun listDueJobs(nowEpochMs: Long): List<CronJobEntity> = dao.listDue(nowEpochMs)

    /** 标记任务运行状态与下次触发时间 */
    suspend fun markStarted(jobId: String, nowEpochMs: Long = System.currentTimeMillis()) {
        val job = dao.getById(jobId) ?: return
        dao.upsert(
            job.copy(
                lastStatus = CronJobStatus.RUNNING.name,
                lastRunAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    suspend fun markFinished(
        jobId: String,
        success: Boolean,
        output: String?,
        error: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val job = dao.getById(jobId) ?: return
        val nextRun = runCatching { CronParser(job.cronExpr).nextRunAfter(nowEpochMs) }.getOrNull()
        dao.upsert(
            job.copy(
                lastStatus = if (success) CronJobStatus.SUCCESS.name else CronJobStatus.FAILED.name,
                lastOutput = if (success) output else job.lastOutput,
                lastError = error,
                nextRunAtEpochMs = nextRun ?: job.nextRunAtEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }
}
