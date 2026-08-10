// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM: ModelListManager (model market/UI layer) lookups removed;
// type detection here is name/tag based only.
package com.alibaba.mnnllm.android.model

import java.util.Locale

object ModelTypeUtils {

    fun isAudioModel(modelId: String): Boolean {
        return modelId.lowercase(Locale.getDefault()).contains("audio") || isOmni(modelId)
    }

    fun isMultiModalModel(modelName: String): Boolean {
        return isAudioModel(modelName) || isVisualModel(modelName) || isDiffusionModel(modelName) || isOmni(modelName)
    }

    fun isQnnModel(modelId: String): Boolean {
        val normalizedId = modelId.lowercase(Locale.getDefault())
        if (modelId.startsWith("local/") && normalizedId.contains("qnn")) {
            return true
        }
        return false
    }

    fun isDiffusionModel(modelName: String): Boolean {
        val lower = modelName.lowercase(Locale.getDefault())
        return lower.contains("stable-diffusion") || lower.contains("sana")
    }

    fun isSanaModel(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("sana")
    }

    fun requiresFaceImageInput(modelName: String): Boolean {
        return isSanaModel(modelName)
    }

    fun isVisualModel(modelId: String): Boolean {
        return modelId.lowercase(Locale.getDefault()).contains("vl") || isOmni(modelId) ||
                isSanaModel(modelId)
    }

    fun isVideoModel(modelId: String): Boolean {
        return modelId.lowercase(Locale.getDefault()).contains("video")
    }

    fun isR1Model(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("deepseek-r1")
    }

    fun isOmni(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("omni")
    }

    fun isSupportThinkingSwitchByTags(extraTags: List<String>): Boolean {
        return extraTags.any { it.equals("ThinkingSwitch", ignoreCase = true) }
    }

    fun isOpenClWarningByExtraTags(extraTags: List<String>): Boolean {
        return extraTags.any {
            it.equals("opencl_warning", ignoreCase = true) ||
                it.equals("OpenCLWarning", ignoreCase = true)
        }
    }

    fun isQnnModel(tags: List<String>): Boolean {
        return tags.any { it.equals("QNN", ignoreCase = true) }
    }

    fun supportAudioOutput(modelName: String): Boolean {
        return isOmni(modelName)
    }

    /**
     * Check if the model is a TTS (Text-to-Speech) model
     */
    fun isTtsModel(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("bert-vits") ||
               modelName.lowercase(Locale.getDefault()).contains("tts")
    }

    /**
     * Check if the model is a TTS model based on tags
     */
    fun isTtsModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("TTS", ignoreCase = true) }
    }

    /**
     * Check if the model is an ASR (Automatic Speech Recognition) model based on tags
     */
    fun isAsrModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("ASR", ignoreCase = true) }
    }

    /**
     * Check if the model is a thinking model based on tags
     */
    fun isThinkingModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("Think", ignoreCase = true) }
    }

    /**
     * Check if the model is a visual model based on tags
     */
    fun isVisualModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("Vision", ignoreCase = true) }
    }

    /**
     * Check if the model is a video model based on tags
     */
    fun isVideoModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("Video", ignoreCase = true) }
    }

    /**
     * Check if the model is an audio model based on tags
     */
    fun isAudioModelByTags(tags: List<String>): Boolean {
        return tags.any { it.equals("Audio", ignoreCase = true) }
    }
}
