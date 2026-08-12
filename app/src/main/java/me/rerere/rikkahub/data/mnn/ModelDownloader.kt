package me.rerere.rikkahub.data.mnn

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Streams one or more remote files to disk with per-file and overall progress reporting,
 * atomic final rename, and cooperative cancellation. Intentionally free of Android / Compose
 * dependencies so it can be unit-tested on the JVM with a MockWebServer.
 */
class ModelDownloader(private val client: OkHttpClient) {

    /**
     * @param files Ordered list of files to download. Each is written to [DownloadFileSpec.target]
     *   (a temp `.part` next to it is used during transfer, then renamed on success).
     * @param cancel Polled between read chunks; when it returns true the current transfer aborts
     *   and [DownloadResult.Canceled] is returned.
     * @param onProgress Emitted (throttled) with cumulative progress for UI binding.
     */
    suspend fun download(
        files: List<DownloadFileSpec>,
        cancel: () -> Boolean = { false },
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadResult {
        if (files.isEmpty()) return DownloadResult.Success

        val overallTotal = files.sumOf { it.sizeHint }.coerceAtLeast(0)
        var overallDownloaded = 0L
        var lastEmit = 0L

        for ((index, spec) in files.withIndex()) {
            if (cancel()) return DownloadResult.Canceled
            val temp = File(spec.target.path + PART_SUFFIX)
            temp.parentFile?.mkdirs()

            val request = Request.Builder().url(spec.url).build()
            val call = client.newCall(request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        temp.delete()
                        return DownloadResult.Error("HTTP ${response.code} · ${spec.url}")
                    }
                    val body = response.body ?: run {
                        temp.delete()
                        return DownloadResult.Error("Empty response body · ${spec.url}")
                    }
                    val fileTotal = response.header("Content-Length")?.toLongOrNull() ?: spec.sizeHint

                    try {
                        body.byteStream().use { input ->
                            FileOutputStream(temp).use { out ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var read: Int
                                var fileDownloaded = 0L
                                val start = System.currentTimeMillis()
                                while (true) {
                                    if (cancel()) throw CancellationRequested
                                    read = input.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                    fileDownloaded += read
                                    overallDownloaded += read
                                    val now = System.currentTimeMillis()
                                    val elapsedMs = (now - start).coerceAtLeast(1)
                                    if (now - lastEmit >= EMIT_INTERVAL_MS || fileDownloaded == fileTotal) {
                                        lastEmit = now
                                        onProgress(
                                            DownloadProgress(
                                                fileIndex = index,
                                                fileCount = files.size,
                                                fileName = spec.target.name,
                                                fileBytes = fileDownloaded,
                                                fileTotal = fileTotal,
                                                overallBytes = overallDownloaded,
                                                overallTotal = overallTotal,
                                                speedBytesPerSec = fileDownloaded * 1000 / elapsedMs,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationRequested) {
                        temp.delete()
                        return DownloadResult.Canceled
                    } catch (e: IOException) {
                        temp.delete()
                        return DownloadResult.Error(e.message ?: "I/O error · ${spec.url}")
                    }

                    // Atomic-ish finalise: prefer rename, fall back to a copy.
                    if (!temp.renameTo(spec.target)) {
                        temp.copyTo(spec.target, overwrite = true)
                        temp.delete()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationRequested) return DownloadResult.Canceled
                temp.delete()
                return DownloadResult.Error(e.message ?: e.toString())
            }
        }
        return DownloadResult.Success
    }

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val BUFFER_SIZE = 64 * 1024
        private const val EMIT_INTERVAL_MS = 80L

        private object CancellationRequested : IOException("canceled")
    }
}

/** A single file to fetch. */
data class DownloadFileSpec(
    val url: String,
    val target: File,
    val sizeHint: Long = 0L,
)

/** Throttled progress snapshot for one download operation. */
data class DownloadProgress(
    val fileIndex: Int,
    val fileCount: Int,
    val fileName: String,
    val fileBytes: Long,
    val fileTotal: Long,
    val overallBytes: Long,
    val overallTotal: Long,
    val speedBytesPerSec: Long,
)

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Error(val message: String) : DownloadResult
    data object Canceled : DownloadResult
}
