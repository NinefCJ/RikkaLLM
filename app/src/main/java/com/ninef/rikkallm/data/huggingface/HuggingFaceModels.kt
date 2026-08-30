package com.ninef.rikkallm.data.huggingface

import kotlin.math.log10
import kotlin.math.pow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.provider.Modality

/**
 * 与 HuggingFace Hub API (`https://huggingface.co/api/models`) 对应的模型实体。
 *
 * 字段均带默认值，配合 [com.ninef.rikkallm.data.huggingface.HuggingFaceApi] 中宽松的
 * Json 配置解析，即使上游返回了未知字段或字段缺失也不会崩溃。
 */
@Serializable
data class HfModel(
    val id: String = "",
    @SerialName("modelId")
    val modelId: String = "",
    val author: String? = null,
    val lastModified: String? = null,
    val createdAt: String? = null,
    val downloads: Long = 0,
    val likes: Int = 0,
    @SerialName("pipeline_tag")
    val pipelineTag: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("library_name")
    val libraryName: String? = null,
    val private: Boolean = false,
    val safetensors: SafetensorsInfo? = null,
    @SerialName("cardData")
    val cardData: JsonObject? = null,
) {
    /** 规范化后的仓库 id（形如 `org/name`） */
    val repoId: String
        get() = id.ifBlank { modelId }

    /** 展示名称：取仓库 id 最后一段 */
    val displayName: String
        get() = repoId.substringAfterLast('/').ifBlank { repoId }

    /** 是否为可直接在本机 MNN 引擎运行的格式 */
    val isMnnFormat: Boolean
        get() = tags.any { it.equals("mnn", ignoreCase = true) }
            || author?.equals("MNN-Community", ignoreCase = true) == true
            || repoId.contains(".mnn", ignoreCase = true)

    /** 许可证标识（优先从 tags 的 `license:` 解析，回退到 cardData） */
    val license: String?
        get() = tags.firstOrNull { it.startsWith("license:", ignoreCase = true) }
            ?.substringAfter("license:")?.takeIf { it.isNotBlank() }
            ?: cardData?.get("license")?.toString()?.trim('"', '[', ']', ' ')?.takeIf { it.isNotBlank() }
}

@Serializable
data class SafetensorsInfo(
    val total: Long = 0,
    val parameters: Map<String, Long> = emptyMap(),
)

/** 模型市场查询参数，对应 HF API 的过滤字段 */
data class HfQuery(
    val search: String = "",
    val pipelineTag: String? = null,
    val libraryName: String? = null,
    val license: String? = null,
    val author: String? = null,
    val limit: Int = 100,
)

/** 任务类型（HF pipeline_tag） */
enum class TaskType(val label: String, val hfPipeline: String?) {
    TEXT_GENERATION("文本生成", "text-generation"),
    IMAGE_TEXT_TO_TEXT("视觉理解", "image-text-to-text"),
    IMAGE_TO_TEXT("图像描述", "image-to-text"),
    IMAGE_CLASSIFICATION("图像分类", "image-classification"),
    OBJECT_DETECTION("目标检测", "object-detection"),
    AUTOMATIC_SPEECH_RECOGNITION("语音识别", "automatic-speech-recognition"),
    TEXT_TO_SPEECH("语音合成", "text-to-speech"),
    TEXT_TO_IMAGE("文生图", "text-to-image"),
    VIDEO_TEXT_TO_TEXT("视频理解", "video-text-to-text"),
    FILL_MASK("掩码填充", "fill-mask"),
    QUESTION_ANSWERING("问答", "question-answering"),
    TRANSLATION("翻译", "translation"),
    SUMMARIZATION("摘要", "summarization"),
    FEATURE_EXTRACTION("特征提取", "feature-extraction"),
    SENTENCE_SIMILARITY("句向量", "sentence-similarity"),
    TEXT_CLASSIFICATION("文本分类", "text-classification"),
    TOKEN_CLASSIFICATION("词性标注", "token-classification"),
    TABLE_QUESTION_ANSWERING("表格问答", "table-question-answering"),
    OTHER("其他", null),
    ;

    companion object {
        fun fromPipeline(tag: String?): TaskType =
            entries.firstOrNull { it.hfPipeline == tag } ?: OTHER
    }
}

/** 框架（HF library_name） */
enum class Framework(val label: String, val hfFilter: String) {
    PYTORCH("PyTorch", "pytorch"),
    TENSORFLOW("TensorFlow", "tensorflow"),
    JAX("JAX", "jax"),
    ONNX("ONNX", "onnx"),
    SAFETENSORS("SafeTensors", "safetensors"),
    MNN("MNN", "mnn"),
    LLAMACPP("llama.cpp", "gguf"),
    KERAS("Keras", "keras"),
    DIFFUSERS("Diffusers", "diffusers"),
    ;

