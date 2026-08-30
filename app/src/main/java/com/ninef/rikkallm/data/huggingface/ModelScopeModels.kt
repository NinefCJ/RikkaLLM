package com.ninef.rikkallm.data.huggingface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 魔搭社区（ModelScope）API 返回结构。
 * 参考: https://modelscope.cn/api/v1/models
 * 字段均带默认值，缺失或未知字段不会崩溃。
 */
@Serializable
internal data class MsListResponse(
    @SerialName("Data") val data: MsData? = null,
    @SerialName("Success") val success: Boolean = false,
)

@Serializable
internal data class MsData(
    @SerialName("Models") val models: List<MsModel> = emptyList(),
    @SerialName("Total") val total: Int = 0,
)

@Serializable
data class MsModel(
    @SerialName("ModelId") val modelId: String? = null,
    @SerialName("Author") val author: String? = null,
    @SerialName("Downloads") val downloads: Long? = null,
    @SerialName("Likes") val likes: Long? = null,
    @SerialName("CreatedAt") val createdAt: String? = null,
    @SerialName("UpdatedAt") val updatedAt: String? = null,
    @SerialName("Tags") val tags: List<String>? = null,
    @SerialName("License") val license: String? = null,
    @SerialName("Intro") val intro: String? = null,
    @SerialName("RepoType") val repoType: String? = null,
    @SerialName("Files") val files: List<MsFile>? = null,
)

@Serializable
data class MsFile(
    @SerialName("Name") val name: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Type") val type: String? = null,
)

/**
 * 将魔搭模型映射为统一的 [HfModel]。
 * [detail] 为 true 时依据文件列表累加体积（用于详情页"模型体积"展示）。
 */
fun MsModel.toHfModel(detail: Boolean = false): HfModel {
    val id = modelId ?: (author ?: "unknown")
    val total = if (detail) (files?.sumOf { it.size ?: 0L } ?: 0L) else 0L
    val tags = buildList {
        addAll(tags ?: emptyList())
        license?.takeIf { it.isNotBlank() }?.let { add("license:$it") }
    }
    return HfModel(
        id = id,
        modelId = id,
        author = author ?: id.substringBefore("/"),
        lastModified = updatedAt,
        createdAt = createdAt,
        downloads = downloads ?: 0L,
        likes = (likes ?: 0L).toInt(),
        tags = tags,
        safetensors = SafetensorsInfo(total = total, parameters = emptyMap()),
    )
}
