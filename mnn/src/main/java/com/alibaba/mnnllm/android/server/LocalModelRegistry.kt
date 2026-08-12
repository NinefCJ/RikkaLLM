package com.alibaba.mnnllm.android.server

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File

/**
 * Enumerates locally installed MNN models and their on-disk version metadata.
 *
 * This is the local half of Phase 3 "model download / version management": it scans a
 * models root directory (typically `AppContext.filesDir/.mnnmodels`), recognises every
 * sub-directory that carries a `config.json`, and reports a stable [InstalledModel]
 * entry with human-readable name, size, and version/install timestamp. The remote half
 * (a catalog of downloadable models + a downloader) lives in the app module
 * (`me.rerere.rikkahub.data.mnn`), keeping this class portable and unit-testable
 * without an Android context or network access.
 */
object LocalModelRegistry {

    private val gson = Gson()

    /**
     * A model discovered on disk.
     *
     * @param id model id in the engine's scheme — `local/<absoluteDir>` so it can be
     *           passed straight to [com.alibaba.mnnllm.android.llm.LlmSession].
     * @param name display name (from config.json `model_name`, else the directory name).
     * @param dir absolute directory holding `config.json`.
     * @param sizeBytes total size of the model directory (weights + assets).
     * @param version semantic version string recorded by the downloader, or null if the
     *                model was added manually (no `.mnnversion` marker).
     * @param installedAt epoch millis when the version marker was written, or null.
     */
    data class InstalledModel(
        val id: String,
        val name: String,
        val dir: File,
        val sizeBytes: Long,
        val version: String?,
        val installedAt: Long?,
    )

    /** Marker file written next to `config.json` by downloads to record the version. */
    const val VERSION_FILE = ".mnnversion"

    /**
     * Lists every model found under [root]. A directory qualifies when it (or one level
     * down) contains a `config.json`, matching both the `builtin/<name>/config.json`
     * layout and the `local/<dir>/config.json` layout.
     */
    fun list(root: File): List<InstalledModel> {
        if (!root.isDirectory) return emptyList()
        val models = mutableListOf<InstalledModel>()
        root.listFiles()?.forEach { top ->
            if (!top.isDirectory) return@forEach
            val config = File(top, "config.json")
            if (config.isFile) {
                models.add(readModel(top, config))
                return@forEach
            }
            // One level deeper (e.g. builtin/<name>/config.json).
            top.listFiles()?.forEach { nested ->
                if (!nested.isDirectory) return@forEach
                val nestedConfig = File(nested, "config.json")
                if (nestedConfig.isFile) models.add(readModel(nested, nestedConfig))
            }
        }
        return models.sortedBy { it.name.lowercase() }
    }

    private fun readModel(dir: File, config: File): InstalledModel {
        val name = readModelName(config) ?: dir.name
        val (version, installedAt) = readVersion(File(dir, VERSION_FILE))
        return InstalledModel(
            id = "local/${dir.absolutePath}",
            name = name,
            dir = dir,
            sizeBytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum(),
            version = version,
            installedAt = installedAt,
        )
    }

    private fun readModelName(config: File): String? {
        return try {
            val obj = JsonParser.parseString(config.readText()).asJsonObject
            obj.getAsJsonPrimitive("model_name")?.asString
                ?: obj.getAsJsonPrimitive("modelId")?.asString
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Persists a version marker so [list] (and the UI) can show which release is
     * installed. Safe to call for manually-added models too — overwrites any prior mark.
     */
    fun writeVersion(dir: File, version: String) {
        val marker = mapOf(
            "version" to version,
            "installed_at" to System.currentTimeMillis(),
        )
        try {
            File(dir, VERSION_FILE).writeText(gson.toJson(marker))
        } catch (_: Throwable) {
            // Best-effort: a failing marker must not break a successful download.
        }
    }

    private fun readVersion(file: File): Pair<String?, Long?> {
        if (!file.isFile) return null to null
        return try {
            val obj = JsonParser.parseString(file.readText()).asJsonObject
            val v = obj.getAsJsonPrimitive("version")?.asString
            val t = obj.getAsJsonPrimitive("installed_at")?.asLong
            v to t
        } catch (_: Throwable) {
            null to null
        }
    }
}
