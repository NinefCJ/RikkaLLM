package com.ninef.rikkallm.data.mnn

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID

class ModelDownloaderTest {

    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var dir: File

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
        client = OkHttpClient()
        dir = File(System.getProperty("java.io.tmpdir"), "mnn-dltest-${UUID.randomUUID()}")
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun serve(path: String, body: ByteArray, code: Int = 200) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(code, if (code == 200) body.size.toLong() else -1)
            if (code == 200) {
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun target(name: String) = File(dir, name)

    @Test
    fun `download writes file content and reports success`() = runBlocking {
        val body = "hello mnn model".toByteArray()
        serve("/model.bin", body)
        val spec = DownloadFileSpec(url("/model.bin"), target("model.bin"))

        val result = ModelDownloader(client).download(listOf(spec)) {}

        assertEquals(DownloadResult.Success, result)
        assertEquals("hello mnn model", spec.target.readText())
        // The temp .part file must be gone after a successful, atomic rename.
        assertFalse(File(spec.target.path + ".part").exists())
    }

    @Test
    fun `progress reports content length and cumulative bytes`() = runBlocking {
        val body = ByteArray(16 * 1024) { it.toByte() } // 16 KiB
        serve("/w.bin", body)
        val spec = DownloadFileSpec(url("/w.bin"), target("w.bin"))

        var last: DownloadProgress? = null
        ModelDownloader(client).download(listOf(spec)) { last = it }

        assertEquals(1, last?.fileCount)
        // Per-file total comes from the server's Content-Length header.
        assertEquals(body.size.toLong(), last?.fileTotal)
        // overallBytes tracks actual bytes streamed; overallTotal is the catalog size-hint
        // sum (0 here, since no hint was supplied), so we assert on the bytes, not the hint.
        assertEquals(body.size.toLong(), last?.overallBytes)
        assertEquals(0L, last?.overallTotal)
    }

    @Test
    fun `download surfaces HTTP errors`() = runBlocking {
        serve("/missing", ByteArray(0), code = 404)
        val spec = DownloadFileSpec(url("/missing"), target("missing.bin"))

        val result = ModelDownloader(client).download(listOf(spec)) {}

        assertTrue(result is DownloadResult.Error)
        assertFalse(spec.target.exists())
    }

    @Test
    fun `download honours cancellation`() = runBlocking {
        val big = ByteArray(2 * 1024 * 1024) // 2 MiB, forces multiple read chunks
        serve("/big.bin", big)
        val spec = DownloadFileSpec(url("/big.bin"), target("big.bin"))

        var cancel = false
        val result = ModelDownloader(client).download(
            files = listOf(spec),
            cancel = { cancel },
            onProgress = { cancel = true },
        )

        assertEquals(DownloadResult.Canceled, result)
        // Cancelled transfer must not finalise the target file.
        assertFalse(spec.target.exists())
    }

    @Test
    fun `empty file list succeeds immediately`() = runBlocking {
        val result = ModelDownloader(client).download(emptyList()) {}
        assertEquals(DownloadResult.Success, result)
    }
}
