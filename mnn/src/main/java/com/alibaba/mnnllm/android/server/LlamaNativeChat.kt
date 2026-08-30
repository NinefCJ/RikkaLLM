// Bridge to llama.cpp's own chat-template engine.
//
// kotlinllamacpp's high-level LlamaHelper.predict() takes a raw prompt and applies no template
// at all, but the underlying LlamaAndroid class is public and exposes getFormattedChat(), which
// runs llama.cpp's built-in Jinja2 engine over the model's `tokenizer.chat_template`. That is
// strictly more accurate than our own Jinja subset, so we prefer it whenever it is reachable.
//
// LlamaHelper keeps its LlamaAndroid instance and native context id in private fields, but the
// Kotlin compiler emits public static synthetic accessors for them (access$getLlama /
// access$getCurrentContext$p), which we reach reflectively. Every step is failure-tolerant: if
// a future version of the library renames anything, format() simply returns null and the caller
// falls back to its own renderer — the engine never depends on this succeeding.

package com.alibaba.mnnllm.android.server

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.nehuatl.llamacpp.LlamaHelper

private const val TAG = "LlamaNativeChat"

object LlamaNativeChat {

    /**
     * Formats [messages] with llama.cpp's native template engine.
     *
     * @param chatTemplate the raw Jinja template string (usually GGUF `tokenizer.chat_template`).
     * @return the formatted prompt, or null when the native path is unavailable or fails.
     */
    @Suppress("UNCHECKED_CAST")
    fun format(
        helper: LlamaHelper,
        messages: List<Map<String, String>>,
        chatTemplate: String,
    ): String? {
        if (chatTemplate.isBlank() || messages.isEmpty()) return null
        return runCatching {
            val helperClass: Class<*> = helper.javaClass

            // public static LlamaAndroid access$getLlama(LlamaHelper)
            val llama: Any = helperClass
                .getMethod("access\$getLlama", helperClass)
                .invoke(null, helper) ?: return null

            // public static Integer access$getCurrentContext$p(LlamaHelper)
            val contextId = helperClass
                .getMethod("access\$getCurrentContext\$p", helperClass)
                .invoke(null, helper) as? Int ?: return null

            val method = llama.javaClass.getMethod(
                "getFormattedChat",
                Int::class.javaPrimitiveType,
                List::class.java,
                String::class.java,
            )
            val flow = method.invoke(llama, contextId, messages, chatTemplate) as? Flow<String>
                ?: return null

            // The flow emits the formatted prompt, potentially in several chunks.
            runBlocking { flow.toList().joinToString("") }
        }.onFailure {
            Log.w(TAG, "Native chat template formatting unavailable", it)
        }.getOrNull()
    }
}
