// Created by ruoyi.sjd on 2025/5/7.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM:
//   - package kept as com.alibaba.mnnllm.android.llm on purpose: the JNI symbols
//     in libmnnllmapp.so are hardcoded to it
//   - QNN/audio/benchmark support removed
//   - Timber replaced with android.util.Log to keep the dependency set minimal
//   - "wait()/notifyAll()" invoked through a java.lang.Object cast for Kotlin 2.x compatibility

package com.alibaba.mnnllm.android.llm

import android.util.Log
import com.alibaba.mnnllm.android.llm.ChatService.Companion.provide
import com.alibaba.mnnllm.android.chat.model.ChatDataItem
import com.alibaba.mnnllm.android.modelsettings.ModelConfig
import com.alibaba.mnnllm.android.model.ModelTypeUtils
import com.alibaba.mnnllm.android.modelsettings.ModelConfig.Companion.getExtraConfigFile
import com.google.gson.Gson
import java.io.File
import java.util.stream.Collectors
import kotlin.concurrent.Volatile
import android.util.Pair
import com.alibaba.mnnllm.android.utils.MmapUtils
import com.alibaba.mnnllm.android.modelsettings.Jinja
import com.alibaba.mnnllm.android.modelsettings.JinjaContext
import com.alibaba.mnnllm.android.modelsettings.ModelConfig.Companion.loadConfig
import com.alibaba.mnnllm.android.utils.FileSplitter

