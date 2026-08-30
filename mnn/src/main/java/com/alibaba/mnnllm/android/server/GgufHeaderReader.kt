package com.alibaba.mnnllm.android.server

import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal, allocation-light reader for the GGUF file header.
 *
 * GGUF is the universal container used by llama.cpp and is the de-facto on-device
 * LLM format (Llama, Qwen, DeepSeek, Gemma, Mistral, Phi, ...). We only need a
 * handful of scalar/string metadata values to (a) recognise the format by its
 * magic bytes instead of relying on the file extension and (b) let the engine
 * auto-configure itself (context length, model family, multimodal hints).
 *
 * Reference: https://github.com/ggml-org/ggml/blob/master/docs/gguf.md
 *
 * The whole header fits in the first few MB, so we read at most [MAX_HEADER_BYTES]
 * and never touch the tensor data — this keeps discovery cheap and pure-JVM so it
 * can run in unit tests without a model file.
 */
object GgufHeaderReader {

    private val MAGIC = "GGUF".toByteArray(Charsets.US_ASCII)
    private const val MAX_HEADER_BYTES = 8 * 1024 * 1024

    // GGUF metadata value types (subset we care about).
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9

    data class Meta(
        val version: Int = 0,
        val architecture: String? = null,
        val contextLength: Long? = null,
        val name: String? = null,
        val fileType: Long? = null,
        val chatTemplate: String? = null,
    )

    /** True when [file]'s first four bytes are the GGUF magic (read-only, safe on any file). */
    fun isGguf(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(4)
                raf.read(head) == 4 && head.contentEquals(MAGIC)
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** Parses the GGUF header of [file], or null when it is not a GGUF file / unreadable. */
    fun readHeader(file: File): Meta? {
        if (!isGguf(file)) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = minOf(raf.length(), MAX_HEADER_BYTES.toLong()).toInt()
                val buf = ByteArray(len)
                raf.readFully(buf)
                parse(buf)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Parses an in-memory GGUF header (exposed for tests). */
    internal fun parse(bytes: ByteArray): Meta {
        val r = Reader(bytes)
        val magic = r.readBytes(4)
        if (!magic.contentEquals(MAGIC)) return Meta()
        val version = r.readU32().toInt()
        r.readU64() // tensor_count — not needed for discovery
        val kvCount = r.readU64()
        var architecture: String? = null
        var contextLength: Long? = null
        var name: String? = null
        var fileType: Long? = null
        var chatTemplate: String? = null
        repeat(kvCount.toInt().coerceAtMost(Int.MAX_VALUE)) {
            val key = r.readString()
            val type = r.readU32().toInt()
            when {
                key == "general.architecture" && type == TYPE_STRING ->
                    architecture = r.readString()

                key == "general.name" && type == TYPE_STRING ->
                    name = r.readString()

                key == "general.file_type" && type == TYPE_UINT32 ->
                    fileType = r.readU32()

                key == "tokenizer.chat_template" && type == TYPE_STRING ->
                    chatTemplate = r.readString()

                key.endsWith(".context_length") && type == TYPE_UINT32 ->
                    contextLength = r.readU32()

                else -> r.skipValue(type)
            }
        }
        return Meta(version, architecture, contextLength, name, fileType, chatTemplate)
    }

    private class Reader(val b: ByteArray) {
        var pos = 0

        fun readBytes(n: Int): ByteArray {
            val a = b.copyOfRange(pos, pos + n)
            pos += n
            return a
        }

        fun readU8(): Int = b[pos++].toInt() and 0xFF

        fun readU16(): Int {
            val v = (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8)
            pos += 2
            return v
        }

        fun readU32(): Long {
            var v = 0L
            for (i in 0..3) v = v or ((b[pos++].toLong() and 0xFF) shl (8 * i))
            return v and 0xFFFFFFFFL
        }

        fun readU64(): Long {
            var v = 0L
            for (i in 0..7) v = v or ((b[pos++].toLong() and 0xFF) shl (8 * i))
            return v
        }

        fun readString(): String {
            val len = readU64().toInt()
            return String(readBytes(len), Charsets.UTF_8)
        }

        /** Consumes a value of [type] without retaining it (advances the cursor). */
        fun skipValue(type: Int) {
            when (type) {
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> pos += 1
                TYPE_UINT16, TYPE_INT16 -> pos += 2
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> pos += 4
                TYPE_STRING -> {
                    val len = readU64().toInt()
                    pos += len
                }
                TYPE_ARRAY -> {
                    val elemType = readU32().toInt()
                    val count = readU64().toInt()
                    repeat(count) { skipValue(elemType) }
                }
                else -> { /* unknown type: stop scanning defensively */ pos = b.size }
            }
        }
    }
}
