package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelRegistryTest {

    private fun tempModelsRoot(): File =
        File.createTempFile("mnnmodels", "").also { it.delete(); it.mkdirs() }

    private fun writeModel(dir: File, name: String?, configBody: String = """{"model_name":"$name"}"""): File {
        dir.mkdirs()
        File(dir, "config.json").writeText(configBody)
        File(dir, "weights.bin").writeText("x".repeat(1024))
        return dir
    }

    @Test
    fun `lists direct child model dirs`() {
        val root = tempModelsRoot()
        writeModel(File(root, "qwen"), "Qwen2.5")
        writeModel(File(root, "llama"), "Llama3")
        val models = LocalModelRegistry.list(root)
        assertEquals(2, models.size)
        val names = models.map { it.name }
        assertTrue(names.contains("Qwen2.5"))
        assertTrue(names.contains("Llama3"))
    }

    @Test
    fun `lists one-level-nested model dirs`() {
        val root = tempModelsRoot()
        // builtin/<name>/config.json layout
        writeModel(File(root, "builtin/qwen"), "Qwen2.5")
        val models = LocalModelRegistry.list(root)
        assertEquals(1, models.size)
        assertEquals("Qwen2.5", models[0].name)
        assertTrue(models[0].id.startsWith("local/"))
        assertTrue(models[0].dir.absolutePath.endsWith("qwen"))
    }

    @Test
    fun `ignores directories without config json`() {
        val root = tempModelsRoot()
        File(root, "empty").mkdirs()
        File(File(root, "empty"), "notes.txt").writeText("hi")
        assertEquals(0, LocalModelRegistry.list(root).size)
    }

    @Test
    fun `reports size as sum of files`() {
        val root = tempModelsRoot()
        writeModel(File(root, "m"), "M") // weights.bin is 1024 bytes (+ config.json)
        val model = LocalModelRegistry.list(root).first()
        // sizeBytes includes every file under the model dir (weights + config.json).
        assertTrue("expected at least the 1024-byte weights, got ${model.sizeBytes}", model.sizeBytes >= 1024L)
    }

    @Test
    fun `falls back to directory name when model_name absent`() {
        val root = tempModelsRoot()
        val dir = writeModel(File(root, "fallback"), null, """{"backend_type":"cpu"}""")
        val model = LocalModelRegistry.list(root).first()
        assertEquals("fallback", model.name)
    }

    @Test
    fun `version marker is written and read`() {
        val root = tempModelsRoot()
        val dir = writeModel(File(root, "m"), "M")
        LocalModelRegistry.writeVersion(dir, "1.2.3")
        val model = LocalModelRegistry.list(root).first()
        assertEquals("1.2.3", model.version)
        assertTrue(model.installedAt != null && model.installedAt > 0L)
    }

    @Test
    fun `manual model has null version`() {
        val root = tempModelsRoot()
        writeModel(File(root, "m"), "M")
        val model = LocalModelRegistry.list(root).first()
        assertNull(model.version)
        assertNull(model.installedAt)
    }
}
