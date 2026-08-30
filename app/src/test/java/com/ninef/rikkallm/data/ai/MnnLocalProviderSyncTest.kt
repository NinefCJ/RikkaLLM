package com.ninef.rikkallm.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MnnLocalProviderSyncTest {

    @Test
    fun `managed flag with loopback base url is resettable`() {
        assertTrue(mnnEntryIsSyncManaged("http://127.0.0.1:8090/v1", managedFlag = true))
        assertTrue(mnnEntryIsSyncManaged("http://127.0.0.1:8080/v1", managedFlag = true))
    }

    @Test
    fun `missing marker protects manual entries on cold start`() {
        // Phase 1 style manual entry pointing at a standalone MNN Chat: no marker
        // was ever written, so a stop cleanup must leave it untouched.
        assertFalse(mnnEntryIsSyncManaged("http://127.0.0.1:8080/v1", managedFlag = false))
        assertFalse(mnnEntryIsSyncManaged("", managedFlag = false))
    }

    @Test
    fun `marker alone is not enough when the endpoint was changed`() {
        // User edited the entry to point somewhere else after the bridge wrote it:
        // the loopback sanity check prevents clobbering the new endpoint.
        assertFalse(mnnEntryIsSyncManaged("https://my-gateway.example.com/v1", managedFlag = true))
        assertFalse(mnnEntryIsSyncManaged("http://192.168.1.10:8080/v1", managedFlag = true))
    }
}
