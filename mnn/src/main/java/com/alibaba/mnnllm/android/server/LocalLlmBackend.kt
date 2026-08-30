// Backend selection for the local inference stack. The Ktor pipeline only cares about the
// LocalLlmEngine contract; which concrete engine runs a model is chosen per-directory by
// [detectBackend] so users can mix MNN (.mnn) and GGUF (llama.cpp) models freely.

package com.alibaba.mnnllm.android.server

import java.io.File

/**
 * Supported local inference backends.
 * - [MNN]: the legacy bundled engine, runs `.mnn` weights (config.json + `*.mnn`).
 * - [LLAMA]: kotlinllamacpp (llama.cpp Kotlin bindings), runs `.gguf` weights and can load an
 *   optional `mmproj*.gguf` multimodal projector.
 */
enum class LocalLlmBackend {
    MNN,
    LLAMA,
}

/**
 * Inspect a model directory and pick the backend that can actually run its files.
 * Detection is layout/magic based (see [ModelDiscovery]) rather than extension
 * based, so it stays correct for sharded GGUF, MNN and HuggingFace layouts.
 * Anything we don't recognise still falls back to MNN to preserve the previous
 * behaviour for MNN-style directories.
 */
fun detectBackend(modelDir: String): LocalLlmBackend {
    return when (ModelDiscovery.discover(File(modelDir)).format) {
        ModelFormat.GGUF -> LocalLlmBackend.LLAMA
        ModelFormat.MNN -> LocalLlmBackend.MNN
        else -> LocalLlmBackend.MNN
    }
}
