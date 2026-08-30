package com.ninef.rikkallm.data.ai.subagent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SubAgentManagerTest {

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

    private fun manager(executor: SubAgentExecutor, maxConcurrent: Int = 2) =
        SubAgentManager(executor = executor, appScope = scope, maxConcurrentRuns = maxConcurrent)

    @Test
    fun `launch returns running run immediately`() {
        val manager = manager(SubAgentExecutor { delay(200); "result" })
        val run = manager.launch(config())
        assertEquals(SubAgentRunStatus.RUNNING, run.status)
        assertTrue(run.runId.startsWith("sa_"))
        assertNotNull(manager.getRun(run.runId))
    }

    @Test
    fun `run completes with output`() = runBlocking {
        val manager = manager(SubAgentExecutor { "done: ${it.task}" })
        val runId = manager.launch(config(task = "hello")).runId
        val completed = awaitCompletion(manager, runId)
        assertEquals(SubAgentRunStatus.COMPLETED, completed.status)
        assertEquals("done: hello", completed.output)
        assertTrue((completed.completedAtEpochMs ?: 0) >= completed.createdAtEpochMs)
    }

    @Test
    fun `failed executor marks run as failed`() = runBlocking {
        val manager = manager(SubAgentExecutor { throw IllegalStateException("boom") })
        val runId = manager.launch(config()).runId
        val completed = awaitCompletion(manager, runId)
        assertEquals(SubAgentRunStatus.FAILED, completed.status)
        assertTrue(completed.error.orEmpty().contains("boom"))
    }

    @Test
    fun `invalid config is rejected synchronously`() {
        val manager = manager(SubAgentExecutor { "ok" })
        try {
            manager.launch(config(task = ""))
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertEquals(0, manager.listRuns().size)
    }

    @Test
    fun `list runs is newest first`() = runBlocking {
        val manager = manager(SubAgentExecutor { delay(50); "r" })
        val first = manager.launch(config(task = "first")).runId
        val second = manager.launch(config(task = "second")).runId
        awaitCompletion(manager, first)
        awaitCompletion(manager, second)
        val runs = manager.listRuns()
        assertEquals(second, runs[0].runId)
        assertEquals(first, runs[1].runId)
    }

    @Test
    fun `concurrency limit queues extra runs`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val manager = manager(
            executor = SubAgentExecutor {
                val now = inFlight.incrementAndGet()
                maxSeen.updateAndGet { maxOf(it, now) }
                delay(100)
                inFlight.decrementAndGet()
                "r"
            },
            maxConcurrent = 1,
        )
        val ids = (1..3).map { manager.launch(config(task = "t$it")).runId }
        ids.forEach { awaitCompletion(manager, it) }
        assertEquals("with 1 permit, concurrency must never exceed 1", 1, maxSeen.get())
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
