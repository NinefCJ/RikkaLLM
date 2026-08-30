package com.ninef.rikkallm.data.ai.cron

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.ninef.rikkallm.data.db.entity.CronJobEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Cron 调度 Worker。
 *
 * WorkManager 周期性唤醒（[CRON_WORK_INTERVAL_MINUTES]，默认 15 分钟），
 * 扫描所有到期的启用任务并受限并发执行。App 存活期间由
 * [CronScheduler.triggerNow] 提供精确的分钟级前台调度作为补充。
 */
class CronExecutionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val scheduler: CronScheduler by inject()
    private val executor: CronJobExecutor by inject()

    override suspend fun doWork(): Result {
        val due = scheduler.listDueJobs(System.currentTimeMillis())
        if (due.isEmpty()) return Result.success()

        // 受限并发执行，防止高峰期打爆模型接口
        val semaphore = Semaphore(CRON_MAX_CONCURRENT)
        coroutineScope {
            due.forEach { job ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        runJob(executor, scheduler, job)
                    }
                }
            }
        }
        return Result.success()
    }

    private companion object {
        const val CRON_MAX_CONCURRENT = 2

        suspend fun runJob(
            executor: CronJobExecutor,
            scheduler: CronScheduler,
            job: CronJobEntity,
        ) {
            scheduler.markStarted(job.jobId)
            try {
                val output = executor.execute(job)
                scheduler.markFinished(
                    jobId = job.jobId,
                    success = true,
                    output = output,
                    error = null,
                )
            } catch (t: Throwable) {
                scheduler.markFinished(
                    jobId = job.jobId,
                    success = false,
                    output = null,
                    error = t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }
}

/** WorkManager 注册入口：唯一周期任务，应用启动时调用一次 */
object CronJobWorkScheduler {
    const val CRON_WORK_INTERVAL_MINUTES: Long = 15

    fun schedule(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            CronExecutionWorker::class.java,
            CRON_WORK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CRON_WORK_UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

const val CRON_WORK_UNIQUE_NAME = "cron_execution_worker"
