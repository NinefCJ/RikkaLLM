package com.alibaba.mnnllm.android.server

import java.io.BufferedReader
import java.io.FileReader

/**
 * Best-effort resident-memory readout for the inference telemetry exposed through the
 * OpenAI `usage` extension.
 *
 * Memory is sampled from `/proc/self/status` (`VmRSS`), i.e. the process resident set
 * size at the moment of the call. For an on-device LLM the process footprint is
 * dominated by the model weights and the growing KV cache, so the post-generation RSS
 * is a good proxy for the peak memory used during inference — no native changes needed.
 */
object MemoryStats {

    /**
     * Resident set size in KiB, or `null` when the platform does not expose
     * `/proc/self/status` (e.g. a non-Linux JVM during unit tests).
     */
    fun residentMemoryKb(): Long? {
        return try {
            BufferedReader(FileReader("/proc/self/status")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val value = parseVmRss(line!!) ?: continue
                    return value
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Parses a `VmRSS: nnn kB` line, returning KiB or `null` if it doesn't match. */
    internal fun parseVmRss(line: String): Long? {
        if (!line.startsWith("VmRSS:")) return null
        val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
        // Expected shape: ["VmRSS:", "<value>", "kB"]
        val value = tokens.getOrNull(1)?.toLongOrNull() ?: return null
        return value
    }
}
