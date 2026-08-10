// Created by ruoyi.sjd on 2025/6/20.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM: com.alibaba.mls.api dependencies replaced -
// ApplicationProvider -> AppContext, DownloadFileUtils -> File.deleteRecursively,
// ModelSources constants inlined.

package com.alibaba.mnnllm.android.utils

import com.alibaba.mnnllm.android.model.ModelUtils
import java.io.File

object MmapUtils {

    // Mirrors com.alibaba.mls.api.source.ModelSources constants.
    private const val SOURCE_MODEL_SCOPE = "ModelScope/"
    private const val SOURCE_MODELERS = "Modelers/"
    private const val SOURCE_HUGGING_FACE = "HuggingFace/"

    fun clearMmapCache(modelId: String): Boolean {
        return File(getMmapDir(modelId)).deleteRecursively()
    }

    fun getMmapDir(modelId: String): String {
        // Local models use a dedicated cache folder to avoid long/absolute path fragments under tmps
        if (modelId.startsWith("local/")) {
            val safeId = ModelUtils.safeModelId(modelId)
            return AppContext.get().filesDir.toString() + "/local_temps/" + safeId
        }
        if (modelId.startsWith("Builtin/")) {
            val safeId = ModelUtils.safeModelId(modelId)
            return AppContext.get().filesDir.toString() + "/builtin_temps/" + safeId
        }

        var newModelId = modelId
        val isModelScope = modelId.startsWith(SOURCE_MODEL_SCOPE)
        val isModelers = modelId.startsWith(SOURCE_MODELERS)
        val isHuggingFace = modelId.startsWith(SOURCE_HUGGING_FACE)
        if (isModelers || isHuggingFace || isModelScope) {
            newModelId = modelId.substring(modelId.indexOf("/") + 1)
        }
        if (newModelId.startsWith("MNN/")) {
            newModelId = newModelId.replace("MNN/", "taobao-mnn/")
        }
        var rootCacheDir =
            AppContext.get().filesDir.toString() + "/tmps/" + ModelUtils.safeModelId(
                newModelId
            )
        if (isModelScope) {
            rootCacheDir = "$rootCacheDir/modelscope"
        } else if (isModelers) {
            rootCacheDir = "$rootCacheDir/modelers"
        }
        return rootCacheDir
    }
}
