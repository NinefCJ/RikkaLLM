// Engine abstraction used by the OpenAI-compatible server. The Ktor layer only talks
// to this interface, which keeps the request/response pipeline testable on the JVM
// with a fake engine. The real implementation wraps the MNN LlmSession.

package com.alibaba.mnnllm.android.server

/** Token usage reported by the engine after a generation. */
data class GenerationStats(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
)

/** Thrown when a generation is requested while another one is still running. */
class EngineBusyException : Exception("The local model engine is busy with another request")

/** Thrown when a generation is requested but no model is loaded. */
class ModelNotLoadedException : Exception("No local model is loaded")

interface MnnEngine {
    /** Human readable identifier of the loaded model, or null when nothing is loaded. */
    val loadedModel: String?

    /** Best-effort application of OpenAI sampling parameters before a generation. */
    fun applySampling(temperature: Float?, topP: Float?, maxTokens: Int?)

    /**
     * Runs a blocking generation over (role, content) message pairs.
     *
     * [onToken] receives each decoded piece of text and returns true to abort the
     * generation early (e.g. the HTTP client disconnected).
     */
    fun generate(messages: List<Pair<String, String>>, onToken: (String) -> Boolean): GenerationStats
}