class LlmSession (
    private val modelId: String,
    override var sessionId: String,
    private val configPath: String,
    var savedHistory: List<ChatDataItem>?,
    var backendType: String? = null,
    private val useCustomConfig: Boolean = true
): ChatSession{
    override var supportOmni: Boolean = false
    private var nativePtr: Long = 0

    @Volatile
    private var modelLoading = false

    @Volatile
    private var generating = false

    @Volatile
    private var releaseRequested = false

    private var keepHistory = false

    override fun getHistory(): List<ChatDataItem>?{
        return savedHistory
    }

    override fun setHistory(history: List<ChatDataItem>?) {
    }

    override fun load() {
        Log.d(TAG, "MNN_DEBUG load begin modelId: $modelId backend: $backendType")
        modelLoading = true

        checkAndMergeSplitFiles()
        var historyStringList: List<String>? = null
        val currentHistory = this.savedHistory
        if (!currentHistory.isNullOrEmpty()) {
            historyStringList =
                    currentHistory.stream()
                            .map { obj: ChatDataItem -> obj.text }
                    .filter { obj: String? -> obj != null }
                    .map { obj: String? -> obj!! }
                    .collect(Collectors.toList())
        }
        val config = if (useCustomConfig) {
            ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))!!
        } else {
            ModelConfig.loadDefaultConfig(configPath)!!
        }
        var rootCacheDir: String? = ""
        if (config.useMmap == true) {
            rootCacheDir = MmapUtils.getMmapDir(modelId)
            File(rootCacheDir).mkdirs()
        }
        val configMap = HashMap<String, Any>().apply {
            put("is_r1", ModelTypeUtils.isR1Model(modelId))
            put("mmap_dir", rootCacheDir ?: "")
            put("keep_history", keepHistory)
        }
        val llmConfig = if (useCustomConfig) {
            ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))!!
        } else {
            ModelConfig.loadDefaultConfig(configPath)!!
        }
        // Override backend type from constructor only if not null
        if (backendType != null) {
            llmConfig.backendType = backendType
        }
        Log.d(TAG, "MNN_DEBUG load initNative")
        nativePtr = initNative(
                configPath,
                historyStringList,
        if (llmConfig != null) {
            Gson().toJson(llmConfig)
        } else {
            "{}"
        },
        Gson().toJson(configMap)
        )
        Log.d(TAG, "MNN_DEBUG load initNative end")
        modelLoading = false
        if (nativePtr == 0L) {
            Log.e(TAG, "Model load failed - native initialization returned null pointer")
            throw IllegalStateException("Model load failed - the model module could not be loaded")
        }
        if (releaseRequested) {
            release()
        }
    }

    /**
     * Check if the model is successfully loaded and ready for inference
     */
    fun isModelLoaded(): Boolean {
        return nativePtr != 0L
    }

    /**
     * Check and merge split files for the current model
     */
    private fun checkAndMergeSplitFiles() {
        try {
            val configFile = File(configPath)
            val modelDir = configFile.parentFile

            if (modelDir != null && modelDir.exists()) {
                Log.d(TAG, "Checking for split files in model directory: ${modelDir.absolutePath}")

                if (FileSplitter.needsMerging(modelDir)) {
                    Log.d(TAG, "Found split files that need merging in ${modelDir.absolutePath}")
                    val success = FileSplitter.mergeAllSplitFiles(modelDir)
                    if (success) {
                        Log.d(TAG, "Successfully merged split files for model: $modelId")
                    } else {
                        Log.w(TAG, "Failed to merge some split files for model: $modelId")
                    }
                } else {
                    Log.d(TAG, "No split files found for model: $modelId")
                }
            } else {
                Log.w(TAG, "Model directory not found: ${modelDir?.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/merging split files for model: $modelId", e)
        }
    }

    fun getConfig(): ModelConfig? {
        return ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))
    }

    private fun generateNewSessionId(): String {
        this.sessionId = System.currentTimeMillis().toString()
        return this.sessionId
    }

    override fun generate(prompt: String,
                          params: Map<String, Any>,
                          progressListener: GenerateProgressListener): HashMap<String, Any> {
        Log.d(TAG, "start generate prompt: $prompt")
        synchronized(this) {
            if (mockLatex) {
                Log.d(TAG, "MNN_DEBUG generate intercepted by mockLatex")
                return submitMockLatexHistory(progressListener)
            }
            Log.d(TAG, "MNN_DEBUG submit$prompt")
            generating = true
            val result = submitNative(nativePtr, prompt, keepHistory, progressListener)
            generating = false
            if (releaseRequested) {
                release()
            }
            return result
        }
    }

    override fun reset(): String {
        synchronized(this) {
            resetNative(nativePtr)
        }
        return generateNewSessionId()
    }

    override fun release() {
        synchronized(this) {
            Log.d(
                    TAG,
                    "MNN_DEBUG release nativePtr: $nativePtr mGenerating: $generating"
            )
            if (!generating && !modelLoading) {
                releaseInner()
            } else {
                releaseRequested = true
                while (generating || modelLoading) {
                    try {
                        (this as java.lang.Object).wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e(TAG, "Thread interrupted while waiting for release", e)
                    }
                }
                releaseInner()
            }
        }
    }

    private fun releaseInner() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr)
            nativePtr = 0
            provide().removeSession(sessionId)
            (this as java.lang.Object).notifyAll()
        }
    }

    private external fun initNative(
            configPath: String?,
            history: List<String>?,
            mergedConfigStr: String?,
            configJsonStr: String?
    ): Long

    private external fun submitNative(
            instanceId: Long,
            input: String,
            keepHistory: Boolean,
            listener: GenerateProgressListener
    ): HashMap<String, Any>

    private external fun resetNative(instanceId: Long)

    private external fun getDebugInfoNative(instanceId: Long): String

    private external fun releaseNative(instanceId: Long)

    override fun setKeepHistory(keepHistory: Boolean) {
        this.keepHistory = keepHistory
    }

    override val debugInfo
        get() = getDebugInfoNative(nativePtr) + "\n"

    fun updateMaxNewTokens(maxNewTokens: Int) {
        updateMaxNewTokensNative(nativePtr, maxNewTokens)
    }

    fun updateSystemPrompt(systemPrompt: String) {
        updateSystemPromptNative(nativePtr, systemPrompt)
    }

    override fun updateThinking(thinking: Boolean) {
        val loadedConfig = loadConfig(modelId)
        loadedConfig?.let {
            loadedConfig.jinja = Jinja(context = JinjaContext(enableThinking = thinking))
            ModelConfig.saveConfig(getExtraConfigFile(modelId), loadedConfig)
            updateConfig(Gson().toJson(loadedConfig))
        }
    }

    fun updateConfig(configJson: String) {
        Log.d(TAG, "updateConfig: $configJson")
        updateConfigNative(nativePtr, configJson)
    }

    private external fun updateMaxNewTokensNative(llmPtr: Long, maxNewTokens: Int)

    private external fun updateSystemPromptNative(llmPtr: Long, systemPrompt: String)

    private external fun updateAssistantPromptNative(llmPtr: Long, assistantPrompt: String)

    private external fun updateConfigNative(llmPtr: Long, configJson: String)


    companion object {
        const val TAG: String = "LlmSession"
        var mockLatex: Boolean = false
        var mockLatexContent: String? = null

        init {
            System.loadLibrary("mnnllmapp")
        }
    }



    //New: public method supporting complete history messages
    fun submitFullHistory(
        history: List<Pair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any> {
        synchronized(this) {
            if (mockLatex) {
                Log.d(TAG, "MNN_DEBUG submitFullHistory intercepted by mockLatex")
                return submitMockLatexHistory(progressListener)
            }
            Log.d(TAG, "MNN_DEBUG submitFullHistory with ${history.size} messages")
            //Type conversion: kotlin.Pair -> android.util.Pair
            val androidHistory = history.map { android.util.Pair(it.first, it.second) }
            val result = submitFullHistoryNative(nativePtr, androidHistory, progressListener)
            generating = false
            return result
        }
    }

    private fun submitMockLatexHistory(progressListener: GenerateProgressListener): HashMap<String, Any> {
        val mockText = mockLatexContent ?: "Here is a math formula:\n\n\$E=mc^2$\n\nAnd a block formula:\n\n\$\$a^2 + b^2 = c^2\$\$\n\nEnd of mock."
        Thread {
            try {
                // Simulate streaming delay
                var index = 0
                val chunkSize = 3
                while (index < mockText.length) {
                    Thread.sleep(50)
                    val endIndex = Math.min(index + chunkSize, mockText.length)
                    val chunk = mockText.substring(index, endIndex)
                    if (progressListener.onProgress(chunk)) {
                        break
                    }
                    index = endIndex
                }
                progressListener.onProgress(null) // notify completion
            } catch (e: Exception) {
                Log.e(TAG, "Mock generation failed", e)
            } finally {
                generating = false
            }
        }.start()
        val map = HashMap<String, Any>()
        map["success"] = true
        map["prompt_len"] = 10L
        map["decode_len"] = mockText.length.toLong()
        map["prefill_time"] = 100000L
        map["decode_time"] = 2000000L
        return map
    }
    private external fun submitFullHistoryNative(
        nativePtr: Long,
        history: List<android.util.Pair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any>

    fun modelId(): String {
        //Create temporary variable to avoid modifying original modelId
        return modelId

    }

    fun getSystemPrompt(): String? {
        return getSystemPromptNative(nativePtr)
    }

    private external fun getSystemPromptNative(llmPtr: Long): String?

    private external fun dumpConfigNative(llmPtr: Long): String

    fun dumpConfig(): String {
        return if (nativePtr != 0L) {
            dumpConfigNative(nativePtr)
        } else {
            "{}"
        }
    }
}
