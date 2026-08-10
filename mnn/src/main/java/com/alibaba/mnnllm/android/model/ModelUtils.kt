// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM: only pure string/model-id utilities kept. UI helpers
// (drawable/Toast/intents), ModelDownloadManager and ModelItem based lookups
// are not part of the engine module.
package com.alibaba.mnnllm.android.model

import android.annotation.SuppressLint

object ModelUtils {

    @SuppressLint("DefaultLocale")
    fun generateBenchMarkString(metrics: HashMap<String, Any>): String {
        if (metrics.containsKey("total_timeus")) {
            return generateDiffusionBenchMarkString(metrics)
        }
        val promptLen = metrics.getOrDefault("prompt_len", 0L) as Long
        val decodeLen = metrics.getOrDefault("decode_len", 0L) as Long
        val prefillTimeUs = metrics.getOrDefault("prefill_time", 0L) as Long
        val decodeTimeUs = metrics.getOrDefault("decode_time", 0L) as Long
        var visionTimeUs = if (metrics.containsKey("vision_time")) metrics["vision_time"] as Long else 0L
        var audioTimeUs = if (metrics.containsKey("audio_time")) metrics["audio_time"] as Long else 0L
        if (promptLen == 0L || decodeLen == 0L) {
            return "generateBenchMarkString error"
        }
        // Calculate speeds in tokens per second
        var totalPrefillTimeUs = prefillTimeUs + visionTimeUs + audioTimeUs
        val promptSpeed =
            if ((totalPrefillTimeUs > 0)) (promptLen / (totalPrefillTimeUs / 1000000.0)) else 0.0
        val decodeSpeed = if ((decodeTimeUs > 0)) (decodeLen / (decodeTimeUs / 1000000.0)) else 0.0
        return String.format(
            "Prefill: %.2fs, %d tokens, %.2f tokens/s \nDecode: %.2fs, %d tokens, %.2f tokens/s",
            totalPrefillTimeUs.toFloat() / 1000000, promptLen, promptSpeed,
            decodeTimeUs.toFloat() / 1000000, decodeLen, decodeSpeed,
        )
    }

    @SuppressLint("DefaultLocale")
    fun generateDiffusionBenchMarkString(metrics: HashMap<String, Any>): String {
        val totalDuration = metrics["total_timeus"] as Long * 1.0 / 1000000.0
        return String.format("Generate time: %.2f s", totalDuration)
    }

    @JvmStatic
    fun getModelName(modelId: String?): String? {
        if (modelId != null && modelId.contains("/")) {
            return modelId.substring(modelId.lastIndexOf("/") + 1)
        }
        return modelId
    }

    fun safeModelId(modelId: String): String {
        return modelId.replace("/".toRegex(), "_")
    }

    //split "Huggingface/taobao-mnn/Qwen-1.5B" to ["Huggingface", "taobao-mnn/Qwen-1.5B"]
    fun splitSource(modelId: String): Array<String> {
        val firstSlashIndex = modelId.indexOf('/')
        if (firstSlashIndex == -1) {
            return arrayOf(modelId)
        }
        val source = modelId.substring(0, firstSlashIndex)
        val path = modelId.substring(firstSlashIndex + 1)
        return arrayOf(source, path)
    }

    fun getSource(modelId: String): String? {
        val firstSlashIndex = modelId.indexOf('/')
        if (firstSlashIndex == -1) {
            return null
        }
        return modelId.substring(0, firstSlashIndex)
    }

    fun getRepositoryPath(modelId: String): String {
        return splitSource(modelId)[1]
    }
}
