package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for [GgufHeaderReader] (magic detection + KV metadata) and [ModelDiscovery]
 * (GGUF / MNN / HuggingFace layout recognition, shard selection).
 *
 * A minimal GGUF header is synthesised in-memory so no model file is required.
 */
class ModelDiscoveryTest {

    companion object {
        /** Builds a minimal valid GGUF header (no tensors) with the given string/uint32 KV pairs. */
        private fun buildGguf(vararg kv: Pair<String, Any>): ByteArray {
            val bb = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("GGUF".toByteArray(Charsets.US_ASCII))
            bb.putInt(3) // version
            bb.putLong(0) // tensor count
            bb.putLong(kv.size.toLong()) // metadata kv count
            for ((k, v) in kv) {
                val kb = k.toByteArray(Charsets.UTF_8)
                bb.putLong(kb.size.toLong())
                bb.put(kb)
                when (v) {
                    is String -> {
                        bb.putInt(8) // TYPE_STRING
                        val vb = v.toByteArray(Charsets.UTF_8)
                        bb.putLong(vb.size.toLong())
                        bb.put(vb)
                    }
                    is Int -> {
                        bb.putInt(4) // TYPE_UINT32
                        bb.putInt(v)
                    }
                    else -> error("unsupported test value type: ${v::class}")
                }
            }
            bb.flip()
            val out = ByteArray(bb.remaining())
            bb.get(out)
            return out
        }

        private fun writeGguf(dir: File, name: String, vararg kv: Pair<String, Any>): File {
            val f = File(dir, name)
            f.writeBytes(buildGguf(*kv))
            return f
        }
    }

    // ---- GgufHeaderReader ----

    @Test
    fun `isGguf rejects non-magic files`() {
        val dir = File.createTempFile("noguf", "").apply { delete() }.also { it.mkdirs() }
        val f = File(dir, "weights.bin").apply { writeText("not a gguf file at all") }
        assertFalse(GgufHeaderReader.isGguf(f))
        assertFalse(GgufHeaderReader.isGguf(File(dir, "missing.gguf")))
        f.deleteRecursively()
    }

    @Test
    fun `reads architecture context length name and file type`() {
        val dir = File.createTempFile("gguf", "").apply { delete() }.also { it.mkdirs() }
        val f = writeGguf(
            dir, "model.gguf",
            "general.architecture" to "llama",
            "llama.context_length" to 8192,
            "general.name" to "Test LLM",
            "general.file_type" to 15,
        )
        val meta = GgufHeaderReader.readHeader(f)
        assertNotNull(meta)
        assertEquals(3, meta!!.version)
        assertEquals("llama", meta.architecture)
        assertEquals(8192L, meta.contextLength)
        assertEquals("Test LLM", meta.name)
        assertEquals(15L, meta.fileType)
        f.deleteRecursively()
    }

    @Test
    fun `returns null for non-gguf bytes`() {
        val dir = File.createTempFile("bad", "").apply { delete() }.also { it.mkdirs() }
        val f = File(dir, "x.gguf").apply { writeText("XXXX not magic") }
        assertNull(GgufHeaderReader.readHeader(f))
        f.deleteRecursively()
    }

    // ---- ModelDiscovery ----

    @Test
    fun `detects a single GGUF model`() {
        val dir = File.createTempFile("mmod", "").apply { delete() }.also { it.mkdirs() }
        val main = writeGguf(dir, "model.gguf", "general.name" to "Solo", "llama.context_length" to 4096)
        val layout = ModelDiscovery.discover(dir)
        assertEquals(ModelFormat.GGUF, layout.format)
        assertEquals(main, layout.mainWeights)
        assertNull(layout.mmproj)
        assertEquals("Solo", layout.metadata.name)
        assertEquals(4096L, layout.metadata.contextLength)
        assertFalse(layout.metadata.isMultimodal)
        dir.deleteRecursively()
    }

    @Test
    fun `detects GGUF with mmproj as multimodal`() {
        val dir = File.createTempFile("mmpro", "").apply { delete() }.also { it.mkdirs() }
        writeGguf(dir, "model.gguf", "general.name" to "Vision")
        val proj = writeGguf(dir, "mmproj.gguf")
        val layout = ModelDiscovery.discover(dir)
        assertEquals(ModelFormat.GGUF, layout.format)
        assertEquals(proj, layout.mmproj)
        assertTrue(layout.metadata.isMultimodal)
        dir.deleteRecursively()
    }

    @Test
    fun `picks the first shard of a split GGUF`() {
        val dir = File.createTempFile("shard", "").apply { delete() }.also { it.mkdirs() }
        writeGguf(dir, "model-00002-of-00002.gguf", "general.name" to "Split")
        val first = writeGguf(dir, "model-00001-of-00002.gguf", "general.name" to "Split")
        val layout = ModelDiscovery.discover(dir)
        assertEquals(first, layout.mainWeights)
        dir.deleteRecursively()
    }

    @Test
    fun `detects MNN layout`() {
        val dir = File.createTempFile("mnn", "").apply { delete() }.also { it.mkdirs() }
        File(dir, "config.json").writeText("""{"model_name":"MyMNN"}""")
        File(dir, "model.mnn").writeText("weights")
        val layout = ModelDiscovery.discover(dir)
        assertEquals(ModelFormat.MNN, layout.format)
        assertEquals("MyMNN", layout.metadata.name)
        dir.deleteRecursively()
    }

    @Test
    fun `recognises HuggingFace safetensors layout`() {
        val dir = File.createTempFile("hfmt", "").apply { delete() }.also { it.mkdirs() }
        File(dir, "config.json").writeText("""{"model_name":"HFModel"}""")
        File(dir, "model.safetensors").writeText("{}")
        File(dir, "tokenizer.json").writeText("{}")
        val layout = ModelDiscovery.discover(dir)
        assertEquals(ModelFormat.HUGGINGFACE, layout.format)
        dir.deleteRecursively()
    }

    @Test
    fun `unknown layout when nothing matches`() {
        val dir = File.createTempFile("unk", "").apply { delete() }.also { it.mkdirs() }
        File(dir, "readme.txt").writeText("no model here")
        assertEquals(ModelFormat.UNKNOWN, ModelDiscovery.discover(dir).format)
        dir.deleteRecursively()
    }
}
