package me.rerere.ai.ui

import kotlinx.serialization.Serializable

/**
 * 生成式 UI 卡片条目。
 *
 * 模型通过 `:::generative-ui` 围栏输出的结构化卡片中的一个条目。
 * 客户端按 [label]/[value] 渲染，可选 [detail]/[link]/[code] 增强展示。
 */
@Serializable
data class GenerativeCardItem(
    val label: String = "",
    val value: String = "",
    val detail: String? = null,
    val link: String? = null,
    val code: String? = null,
)

/**
 * 生成式 UI 卡片声明（模型输出的结构化数据）。
 *
 * 与 AmberAgent 的 GenerativeWidget 对应：允许模型在回复中以结构化方式
 * 呈现工具结果（搜索结果、文件列表、设备状态、代码片段等），客户端渲染为
 * 统一的 Compose 卡片，而非纯文本。所有字段在渲染前须经 [com.ninef.rikkallm.data.ai.generativeui.GenerativeUiSanitizer]
 * 清洗，防止恶意链接 / 超长内容 / 注入内容进入 UI。
 */
@Serializable
data class GenerativeCardData(
    val kind: String = "card",
    val title: String = "",
    val subtitle: String? = null,
    val items: List<GenerativeCardItem> = emptyList(),
    val footer: String? = null,
)
