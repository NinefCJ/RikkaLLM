package com.ninef.rikkallm.data.deepread

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 深度阅读请求：一段材料（URL 或粘贴文本）+ 期望输出语言 + 可选标题。
 *
 * @param materialUrl 材料 URL（与 [materialText] 二选一，URL 优先）
 * @param materialText 直接提供的材料文本
 * @param language 输出语言，如 "zh-CN"、"en"
 * @param title 可选标题，缺省时由模型推断
 */
@Serializable
data class DeepReadRequest(
    val materialUrl: String = "",
    val materialText: String = "",
    val language: String = "zh-CN",
    val title: String = "",
)

/** 深度阅读报告的一个章节 */
@Serializable
data class DeepReadSection(
    val title: String = "",
    val content: String = "",
)

/** 一份完整的深度阅读报告 */
@Serializable
data class DeepReadReport(
    val id: String = Uuid.random().toString(),
    val title: String = "",
    val source: String = "",
    val language: String = "zh-CN",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val summary: String = "",
    val sections: List<DeepReadSection> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
)

@Serializable
data class DeepReadState(
    val reports: List<DeepReadReport> = emptyList(),
)
