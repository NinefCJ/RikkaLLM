// Engine abstraction used by the OpenAI-compatible server. The Ktor layer only talks
// to this interface, which keeps the request/response pipeline testable on the JVM
// with a fake engine. The real implementation wraps the MNN LlmSession.

package com.alibaba.mnnllm.android.server

/**
 * Token usage and timing reported by the engine after a generation.
 *
 * [prefillMs] and [decodeMs] are wall-clock durations measured inside the MNN JNI
 * (see llm_mnn_jni.cpp `submitHistory` / `chat` paths). [promptTokens] / [completionTokens]
 * cover the full history — they include the whole prompt fed to prefill and all tokens
 * emitted during decode.
 *
 * `prefillTokensPerSecond` / `decodeTokensPerSecond` are derived helpers that compute
 * tokens/sec from the raw timing fields. They are `null` when the corresponding token
 * count is zero (division-by-zero guard).
 *
 * [memoryKb] is the peak resident set size (KiB) observed around the generation, sourced
 * from [MemoryStats] (best-effort). It is 0 when the platform does not expose process
 * memory (e.g. a non-Linux JVM during unit tests).
 */
data class GenerationStats(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val prefillMs: Long = 0,
    val decodeMs: Long = 0,
    val memoryKb: Long = 0,
) {
    val prefillTokensPerSecond: Double?
        get() = if (prefillMs > 0) promptTokens.toDouble() * 1000.0 / prefillMs.toDouble() else null

    val decodeTokensPerSecond: Double?
        get() = if (decodeMs > 0) completionTokens.toDouble() * 1000.0 / decodeMs.toDouble() else null
}

/** Thrown when a generation is requested while another one is still running. */
class EngineBusyException : Exception("The local model engine is busy with another request")

/** Thrown when a generation is requested but no model is loaded. */
class ModelNotLoadedException : Exception("No local model is loaded")

interface LocalLlmEngine {
    /** Human readable identifier of the loaded model, or null when nothing is loaded. */
    val loadedModel: String?

    /** Loads a model from a directory. Blocking; call on a worker thread. Returns false when the directory is not usable. */
    fun load(modelDirectory: String): Boolean

    /** Releases the loaded model and frees native resources. */
    fun unload()

    /** Best-effort application of OpenAI sampling parameters before a generation. */
    fun applySampling(temperature: Float?, topP: Float?, maxTokens: Int?)

    /**
     * Runs a blocking generation over (role, content) message pairs.
     *
     * [images] carries image references for multimodal (mmproj) models — either absolute
     * paths, `file://` URIs or `data:` base64 URLs. Engines that cannot process images
     * ignore it; most backends only accept a single image, so callers should pass the
     * most relevant one first.
     *
     * [onToken] receives each decoded piece of text and returns true to abort the
     * generation early (e.g. the HTTP client disconnected).
     */
    fun generate(
        messages: List<Pair<String, String>>,
        images: List<String>,
        onToken: (String) -> Boolean,
    ): GenerationStats
}

/** Text-only convenience overload, forwarding to [LocalLlmEngine.generate] with no images. */
fun LocalLlmEngine.generate(
    messages: List<Pair<String, String>>,
    onToken: (String) -> Boolean,
): GenerationStats = generate(messages, emptyList(), onToken)

/** Backward-compatible alias for [LocalLlmEngine]; kept so the existing adapter + tests compile. */
typealias MnnEngine = LocalLlmEngine
