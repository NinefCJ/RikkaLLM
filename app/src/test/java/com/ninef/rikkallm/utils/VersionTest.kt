package com.ninef.rikkallm.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `basic version comparison`() {
        assertTrue(Version("1.0.0") < Version("2.0.0"))
        assertTrue(Version("1.0.0") < Version("1.1.0"))
        assertTrue(Version("1.0.0") < Version("1.0.1"))
        assertEquals(0, Version("1.0.0").compareTo(Version("1.0.0")))
    }

    @Test
    fun `different length versions`() {
        assertEquals(0, Version("1.0").compareTo(Version("1.0.0")))
        assertTrue(Version("1.0") < Version("1.0.1"))
    }

    @Test
    fun `leading v or V is ignored`() {
        assertEquals(0, Version("v1.0.0").compareTo(Version("1.0.0")))
        assertEquals(0, Version("V1.2.3").compareTo(Version("v1.2.3")))
        assertEquals(0, Version("v2.0").compareTo(Version("2.0.0")))
    }

    @Test
    fun `non numeric segments fall back to zero`() {
        assertEquals(0, Version("1.0.0").compareTo(Version("1.0.x")))
        assertTrue(Version("1.0.0") < Version("1.0.1"))
    }

    @Test
    fun `compare to string`() {
        assertTrue(Version("2.0.0") > "1.0.0")
        assertTrue(Version("1.0.0") < "2.0.0")
        assertEquals(0, Version("1.0.0").compareTo("1.0.0"))
    }

    @Test
    fun `full precedence chain`() {
        val versions = listOf(
            Version("1.0.0"),
            Version("1.0.1"),
            Version("1.1.0"),
            Version("2.0.0"),
        )
        for (i in 0 until versions.size - 1) {
            assertTrue("${versions[i].value} should be < ${versions[i + 1].value}", versions[i] < versions[i + 1])
        }
    }
}
