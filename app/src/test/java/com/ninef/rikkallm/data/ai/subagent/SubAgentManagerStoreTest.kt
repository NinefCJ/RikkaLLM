package com.ninef.rikkallm.data.ai.subagent

import com.ninef.rikkallm.data.event.AppEvent
import com.ninef.rikkallm.data.event.AppEventBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 内存实现的 AgentRunStore，模拟 Room 语义（upsert 覆盖、倒序、limit） */
class FakeAgentRunStore : AgentRunStore {
    val runs = LinkedHashMap<String, SubAgentRun>()

    override suspend fun upsert(run: SubAgentRun) {
        runs[run.runId] = run
    }

    override suspend fun getById(runId: String): SubAgentRun? = runs[runId]

    override suspend fun listRecent(limit: Int): List<SubAgentRun> =
        runs.values.sortedByDescending { it.createdAtEpochMs }.take(limit.coerceAtLeast(1))

    override suspend fun count(): Int = runs.size
}

class SubAgentManagerStoreTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun config(task: String = "task") = SubAgentConfig(
        role = "researcher",
        task = task,
        model = Model(),
        provider = ProviderSetting.OpenAI(),
    )

    /** 轮询 store，直到 run 落盘为指定状态（upsert 在后台协程执行）。 */
    private suspend fun awaitStoreStatus(store: FakeAgentRunStore, runId: String, status: SubAgentRunStatus) {
        withTimeout(2000) {
            while (store.runs[runId]?.status != status) delay(10)
        }
    }

    @Test
    fun `running state is persisted before execution and completed after`() = runBlocking {
        val store = FakeAgentRunStore()
        // 用门闩让测试确定性地观察"执行开始前 RUNNING 已落盘"
        val executionStarted = CompletableDeferred<Unit>()
        val releaseExecution = CompletableDeferred<Unit>()
        val manager = SubAgentManager(
            executor = SubAgentExecutor {
                executionStarted.complete(Unit)
                releaseExecution.await()
                "the result"
            },
            appScope = scope,
            store = store,
        )
        val run = manager.launch(config(task = "persist me"))
        // 执行器开始运行前 SubAgentManager 已完成 RUNNING 落盘
        withTimeout(2000) { executionStarted.await() }
        assertEquals(SubAgentRunStatus.RUNNING, store.runs[run.runId]!!.status)
        releaseExecution.complete(Unit)

        awaitCompletion(manager, run.runId)
        awaitStoreStatus(store, run.runId, SubAgentRunStatus.COMPLETED)
        assertEquals("the result", store.runs[run.runId]!!.output)
        assertNotNull(store.runs[run.runId]!!.completedAtEpochMs)
    }

    @Test
    fun `failed run persists failed terminal state`() = runBlocking {
        val store = FakeAgentRunStore()
        val manager = SubAgentManager(
            executor = SubAgentExecutor { throw IllegalStateException("kaboom") },
            appScope = scope,
            store = store,
        )
        val runId = manager.launch(config()).runId
        awaitCompletion(manager, runId)
        awaitStoreStatus(store, runId, SubAgentRunStatus.FAILED)
        assertEquals(SubAgentRunStatus.FAILED, store.runs[runId]!!.status)
        assertTrue(store.runs[runId]!!.error.orEmpty().contains("kaboom"))
    }

    @Test
    fun `persisted history reads from store newest first`() = runBlocking {
        val store = FakeAgentRunStore()
        val manager = SubAgentManager(
            executor = SubAgentExecutor { delay(20); "r" },
            appScope = scope,
            store = store,
        )
        val first = manager.launch(config(task = "first")).runId
        delay(5) // 保证两次启动时间戳不同，历史排序稳定
        val second = manager.launch(config(task = "second")).runId
        awaitCompletion(manager, first)
        awaitCompletion(manager, second)
        awaitStoreStatus(store, first, SubAgentRunStatus.COMPLETED)
        awaitStoreStatus(store, second, SubAgentRunStatus.COMPLETED)

        val history = manager.persistedHistory(limit = 10)
        assertEquals(2, history.size)
        assertEquals(second, history[0].runId)
        assertEquals(first, history[1].runId)
    }

    @Test
    fun `persisted history falls back to memory without store`() = runBlocking {
        val manager = SubAgentManager(
            executor = SubAgentExecutor { "r" },
            appScope = scope,
        )
        manager.launch(config(task = "no store")).runId
        delay(100) // 等待后台完成进入内存态
        assertEquals(1, manager.persistedHistory(limit = 10).size)
    }

    @Test
    fun `persisted get by id falls back to memory on store miss`() = runBlocking {
        val store = FakeAgentRunStore()
        val manager = SubAgentManager(
            executor = SubAgentExecutor { "r" },
            appScope = scope,
            store = store,
        )
        val runId = manager.launch(config()).runId
        delay(100)
        // store 被清空模拟"进程重启前未持久化"的极端情况
        store.runs.clear()
        val restored = manager.persistedGetById(runId)
        assertNotNull(restored)
        assertEquals(runId, restored!!.runId)
    }

    @Test
    fun `store miss returns null`() = runBlocking {
        val store = FakeAgentRunStore()
        val manager = SubAgentManager(
            executor = SubAgentExecutor { "r" },
            appScope = scope,
            store = store,
        )
        assertNull(manager.persistedGetById("sa_missing"))
    }

    @Test
    fun `events emitted for running and terminal states`() = runBlocking {
        val bus = AppEventBus()
        val manager = SubAgentManager(
            executor = SubAgentExecutor { delay(30); "r" },
            appScope = scope,
            eventBus = bus,
        )
        val running = CompletableDeferred<AppEvent.SubAgentRunEvent>()
        val done = CompletableDeferred<AppEvent.SubAgentRunEvent>()
        val collector = launch {
            bus.events.collect { e ->
                if (e is AppEvent.SubAgentRunEvent) {
                    when (e.status) {
                        SubAgentRunStatus.RUNNING.name -> running.complete(e)
                        SubAgentRunStatus.COMPLETED.name -> done.complete(e)
                    }
                }
            }
        }
        // SharedFlow(replay=0) 不会重放历史事件：让收集器先完成订阅再启动 run。
        yield()
        val runId = manager.launch(config(task = "eventful")).runId

        val runningEvent = withTimeout(2000) { running.await() }
        assertEquals(runId, runningEvent.runId)
        assertEquals("researcher", runningEvent.role)
        assertEquals("eventful", runningEvent.taskPreview)

        val doneEvent = withTimeout(2000) { done.await() }
        assertEquals(runId, doneEvent.runId)
        assertTrue(doneEvent.durationMs != null)
        collector.cancel()
    }

    private suspend fun awaitCompletion(manager: SubAgentManager, runId: String): SubAgentRun {
        var run = manager.getRun(runId)!!
        while (run.status == SubAgentRunStatus.RUNNING) {
            delay(10)
            run = manager.getRun(runId)!!
        }
        return run
    }
}
