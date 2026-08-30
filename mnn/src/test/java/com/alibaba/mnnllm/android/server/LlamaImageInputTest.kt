package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class LlamaImageInputTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private fun dataUri(bytes: ByteArray = pngBytes): String =
        "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `decodes a base64 data uri to a readable file uri`() {
        val uri = LlamaImageInput.resolve(dataUri(), tmp.root)!!
        assertTrue("expected a file: uri, got $uri", uri.startsWith("file:"))

        val file = File(java.net.URI(uri))
        assertTrue(file.isFile)
        assertEquals(pngBytes.toList(), file.readBytes().toList())
    }

    @Test
    fun `reuses the same file for identical image payloads`() {
        val first = LlamaImageInput.resolve(dataUri(), tmp.root)!!
        val second = LlamaImageInput.resolve(dataUri(), tmp.root)!!
        assertEquals(first, second)
        // One file only: content-hash naming prevents cache growth.
        assertEquals(1, File(tmp.root, "llm-images").listFiles()!!.size)
    }

    @Test
    fun `distinct payloads produce distinct files`() {
        val a = LlamaImageInput.resolve(dataUri(), tmp.root)!!
        val b = LlamaImageInput.resolve(dataUri(pngBytes + byteArrayOf(9)), tmp.root)!!
        assertTrue(a != b)
    }

    @Test
    fun `passes through uris the platform can already open`() {
        assertEquals("file:///sdcard/a.png", LlamaImageInput.resolve("file:///sdcard/a.png", tmp.root))
        assertEquals(
            "content://media/external/images/1",
            LlamaImageInput.resolve("content://media/external/images/1", tmp.root),
        )
    }

    @Test
    fun `adds a scheme to bare absolute paths`() {
        val file = tmp.newFile("shot.jpg").apply { writeBytes(pngBytes) }
        val uri = LlamaImageInput.resolve(file.absolutePath, tmp.root)!!
        assertTrue(uri.startsWith("file:"))
        assertEquals(file.absolutePath, File(java.net.URI(uri)).absolutePath)
    }

    @Test
    fun `rejects nonexistent absolute paths`() {
        assertNull(LlamaImageInput.resolve("/definitely/not/here.png", tmp.root))
    }

    @Test
    fun `rejects remote urls and unsupported payloads`() {
        // The engine is offline: downloading is the caller's job.
        assertNull(LlamaImageInput.resolve("https://example.com/a.png", tmp.root))
        assertNull(LlamaImageInput.resolve("http://example.com/a.png", tmp.root))
        assertNull(LlamaImageInput.resolve("", tmp.root))
        assertNull(LlamaImageInput.resolve("   ", tmp.root))
        assertNull(LlamaImageInput.resolve("data:image/png;base64,!!!not-base64!!!", tmp.root))
    }

    @Test
    fun `clearCache removes decoded images`() {
        LlamaImageInput.resolve(dataUri(), tmp.root)!!
        val dir = File(tmp.root, "llm-images")
        assertTrue(dir.isDirectory)

        LlamaImageInput.clearCache(tmp.root)
        assertTrue(!dir.exists())
    }
}
