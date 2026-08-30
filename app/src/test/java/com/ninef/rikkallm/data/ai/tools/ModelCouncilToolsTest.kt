package com.ninef.rikkallm.data.ai.tools

import kotlin.uuid.Uuid
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCouncilToolsTest {

    private fun chatModel(modelId: String, displayName: String) = Model(
        modelId = modelId,
        displayName = displayName,
        type = ModelType.CHAT,
    )

    @Test
    fun `resolveModel matches by modelId`() {
        val m = chatModel("gpt-4o", "GPT-4o")
        val all = listOf(m, chatModel("claude-3-5-sonnet", "Claude"))
        assertEquals(m, resolveModel("gpt-4o", all))
    }

    @Test
    fun `resolveModel matches by displayName case-insensitive`() {
        val m = chatModel("gpt-4o", "GPT-4o")
        val all = listOf(m)
        assertEquals(m, resolveModel("gpt-4o", all))
        assertEquals(m, resolveModel("GPT-4O", all))
    }

    @Test
    fun `resolveModel matches by uuid`() {
        val id = Uuid.random()
        val m = chatModel("gpt-4o", "GPT-4o").copy(id = id)
        val all = listOf(m)
        assertEquals(m, resolveModel(id.toString(), all))
    }

    @Test
    fun `resolveModel returns null for unknown token`() {
        val all = listOf(chatModel("gpt-4o", "GPT-4o"))
        assertNull(resolveModel("does-not-exist", all))
    }

    @Test
    fun `formatCouncilResult includes synthesis and per-model sections`() {
        val syn = chatModel("gpt-4o", "GPT-4o")
        val a = chatModel("claude-3-5-sonnet", "Claude")
        val b = chatModel("gemini-1.5-pro", "Gemini")
        val text = formatCouncilResult(
            prompt = "1+1=?",
            synthesis = "共识：答案是 2。分歧：无。",
            answers = listOf(CouncilSeat.Api(a) to "等于 2", CouncilSeat.Api(b) to "等于 2"),
            synthesisModel = syn,
        )
        assertTrue(text, text.contains("模型议会"))
        assertTrue(text, text.contains("共识：答案是 2"))
        assertTrue(text, text.contains("Claude"))
        assertTrue(text, text.contains("Gemini"))
        assertTrue(text, text.contains("1+1=?"))
    }
}
