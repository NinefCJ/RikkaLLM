package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineStatsTest {

    @Test
    fun `prefill and decode tps are derived from ms timing`() {
        val s = GenerationStats(promptTokens = 43, completionTokens = 479, prefillMs = 1330L, decodeMs = 43460L)
        // 43 / 1.330 ~= 32.330...
        assertEquals(43.0 * 1000.0 / 1330.0, s.prefillTokensPerSecond!!, 1e-3)
        // 479 / 43.460 ~= 11.022...
        assertEquals(479.0 * 1000.0 / 43460.0, s.decodeTokensPerSecond!!, 1e-3)
    }

    @Test
    fun `zero timing yields null tps to avoid div by zero`() {
        val s = GenerationStats(promptTokens = 10, completionTokens = 10, prefillMs = 0L, decodeMs = 0L)
        assertNull(s.prefillTokensPerSecond)
        assertNull(s.decodeTokensPerSecond)
    }
}