    companion object {
        fun fromLibrary(name: String?): Framework? =
            entries.firstOrNull { it.hfFilter == name }
    }
}

/** 许可证类型 */
enum class LicenseType(val label: String, val hfFilter: String) {
    APACHE_2_0("Apache 2.0", "apache-2.0"),
    MIT("MIT", "mit"),
    GPL_3_0("GPL-3.0", "gpl-3.0"),
    GPL_2_0("GPL-2.0", "gpl-2.0"),
    BSD("BSD", "bsd"),
    CC_BY_4_0("CC BY 4.0", "cc-by-4.0"),
    CC_BY_SA_4_0("CC BY-SA 4.0", "cc-by-sa-4.0"),
    CC0_1_0("CC0", "cc0-1.0"),
    AGPL_3_0("AGPL-3.0", "agpl-3.0"),
    LLAMA_2("Llama 2", "llama2"),
    OTHER("其他", "other"),
    ;

    companion object {
        fun fromTag(tag: String?): LicenseType =
            entries.firstOrNull { it.hfFilter.equals(tag, ignoreCase = true) } ?: OTHER
    }
}

/** 模型规模分桶（按参数量，单位十亿） */
enum class ModelSizeBucket(val label: String, val minParams: Double) {
    TINY("≤ 1B", 0.0),
    SMALL("1B – 7B", 1.0),
    MEDIUM("7B – 14B", 7.0),
    LARGE("14B – 34B", 14.0),
    XL("34B – 70B", 34.0),
    XXL("≥ 70B", 70.0),
    ;

    companion object {
        fun fromParams(billions: Double): ModelSizeBucket =
            entries.lastOrNull { billions >= it.minParams } ?: TINY
    }
}

/** 任务类型 */
fun HfModel.taskType(): TaskType = TaskType.fromPipeline(pipelineTag)

/** 框架 */
fun HfModel.framework(): Framework? = Framework.fromLibrary(libraryName)
    ?: tags.firstNotNullOfOrNull { Framework.fromLibrary(it) }

/** 许可证类型 */
fun HfModel.licenseType(): LicenseType = LicenseType.fromTag(license)

/** 参数量（十亿） */
fun HfModel.paramCountB(): Double =
    (safetensors?.parameters?.values?.sum() ?: 0L) / 1_000_000_000.0

/** 规模分桶 */
fun HfModel.sizeBucket(): ModelSizeBucket = ModelSizeBucket.fromParams(paramCountB())

/** 估算下载体积（MB） */
fun HfModel.estimatedDownloadMb(): Long = (safetensors?.total ?: 0L) / (1024 * 1024)

/** 估算最低运行内存（MB）：权重 fp16 约 2 bytes/param + 20% 开销 */
fun HfModel.estimateMinRamMb(): Int =
    ((safetensors?.total ?: 0L) * 1.2 / (1024 * 1024)).toInt().coerceAtLeast(512)

/** 根据任务类型推断输入模态（MultiModal 框架当前仅支持 TEXT / IMAGE） */
fun HfModel.inferInputModalities(): List<Modality> = when (taskType()) {
    TaskType.IMAGE_TEXT_TO_TEXT, TaskType.IMAGE_TO_TEXT, TaskType.VIDEO_TEXT_TO_TEXT ->
        listOf(Modality.TEXT, Modality.IMAGE)
    else -> listOf(Modality.TEXT)
}

/** 根据任务类型推断输出模态 */
fun HfModel.inferOutputModalities(): List<Modality> = when (taskType()) {
    TaskType.TEXT_TO_IMAGE -> listOf(Modality.IMAGE)
    TaskType.TEXT_TO_SPEECH -> listOf(Modality.IMAGE) // 框架暂无 AUDIO 模态，占位标注
    else -> listOf(Modality.TEXT)
}

/** 模型市场列表排序维度 */
enum class SortOption(val label: String) {
    DOWNLOADS("下载量"),
    UPDATED("更新时间"),
    SIZE("模型大小"),
}

/** 模型大小（参数，单位十亿）双头滑块的对数映射范围 */
private val SIZE_LOG_MIN = log10(0.1)
private val SIZE_LOG_MAX = log10(400.0)

/** RangeSlider 位置 [0,1] → 参数量（十亿），采用对数刻度以覆盖 0.1B~400B */
fun sliderPosToParamsB(pos: Float): Double =
    10.0.pow(SIZE_LOG_MIN + pos * (SIZE_LOG_MAX - SIZE_LOG_MIN))

/** 格式化参数量（十亿）为人类可读文本 */
fun formatParamsB(b: Double): String = when {
    b >= 100 -> "%.0fB".format(b)
    b >= 10 -> "%.0fB".format(b)
    else -> "%.1fB".format(b)
}
