package com.ninef.rikkallm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ninef.rikkallm.data.ai.cron.CronJob
import com.ninef.rikkallm.data.ai.cron.CronJobStatus
import com.ninef.rikkallm.data.ai.cron.CronJobType
import com.ninef.rikkallm.data.ai.cron.DEFAULT_BOARD_CRON
import com.ninef.rikkallm.data.ai.cron.DEFAULT_BOARD_PROMPT
import kotlin.uuid.Uuid

/**
 * Cron 定时任务实体。
 *
 * 支持两类任务：
 * - [CronJobType.CRON]：通用无人值守任务，按 cron 表达式触发。
 * - [CronJobType.BOARD]：今日看板（每天 08:00 自动生成）。
 */
@Entity(
    tableName = "cron_jobs",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["type"]),
        Index(value = ["next_run_at"]),
    ],
)
data class CronJobEntity(
    @PrimaryKey
    @ColumnInfo("job_id")
    val jobId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("cron_expr")
    val cronExpr: String,
    @ColumnInfo("prompt")
    val prompt: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("type")
    val type: String,
    @ColumnInfo("enabled")
    val enabled: Boolean,
    @ColumnInfo("next_run_at")
    val nextRunAtEpochMs: Long,
    @ColumnInfo("last_run_at")
    val lastRunAtEpochMs: Long? = null,
    @ColumnInfo("last_status")
    val lastStatus: String = CronJobStatus.PENDING.name,
    @ColumnInfo("last_output")
    val lastOutput: String? = null,
    @ColumnInfo("last_error")
    val lastError: String? = null,
    @ColumnInfo("created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo("updated_at")
    val updatedAtEpochMs: Long,
) {
    fun toJob(): CronJob = CronJob(
        jobId = jobId,
        name = name,
        cronExpr = cronExpr,
        prompt = prompt,
        assistantId = assistantId,
        type = runCatching { CronJobType.valueOf(type) }.getOrDefault(CronJobType.CRON),
        enabled = enabled,
        nextRunAtEpochMs = nextRunAtEpochMs,
        lastRunAtEpochMs = lastRunAtEpochMs,
        lastStatus = runCatching { CronJobStatus.valueOf(lastStatus) }
            .getOrDefault(CronJobStatus.PENDING),
        lastOutput = lastOutput,
        lastError = lastError,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun from(job: CronJob): CronJobEntity = CronJobEntity(
            jobId = job.jobId,
            name = job.name,
            cronExpr = job.cronExpr,
            prompt = job.prompt,
            assistantId = job.assistantId,
            type = job.type.name,
            enabled = job.enabled,
            nextRunAtEpochMs = job.nextRunAtEpochMs,
            lastRunAtEpochMs = job.lastRunAtEpochMs,
            lastStatus = job.lastStatus.name,
            lastOutput = job.lastOutput,
            lastError = job.lastError,
            createdAtEpochMs = job.createdAtEpochMs,
            updatedAtEpochMs = job.updatedAtEpochMs,
        )

        /** 预置今日看板任务模板 */
        fun boardTemplate(nowEpochMs: Long, assistantId: String): CronJobEntity = CronJobEntity(
            jobId = Uuid.random().toString(),
            name = "今日看板",
            cronExpr = DEFAULT_BOARD_CRON,
            prompt = DEFAULT_BOARD_PROMPT,
            assistantId = assistantId,
            type = CronJobType.BOARD.name,
            enabled = true,
            nextRunAtEpochMs = nowEpochMs,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }
}
