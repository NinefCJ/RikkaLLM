package com.alibaba.mnnllm.android.server

import java.io.File

/**
 * Single source of truth for "what kind of model lives in this directory".
 *
 * Local inference supports two real backends today:
 *  - llama.cpp  -> GGUF files (the universal open-LLM container)
 *  - MNN        -> its own `.mnn` weights paired with a `config.json`
 *
 * We deliberately also *recognise* the HuggingFace safetensors layout (config.json
 * + *.safetensors + tokenizer*) so the UI can tell the user "this needs conversion"
 * instead of silently misrouting it. Detection is layout/magic based, not extension
 * based, so oddly named or sharded files are still identified correctly.
 */
enum class ModelFormat {
    GGUF,
    MNN,
    HUGGINGFACE, // recognised but not runnable by the current local backends
    UNKNOWN,
}

data class ModelMetadata(
    val name: String? = null,
    val architecture: String? = null,
    val contextLength: Long? = null,
    val fileType: Long? = null,
    val chatTemplate: String? = null,
    val isMultimodal: Boolean = false,
)

data class ModelLayout(
    val format: ModelFormat,
    val dir: File,
    val mainWeights: File? = null,
    val mmproj: File? = null,
    val metadata: ModelMetadata = ModelMetadata(),
)

object ModelDiscovery {

    fun discover(dir: File): ModelLayout {
        if (!dir.isDirectory) return ModelLayout(ModelFormat.UNKNOWN, dir)
        val files = dir.listFiles().orEmpty()

        // 1) GGUF: identify by magic bytes (works for single, sharded and external-data files).
        val ggufFiles = files.filter { it.isFile && GgufHeaderReader.isGguf(it) }
        if (ggufFiles.isNotEmpty()) {
            val mmproj = ggufFiles.firstOrNull { it.name.contains("mmproj", ignoreCase = true) }
            val main = (ggufFiles - listOfNotNull(mmproj)).pickMainGguf()
            val meta = main?.let { GgufHeaderReader.readHeader(it) }
            return ModelLayout(
                format = ModelFormat.GGUF,
                dir = dir,
                mainWeights = main,
                mmproj = mmproj,
                metadata = ModelMetadata(
                    name = meta?.name,
                    architecture = meta?.architecture,
                    contextLength = meta?.contextLength,
                    fileType = meta?.fileType,
                    chatTemplate = meta?.chatTemplate,
                    isMultimodal = mmproj != null,
                ),
            )
        }

        // 2) MNN: config.json + at least one .mnn weight.
        val config = File(dir, "config.json")
        val hasMnn = files.any { it.extension.equals("mnn", ignoreCase = true) }
        if (config.isFile && hasMnn) {
            return ModelLayout(
                format = ModelFormat.MNN,
                dir = dir,
                metadata = ModelMetadata(name = readModelName(config) ?: dir.name),
            )
        }

        // 3) HuggingFace layout (safetensors + tokenizer): recognised, not locally runnable.
        val hasSafetensors = files.any { it.extension.equals("safetensors", ignoreCase = true) }
        val hasTokenizer = files.any { it.name.startsWith("tokenizer", ignoreCase = true) }
        if (config.isFile && hasSafetensors && hasTokenizer) {
            return ModelLayout(
                format = ModelFormat.HUGGINGFACE,
                dir = dir,
                metadata = ModelMetadata(name = readModelName(config) ?: dir.name),
            )
        }

        return ModelLayout(ModelFormat.UNKNOWN, dir)
    }

    /**
     * Picks the primary GGUF weights from a set:
     *  - for split files (`*-00001-of-00005.gguf`) the lowest-index shard is the entry point
     *    llama.cpp expects;
     *  - otherwise the largest file (the main model, not a small sidecar).
     */
    private fun List<File>.pickMainGguf(): File? {
        if (isEmpty()) return null
        val indexed = mapNotNull { f ->
            val idx = Regex("-(\\d+)-of-\\d+").find(f.name)?.groupValues?.get(1)?.toInt()
            idx?.let { it to f }
        }
        val firstShard = indexed.minByOrNull { it.first }
        if (firstShard != null) return firstShard.second
        return maxByOrNull { it.length() }
    }

    /** Best-effort model name from a `config.json` (MNN / HF conventions). */
    private fun readModelName(config: File): String? = runCatching {
        val text = config.readText()
        listOf("\"model_name\"", "\"modelId\"", "\"_name_or_path\"").firstNotNullOfOrNull { key ->
            Regex("${Regex.escape(key)}\\s*[:=]\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        }
    }.getOrNull()
}
