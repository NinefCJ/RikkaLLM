// MnnEngine implementation backed by the real MNN LlmSession. Lives outside the pure
// server pipeline so the Ktor layer stays JVM-testable.

package com.alibaba.mnnllm.android.server

import android.util.Log
import com.alibaba.mnnllm.android.llm.ChatService
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import kotlin.concurrent.Volatile

private const val TAG = "MnnEngineAdapter"

class MnnEngineAdapter : MnnEngine {

    @Volatile
    private var session: LlmSession? = null

    @Volatile
    private var label: String? = null

    @Volatile
    private var dirPath: String? = null

    private val gson = Gson()

    val modelDirPath: String? get() = dirPath

    override val loadedModel: String?
        get() = session?.takeIf { it.isModelLoaded() }?.let { label ?: it.modelId() }

    /**
     * Loads a model from a directory containing a MNN config.json. Blocking; call on a
     * worker thread. Throws on failure.
     */
    @Synchronized
    override fun load(modelDirectory: String): Boolean {
        unload()
        val dir = File(modelDirectory)
        val configFile = File(dir, "config.json")
        if (!configFile.exists()) {
            throw IllegalArgumentException("config.json not found in ${dir.absolutePath}")
        }
        // "local/<dir>" model id keeps compatibility with ModelConfig path conventions.
        val modelId = "local/" + dir.absolutePath
        Log.i(TAG, "Loading model from ${dir.absolutePath}")
        val created = ChatService.provide().createSession(
            modelId = modelId,
            modelName = dir.name,
            sessionIdParam = null,
            historyList = null,
            configPath = configFile.absolutePath,
            useNewConfig = true,
            useCustomConfig = false,
        )
        created.load()
        if (created is LlmSession && created.isModelLoaded()) {
            session = created
            label = dir.name
            dirPath = dir.absolutePath
            Log.i(TAG, "Model loaded: ${dir.name}")
        } else {
            throw IllegalStateException("Engine reported the model is not loaded after load()")
        }
        return true
    }

    @Synchronized
    override fun unload() {
        val current = session ?: return
        session = null
        label = null
        dirPath = null
        runCatching { current.release() }.onFailure { Log.w(TAG, "release failed", it) }
    }

    override fun applySampling(temperature: Float?, topP: Float?, maxTokens: Int?) {
        val current = session ?: return
        val config = JsonObject()
        temperature?.let { config.addProperty("temperature", it) }
        topP?.let { config.addProperty("topP", it) }
        maxTokens?.let { config.addProperty("max_new_tokens", it) }
        if (config.size() > 0) {
            runCatching { current.updateConfig(gson.toJson(config)) }
                .onFailure { Log.w(TAG, "applySampling failed", it) }
        }
    }

    override fun generate(
        messages: List<Pair<String, String>>,
        images: List<String>,
        onToken: (String) -> Boolean,
    ): GenerationStats {
        // MNN's LlmSession.submitFullHistory() is text-only: it has no image channel, so
        // multimodal requests simply run as text on this backend.
        if (images.isNotEmpty()) {
            Log.w(TAG, "Ignoring ${images.size} image(s): the MNN backend does not support vision input")
        }
        val current = session ?: throw ModelNotLoadedException()
        if (!current.isModelLoaded()) throw ModelNotLoadedException()

        // Best-effort memory telemetry: sample RSS before and after the (blocking)
        // submitFullHistory call. The KV cache grows monotonically during decode, so the
        // post-generation RSS is effectively the peak memory used by this generation.
        val memBefore = MemoryStats.residentMemoryKb()
        val result = current.submitFullHistory(
            messages.map { android.util.Pair(it.first, it.second) },
            object : GenerateProgressListener {
            override fun onProgress(progress: String?): Boolean {
                // The engine signals completion with a null progress.
                if (progress == null) return false
                return onToken(progress)
            }
        })
        val memAfter = MemoryStats.residentMemoryKb()

        (result["error"] as? String)?.let { error ->
            throw IllegalStateException("Engine generation failed: $error")
        }

        // Peak resident set over the generation window (null-safe: keep 0 when unavailable).
        val peakKb = listOfNotNull(memBefore, memAfter).maxOrNull() ?: 0L

        return GenerationStats(
            promptTokens = (result["prompt_len"] as? Number)?.toLong() ?: 0L,
            completionTokens = (result["decode_len"] as? Number)?.toLong() ?: 0L,
            // MNN returns microseconds for prefill_time / decode_time; convert to ms
            // so the OpenAI usage extension below stays in human-readable units.
            prefillMs = ((result["prefill_time"] as? Number)?.toLong() ?: 0L) / 1000L,
            decodeMs = ((result["decode_time"] as? Number)?.toLong() ?: 0L) / 1000L,
            memoryKb = peakKb,
        )
    }
}
