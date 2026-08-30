package com.ninef.rikkallm.data.db.entity

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import com.ninef.rikkallm.data.ai.subagent.SubAgentConfig
import com.ninef.rikkallm.data.ai.subagent.SubAgentRun
import com.ninef.rikkallm.data.ai.subagent.SubAgentRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunEntityTest {

    private fun config() = SubAgentConfig(
        role = "researcher",
        task = "summarize the repo",
        systemPrompt = "Be concise",
        model = Model(),
        provider = ProviderSetting.OpenAI(),
    )

    private fun run(
        status: SubAgentRunStatus = SubAgentRunStatus.COMPLETED,
        output: String? = "the answer",
        error: String? = null,
    ) = SubAgentRun(
        runId = "sa_1",
        config = config(),
        status = status,
        output = output,
        error = error,
        createdAtEpochMs = 1000L,
        completedAtEpochMs = if (status == SubAgentRunStatus.RUNNING) null else 2500L,
    )

    @Test
    fun `entity round trip preserves run fields`() {
        val original = run()
        val entity = AgentRunEntity.from(original)
        val restored = entity.toRun()

        assertEquals(original.runId, restored.runId)
        assertEquals(original.status, restored.status)
        assertEquals(original.output, restored.output)
        assertEquals(original.error, restored.error)
        assertEquals(original.createdAtEpochMs, restored.createdAtEpochMs)
        assertEquals(original.completedAtEpochMs, restored.completedAtEpochMs)
        assertEquals(original.config.role, restored.config.role)
        assertEquals(original.config.task, restored.config.task)
        assertEquals(original.config.systemPrompt, restored.config.systemPrompt)
        assertEquals(original.config.maxOutputChars, restored.config.maxOutputChars)
    }

    @Test
    fun `config json embeds model and provider`() {
        val original = run()
        val entity = AgentRunEntity.from(original)
        assertTrue(entity.configJson.contains("\"researcher\""))
        assertTrue(entity.configJson.contains("\"summarize the repo\""))
        // 反序列化出的 model/provider 与原始一致（复用同一实例，避免随机 model id 干扰）
        val restored = entity.toRun()
        assertEquals(original.config.model, restored.config.model)
        assertEquals(original.config.provider, restored.config.provider)
    }

    @Test
    fun `malformed config json falls back without throwing`() {
        val entity = AgentRunEntity(
            runId = "sa_2",
            status = SubAgentRunStatus.FAILED.name,
            role = "researcher",
            task = "task",
            output = null,
            error = "boom",
            configJson = "{not valid json",
            createdAtEpochMs = 1L,
            completedAtEpochMs = 2L,
        )
        val restored = entity.toRun() // 不应抛异常
        assertEquals(SubAgentRunStatus.FAILED, restored.status)
        assertEquals("researcher", restored.config.role)
        assertEquals("boom", restored.error)
    }

    @Test
    fun `unknown status name falls back to failed`() {
        val entity = AgentRunEntity.from(run()).copy(status = "PENDING")
        assertEquals(SubAgentRunStatus.FAILED, entity.toRun().status)
    }

    @Test
    fun `running entity has no completion timestamp`() {
        val entity = AgentRunEntity.from(run(status = SubAgentRunStatus.RUNNING, output = null))
        assertEquals(SubAgentRunStatus.RUNNING, entity.toRun().status)
        assertNull(entity.completedAtEpochMs)
    }
}
