// Local LLM backend backed by kotlinllamacpp (llama.cpp Kotlin bindings). Runs GGUF models
// and is selected automatically by detectBackend() when a model directory contains a .gguf
// file. Kept side-by-side with MnnEngineAdapter so both engines satisfy the same LocalLlmEngine
// contract and the Ktor pipeline stays backend-agnostic.

package com.alibaba.mnnllm.android.server

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.concurrent.Volatile

private const val TAG = "LlamaEngine"
private const val CONTEXT_LENGTH = 4096

/**
 * llama.cpp backend implementing the shared [LocalLlmEngine] contract.
 *
 * kotlinllamacpp opens model files through a ContentResolver File-Descriptor bridge (to dodge
 * Android scoped storage), so we hand it `file://` URIs built from the on-disk weights. Our
 * models always live under app-internal storage, which [Uri.fromFile] is allowed to address.
 *
 * [cacheDir] is where base64 images from OpenAI-style requests are decoded before being handed
 * to the vision projector (mmproj).
 */
class LlamaEngine(
    private val resolver: ContentResolver,
    private val cacheDir: File? = null,
) : LocalLlmEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Reactive stream the engine emits status updates + decoded tokens on.
    private val llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var helper: LlamaHelper? = null

    @Volatile
    private var loadedDir: String? = null

    // The model's own chat template from the GGUF header (tokenizer.chat_template).
    // Rendered by ChatTemplateRenderer in generate(); falls back to the generic prompt
    // when absent or unsupported.
    @Volatile
    private var chatTemplate: String? = null

    // True when the loaded model carries a multimodal projector (mmproj*.gguf). Only these
    // models accept an image; feeding one to a text-only model makes llama.cpp error out.
    @Volatile
    private var supportsVision: Boolean = false

    // OpenAI sampling params requested via applySampling().
    //
    // NOTE: kotlinllamacpp 0.4.0 cannot honour them. LlamaHelper.predict() builds a fixed
    // {prompt, emit_partial_completion} argument map, so temperature/topP/maxTokens never reach
    // llama.cpp. The underlying LlamaAndroid.launchCompletion(ctxId, params) does accept an
    // arbitrary map, but token streaming is bound to LlamaHelper's own sharedFlow — hijacking
    // it would break streaming, so we deliberately keep the fixed call path and record the
    // values here for when the library exposes sampling.
    private var sampling: SamplingRequest? = null

    override val loadedModel: String?
        get() = loadedDir

    @Synchronized
    override fun load(modelDirectory: String): Boolean {
        val layout = ModelDiscovery.discover(File(modelDirectory))
        val gguf = layout.mainWeights ?: run {
            Log.e(TAG, "No GGUF weights found in $modelDirectory")
            return false
        }
        val mmproj = layout.mmproj
        val mainUri = Uri.fromFile(gguf).toString()
        val mmprojUri = mmproj?.let { Uri.fromFile(it).toString() }
        // Read the model's preferred context length from its GGUF header instead of
        // hardcoding it. Clamp to a mobile-feasible window so large-context models
        // (e.g. 32k/128k) don't OOM lower-end devices; the header value is still used
        // whenever it fits the budget.
        val contextLength = clampContext(layout.metadata.contextLength)
        runBlocking {
            helper?.let { releaseHelper(it) }
            helper = LlamaHelper(resolver, scope, llmFlow)
            helper!!.load(path = mainUri, contextLength = contextLength, mmprojPath = mmprojUri) { _ -> }
        }
        chatTemplate = layout.metadata.chatTemplate
        supportsVision = mmproj != null
        loadedDir = modelDirectory
        // Drop images decoded for the previous model so the cache cannot grow unbounded.
        cacheDir?.let { runCatching { LlamaImageInput.clearCache(it) } }
        Log.i(
            TAG,
            "Loaded GGUF model: ${gguf.name} (ctx=$contextLength, mmproj=${mmproj != null}, " +
                "chatTemplate=${chatTemplate != null})"
        )
        return true
    }

    @Synchronized
    override fun unload() {
        helper?.let { releaseHelper(it) }
        helper = null
        loadedDir = null
        chatTemplate = null
        supportsVision = false
    }

    override fun applySampling(temperature: Float?, topP: Float?, maxTokens: Int?) {
        sampling = SamplingRequest(temperature, topP, maxTokens)
    }

    override fun generate(
        messages: List<Pair<String, String>>,
        images: List<String>,
        onToken: (String) -> Boolean,
    ): GenerationStats {
        val h = helper ?: throw ModelNotLoadedException()
        val prompt = renderPrompt(messages)
        val imageUri = resolveImage(images)
        val memBefore = MemoryStats.residentMemoryKb()
        var completionTokens = 0L
        runBlocking {
            launch {
                try {
                    // predict(prompt, imagePath?, emitPartialCompletion)
                    h.predict(prompt, imageUri)
                } catch (e: Throwable) {
                    Log.e(TAG, "predict failed", e)
                    return@launch
                }
                llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            completionTokens += countTokens(event.word)
                            // Client asked to abort: stop the native prediction and let the
                            // stream drain to Done/Error so the call unwinds cleanly.
                            if (onToken(event.word)) h.stopPrediction()
                        }
                        is LlamaHelper.LLMEvent.Done -> return@collect
                        is LlamaHelper.LLMEvent.Error -> return@collect
                        // Unknown/housekeeping events: stop draining the stream cleanly.
                        else -> return@collect
                    }
                }
            }.join()
        }
        val memAfter = MemoryStats.residentMemoryKb()
        val peakKb = listOfNotNull(memBefore, memAfter).maxOrNull() ?: 0L
        return GenerationStats(completionTokens = completionTokens, memoryKb = peakKb)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Picks the single image llama.cpp can consume for this generation, or null for text-only.
     * Images are only forwarded to models that actually loaded an mmproj projector — passing one
     * to a text-only model makes the native layer fail the request.
     */
    private fun resolveImage(images: List<String>): String? {
        if (images.isEmpty()) return null
        if (!supportsVision) {
            Log.w(TAG, "Ignoring ${images.size} image(s): loaded model has no mmproj projector")
            return null
        }
        val cache = cacheDir
        if (cache == null) {
            Log.w(TAG, "Cannot use image input: no cache directory configured")
            return null
        }
        return images.firstNotNullOfOrNull { image ->
            LlamaImageInput.resolve(image, cache).also {
                if (it == null) Log.w(TAG, "Unsupported image reference, skipping")
            }
        }
    }

    private fun releaseHelper(h: LlamaHelper) {
        // LlamaHelper.release() stops any in-flight prediction and frees the native llama.cpp
        // context. Without it, every load() would leak a native context (the previous code
        // probed for unload()/close(), neither of which exists in this library).
        runCatching { h.release() }
            .onFailure { Log.w(TAG, "Failed to release native context", it) }
    }

    /**
     * Clamps a model's declared context length into a mobile-feasible window.
     * [CONTEXT_LENGTH] is the floor (used when the header has no value); the ceiling keeps
     * large-context GGUF models from exhausting RAM on lower-end devices.
     */
    private fun clampContext(headerCtx: Long?): Int {
        val ctx = headerCtx ?: CONTEXT_LENGTH.toLong()
        return ctx.coerceIn(CONTEXT_LENGTH.toLong(), MAX_CONTEXT_LENGTH.toLong()).toInt()
    }

    private companion object {
        // Floor when the header carries no context length (matches the previous default).
        const val MAX_CONTEXT_LENGTH = 8192
    }

    /**
     * Renders (role, content) message pairs into a single prompt string. kotlinllamacpp's
     * [LlamaHelper.predict] takes a raw prompt — the helper applies no template at all — so we
     * apply the model's own chat template (from the GGUF header) using the most accurate engine
     * available, in order:
     *
     * 1. llama.cpp's native Jinja2 engine (via [LlamaNativeChat]) — exact model formatting.
     * 2. Our own Jinja subset ([ChatTemplateRenderer]) — pure JVM, no native call needed.
     * 3. A generic instruction-style template — always works, only roughly correct.
     *
     * Each stage degrades gracefully, so an unrecognised template never breaks generation.
     */
    private fun renderPrompt(messages: List<Pair<String, String>>): String {
        val template = chatTemplate
        val trimmed = messages.map { (role, content) -> role.lowercase() to content }
        if (!template.isNullOrBlank()) {
            val helper = helper
            if (helper != null && trimmed.isNotEmpty()) {
                LlamaNativeChat.format(
                    helper = helper,
                    messages = trimmed.map { (role, content) ->
                        mapOf("role" to role, "content" to content)
                    },
                    chatTemplate = template,
                )?.let { return it }
            }
            val rendered = ChatTemplateRenderer.render(
                template = template,
                messages = trimmed.map { (role, content) ->
                    ChatTemplateRenderer.Message(role = role, content = content)
                },
            )
            if (rendered != null) return rendered
            Log.w(TAG, "Chat template unsupported for this model, using generic prompt")
        }
        val sb = StringBuilder()
        for ((role, content) in messages) {
            when (role.lowercase()) {
                "system" -> sb.append("System: $content\n")
                "user" -> sb.append("User: $content\n")
                "assistant" -> sb.append("Assistant: $content\n")
                else -> sb.append("$role: $content\n")
            }
        }
        sb.append("Assistant:")
        return sb.toString()
    }

    private fun countTokens(text: String): Long =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }.toLong()

    private data class SamplingRequest(
        val temperature: Float?,
        val topP: Float?,
        val maxTokens: Int?,
    )
}
