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
 * (`com.ninef.rikkallm.data.mnn`), keeping this class portable and unit-testable
 * without an Android context or network access.
 */
object LocalModelRegistry {

    private val gson = Gson()

    /**
     * A model discovered on disk.
     *
     * @param id model id in the engine's scheme — `local/<absoluteDir>` so it can be
     *           passed straight to [com.alibaba.mnnllm.android.llm.LlmSession].
     * @param name display name (from GGUF `general.name` / config.json `model_name`, else
     *             the directory name).
     * @param dir absolute directory holding the model.
     * @param sizeBytes total size of the model directory (weights + assets).
     * @param version semantic version string recorded by the downloader, or null if the
     *                model was added manually (no `.mnnversion` marker).
     * @param installedAt epoch millis when the version marker was written, or null.
     * @param format the detected on-disk format (GGUF / MNN / HuggingFace / UNKNOWN).
     * @param metadata best-effort metadata parsed from the model header/config.
     */
    data class InstalledModel(
        val id: String,
        val name: String,
        val dir: File,
        val sizeBytes: Long,
        val version: String?,
        val installedAt: Long?,
        val format: ModelFormat,
        val metadata: ModelMetadata?,
    )

    /** Marker file written next to `config.json` by downloads to record the version. */
    const val VERSION_FILE = ".mnnversion"

    /**
     * Lists every model found under [root]. Detection is delegated to [ModelDiscovery],
     * so a directory qualifies when it carries GGUF weights, an MNN layout
     * (`config.json` + `*.mnn`), or a HuggingFace layout — not just `config.json`.
     * This is what lets GGUF models (which have no `config.json`) appear in the manager.
     */
    fun list(root: File): List<InstalledModel> {
        if (!root.isDirectory) return emptyList()
        val models = mutableListOf<InstalledModel>()
        root.listFiles()?.forEach { top ->
            if (!top.isDirectory) return@forEach
            var layout = ModelDiscovery.discover(top)
            // Support the `builtin/<name>/` one-level-nested layout: if the top-level
            // directory is not itself a model, check each of its direct children.
            if (layout.format == ModelFormat.UNKNOWN) {
                top.listFiles()?.firstOrNull { child ->
                    child.isDirectory && ModelDiscovery.discover(child).format != ModelFormat.UNKNOWN
                }?.let { layout = ModelDiscovery.discover(it) }
            }
            if (layout.format == ModelFormat.UNKNOWN) return@forEach
            models.add(readModel(layout.dir, layout))
        }
        return models.sortedBy { it.name.lowercase() }
    }

    private fun readModel(dir: File, layout: ModelLayout): InstalledModel {
        val name = layout.metadata.name ?: dir.name
        val (version, installedAt) = readVersion(File(dir, VERSION_FILE))
        return InstalledModel(
            id = "local/${dir.absolutePath}",
            name = name,
            dir = dir,
            sizeBytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum(),
            version = version,
            installedAt = installedAt,
            format = layout.format,
            metadata = layout.metadata,
        )
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
