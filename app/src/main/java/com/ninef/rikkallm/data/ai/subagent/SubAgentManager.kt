package com.ninef.rikkallm.data.ai.subagent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.ninef.rikkallm.data.event.AppEvent
import com.ninef.rikkallm.data.event.AppEventBus

/**
 * 子智能体管理器。
 *
 * 职责：
 * - [launch] 校验配置后立即返回 RUNNING 态 run，并后台异步执行
 * - 并发闸：超过 [maxConcurrentRuns] 时新 run 排队等待
 * - 超时 / 失败标记：执行异常或超时被记录到 run 状态
 * - 持久化（Agent Store）：注入 [store] 时，RUNNING 态与终态均 best-effort 落盘，
 *   支持进程重启后的历史恢复与审计；事件经 [eventBus] 广播 [SubAgentRunEvent]
 * - 内存态保留最近 [MAX_SUBAGENT_RUNS] 条记录，供 subagent_list / subagent_result 查询
 */
class SubAgentManager(
    private val executor: SubAgentExecutor,
    private val appScope: CoroutineScope,
    private val maxConcurrentRuns: Int = DEFAULT_SUBAGENT_MAX_CONCURRENT_RUNS,
    private val store: AgentRunStore? = null,
    private val eventBus: AppEventBus? = null,
) {
    private val semaphore = Semaphore(maxConcurrentRuns)
    private val runIdCounter = AtomicInteger(0)
    private val timeSource: () -> Long = { System.currentTimeMillis() }

    private val runs = object : LinkedHashMap<String, SubAgentRun>(MAX_SUBAGENT_RUNS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SubAgentRun>?): Boolean =
            size > MAX_SUBAGENT_RUNS
    }

    /**
     * 校验并启动一个子智能体，立即返回 RUNNING 状态的 [SubAgentRun]。
     * 校验失败同步抛 [IllegalArgumentException]。
     */
    fun launch(config: SubAgentConfig): SubAgentRun {
        val validated = SubAgentValidator.validate(config)
        val runId = "sa_${timeSource()}_${runIdCounter.incrementAndGet()}"
        val run = SubAgentRun(
            runId = runId,
            config = validated,
            status = SubAgentRunStatus.RUNNING,
            createdAtEpochMs = timeSource(),
        )
        synchronized(runs) { runs[runId] = run }
        emitEvent(run)

        appScope.launch {
            // 先落盘 RUNNING 态；持久化失败仅降级为内存态，不影响执行
            runCatching { store?.upsert(run) }

            try {
                semaphore.withPermit {
                    val output = executor.execute(validated)
                    finish(runId, SubAgentRunStatus.COMPLETED, output = output)
                }
            } catch (e: TimeoutCancellationException) {
                finish(runId, SubAgentRunStatus.TIMEOUT, error = "Sub-agent timed out: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(runId, SubAgentRunStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
            }
        }
        return run
    }

    fun getRun(runId: String): SubAgentRun? = synchronized(runs) { runs[runId] }

    /** 按启动时间倒序返回最近 [limit] 条运行记录 */
    fun listRuns(limit: Int = 10): List<SubAgentRun> = synchronized(runs) {
        runs.values.toList().reversed().take(limit.coerceIn(1, MAX_SUBAGENT_RUNS))
    }

    /** 运行中（含排队中）的 run 数 */
    fun activeRunCount(): Int = synchronized(runs) {
        runs.values.count { it.status == SubAgentRunStatus.RUNNING }
    }

    /**
     * 从持久层读取历史记录（进程重启后可恢复），按启动时间倒序。
     * 未注入 store 或持久层不可用时回退到内存态。
     */
    suspend fun persistedHistory(limit: Int = 20): List<SubAgentRun> {
        val store = store ?: return listRuns(limit)
        return runCatching { store.listRecent(limit.coerceAtLeast(1)) }
            .getOrDefault(listRuns(limit))
    }

    /** 优先从持久层读取单条记录，未找到时回退内存态。 */
    suspend fun persistedGetById(runId: String): SubAgentRun? {
        val persisted = store?.let { runCatching { it.getById(runId) }.getOrNull() }
        return persisted ?: getRun(runId)
    }

    private suspend fun finish(runId: String, status: SubAgentRunStatus, output: String? = null, error: String? = null) {
        val updated = synchronized(runs) {
            val current = runs[runId] ?: return
            current.copy(
                status = status,
                output = output,
                error = error,
                completedAtEpochMs = timeSource(),
            ).also { runs[runId] = it }
        }
        // 终态落盘（best-effort）并广播状态事件
        runCatching { store?.upsert(updated) }
        emitEvent(updated)
    }

    private fun emitEvent(run: SubAgentRun) {
        eventBus?.tryEmit(
            AppEvent.SubAgentRunEvent(
                runId = run.runId,
                status = run.status.name,
                role = run.config.role,
                taskPreview = run.config.task.take(120),
                durationMs = run.completedAtEpochMs?.minus(run.createdAtEpochMs),
            )
        )
    }
}
