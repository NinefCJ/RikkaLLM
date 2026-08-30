package com.ninef.rikkallm.data.ai.generativeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeUiPromptTest {

    @Test
    fun `prompt contains fenced block markers`() {
        val prompt = GenerativeUiPrompt.build()
        assertTrue(prompt.contains(GenerativeUiPrompt.FENCE_START))
        assertTrue(prompt.contains(GenerativeUiPrompt.FENCE_END))
    }

    @Test
    fun `prompt enforces safe link schemes`() {
        val prompt = GenerativeUiPrompt.build()
        assertTrue(prompt.contains("https://"))
        assertTrue(prompt.contains("javascript:"))
    }

    @Test
    fun `prompt enforces length limits`() {
        val prompt = GenerativeUiPrompt.build()
        assertTrue(prompt.contains("120"))
        assertTrue(prompt.contains("500"))
        assertTrue(prompt.contains("12"))
    }

    @Test
    fun `prompt mentions plain text rendering and no fabrication`() {
        val prompt = GenerativeUiPrompt.build()
        assertTrue(prompt.contains("plain text"))
        assertTrue(prompt.contains("never fabricate"))
    }

    @Test
    fun `model name is embedded when provided`() {
        val prompt = GenerativeUiPrompt.build(modelName = "claude-3.5-sonnet")
        assertTrue(prompt.contains("claude-3.5-sonnet"))
    }

    @Test
    fun `model name omitted gracefully`() {
        val prompt = GenerativeUiPrompt.build(modelName = null)
        assertTrue(prompt.contains(GenerativeUiPrompt.FENCE_START))
        val prompt2 = GenerativeUiPrompt.build(modelName = "")
        assertEquals(prompt, prompt2)
    }

    @Test
    fun `prompt lists allowed kinds`() {
        val prompt = GenerativeUiPrompt.build()
        for (kind in listOf("search_result", "file_list", "data_list", "code", "status")) {
            assertTrue("prompt should mention kind '$kind'", prompt.contains(kind))
        }
    }
}
