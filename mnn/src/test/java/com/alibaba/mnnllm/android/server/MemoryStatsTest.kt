package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryStatsTest {

    @Test
    fun `VmRSS line is parsed in kib`() {
        assertEquals(2355200L, MemoryStats.parseVmRss("VmRSS:\t  2355200 kB"))
    }

    @Test
    fun `non VmRSS lines yield null`() {
        assertEquals(null, MemoryStats.parseVmRss("VmSize:\t  8000000 kB"))
    }

    @Test
    fun `malformed value yields null`() {
        assertEquals(null, MemoryStats.parseVmRss("VmRSS:\t  notanumber kB"))
    }

    @Test
    fun `spaces around value are tolerated`() {
        assertEquals(1024L, MemoryStats.parseVmRss("VmRSS: 1024 kB"))
    }
}
