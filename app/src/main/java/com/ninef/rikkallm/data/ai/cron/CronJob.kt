package com.ninef.rikkallm.data.ai.cron

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Cron 任务类型：通用无人值守 / 今日看板 */
@Serializable
enum class CronJobType {
    CRON,
    BOARD,
}

/** Cron 任务运行状态 */
@Serializable
enum class CronJobStatus {
    /** 尚未运行 */
    PENDING,

    /** 运行中 */
    RUNNING,

    /** 成功完成 */
    SUCCESS,

    /** 失败（含异常/超时） */
    FAILED,
}

/** 今日看板默认触发表达式：每天 08:00 */
const val DEFAULT_BOARD_CRON: String = "0 8 * * *"

/** 看板任务内置 prompt 模板 */
const val DEFAULT_BOARD_PROMPT: String =
    "生成今日看板。请基于当前时间给出以下内容：\n" +
        "1. 今日日期与星期\n" +
        "2. 一条今日工作/学习建议\n" +
        "3. 一条效率技巧或提醒\n" +
        "要求：输出 3-5 行纯文本，结构清晰，使用 - 列表。"

/** Cron 任务领域模型 */
data class CronJob(
    val jobId: String = Uuid.random().toString(),
    val name: String,
    val cronExpr: String,
    val prompt: String,
    val assistantId: String,
    val type: CronJobType = CronJobType.CRON,
    val enabled: Boolean = true,
    val nextRunAtEpochMs: Long,
    val lastRunAtEpochMs: Long? = null,
    val lastStatus: CronJobStatus = CronJobStatus.PENDING,
    val lastOutput: String? = null,
    val lastError: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    /** 是否已到执行时间（用于调度器扫描） */
    fun isDue(nowEpochMs: Long): Boolean = enabled && nextRunAtEpochMs <= nowEpochMs
}
