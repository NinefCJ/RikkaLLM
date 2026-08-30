package com.ninef.rikkallm.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.ai.cron.CronJobType
import com.ninef.rikkallm.data.ai.cron.CronScheduler
import com.ninef.rikkallm.data.ai.cron.CronJobExecutor
import com.ninef.rikkallm.data.ai.cron.DEFAULT_BOARD_CRON
import com.ninef.rikkallm.data.ai.cron.DEFAULT_BOARD_PROMPT
import com.ninef.rikkallm.data.db.entity.CronJobEntity

class CronJobVM(
    private val scheduler: CronScheduler,
    private val executor: CronJobExecutor,
) : ViewModel() {
    val jobs: StateFlow<List<CronJobEntity>> = scheduler.jobs
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busyJobIds = MutableStateFlow<Set<String>>(emptySet())
    val busyJobIds: StateFlow<Set<String>> = _busyJobIds

    fun createJob(
        name: String,
        cronExpr: String,
        prompt: String,
        assistantId: String,
        type: CronJobType = CronJobType.CRON,
        enabled: Boolean = true,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            scheduler.createJob(
                name = name,
                cronExpr = cronExpr,
                prompt = prompt,
                assistantId = assistantId,
                type = type,
                enabled = enabled,
            ).onFailure { onError(it.message ?: "创建失败") }
        }
    }

    fun updateJob(job: CronJobEntity) {
        viewModelScope.launch {
            scheduler.updateJob(job)
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            scheduler.deleteJob(jobId)
        }
    }

    fun setEnabled(jobId: String, enabled: Boolean) {
        viewModelScope.launch {
            scheduler.setEnabled(jobId, enabled)
        }
    }

    /** 立即手动执行（前台直接调用执行器，反馈即时） */
    fun runNow(job: CronJobEntity) {
        viewModelScope.launch {
            _busyJobIds.value = _busyJobIds.value + job.jobId
            try {
                executor.execute(job).also { output ->
                    scheduler.markFinished(
                        jobId = job.jobId,
                        success = true,
                        output = output,
                        error = null,
                    )
                }
            } catch (t: Throwable) {
                scheduler.markFinished(
                    jobId = job.jobId,
                    success = false,
                    output = null,
                    error = t.message ?: t.javaClass.simpleName,
                )
            } finally {
                _busyJobIds.value = _busyJobIds.value - job.jobId
            }
        }
    }

    /** 创建今日看板任务（若已存在 BOARD 任务则跳过） */
    fun ensureBoardJob() {
        viewModelScope.launch {
            if (scheduler.listAll().any { it.type == CronJobType.BOARD.name }) return@launch
            val assistantId = scheduler.listAll().firstOrNull()?.assistantId ?: ""
            scheduler.createJob(
                name = "今日看板",
                cronExpr = DEFAULT_BOARD_CRON,
                prompt = DEFAULT_BOARD_PROMPT,
                assistantId = assistantId,
                type = CronJobType.BOARD,
            )
        }
    }
}
