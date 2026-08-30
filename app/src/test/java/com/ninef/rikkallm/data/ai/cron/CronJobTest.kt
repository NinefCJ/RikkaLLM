package com.ninef.rikkallm.data.ai.cron

import com.ninef.rikkallm.data.db.entity.CronJobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobTest {

    @Test
    fun `isDue is true only when enabled and reached next run`() {
        val base = CronJob(
            name = "t",
            cronExpr = "0 8 * * *",
            prompt = "p",
            assistantId = "",
            nextRunAtEpochMs = 1_000,
        )
        assertTrue(base.isDue(1_000))
        assertTrue(base.isDue(2_000))
        assertFalse(base.isDue(999))

        val disabled = base.copy(enabled = false)
        assertFalse(disabled.isDue(2_000))
    }

    @Test
    fun `entity round trip preserves fields`() {
        val job = CronJob(
            name = "今日看板",
            cronExpr = DEFAULT_BOARD_CRON,
            prompt = DEFAULT_BOARD_PROMPT,
            assistantId = "assistant-1",
            type = CronJobType.BOARD,
            nextRunAtEpochMs = 42,
            lastRunAtEpochMs = 1,
            lastStatus = CronJobStatus.SUCCESS,
            lastOutput = "ok",
            lastError = null,
        )
        val entity = CronJobEntity.from(job)
        val back = entity.toJob()
        assertEquals(job.jobId, back.jobId)
        assertEquals(job.name, back.name)
        assertEquals(job.cronExpr, back.cronExpr)
        assertEquals(job.prompt, back.prompt)
        assertEquals(job.assistantId, back.assistantId)
        assertEquals(job.type, back.type)
        assertEquals(job.enabled, back.enabled)
        assertEquals(job.nextRunAtEpochMs, back.nextRunAtEpochMs)
        assertEquals(job.lastRunAtEpochMs, back.lastRunAtEpochMs)
        assertEquals(job.lastStatus, back.lastStatus)
        assertEquals(job.lastOutput, back.lastOutput)
        assertEquals(job.lastError, back.lastError)
    }

    @Test
    fun `unknown type string falls back to CRON`() {
        val entity = CronJobEntity(
            jobId = "x",
            name = "n",
            cronExpr = "* * * * *",
            prompt = "p",
            assistantId = "",
            type = "UNKNOWN",
            enabled = true,
            nextRunAtEpochMs = 0,
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
        )
        assertEquals(CronJobType.CRON, entity.toJob().type)
    }

    @Test
    fun `board template is enabled and uses default schedule`() {
        val now = 1000L
        val entity = CronJobEntity.boardTemplate(now, "assistant-1")
        assertEquals(CronJobType.BOARD.name, entity.type)
        assertEquals(DEFAULT_BOARD_CRON, entity.cronExpr)
        assertTrue(entity.enabled)
        assertEquals(now, entity.nextRunAtEpochMs)
        assertEquals("assistant-1", entity.assistantId)
    }
}
