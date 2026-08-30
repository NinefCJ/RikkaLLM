package com.ninef.rikkallm.data.ai.generativeui

import me.rerere.ai.ui.GenerativeCardData
import me.rerere.ai.ui.GenerativeCardItem

/**
 * 生成式 UI 卡片安全清洗器（GenerativeWidgetSanitizer 的轻量版）。
 *
 * 模型输出的结构化卡片在进入渲染管线前必须经过校验：
 * - 字段长度限制，防止超长内容拖垮 UI / 泄露上下文
 * - 链接协议白名单（仅 http/https，拦截 `javascript:` 等注入式协议）
 * - 条目数量 / 嵌套深度限制
 * - 明确禁止 HTML / 脚本渲染面（本项目卡片为纯 Compose 渲染，天然无脚本，
 *   此处仍将链接与文本作为纯文本处理，杜绝 HTML 注入）
 *
 * 校验失败返回 null，调用方应保留原始文本而放弃渲染，绝不抛异常。
 */
object GenerativeUiSanitizer {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_SUBTITLE_LENGTH = 200
    const val MAX_FOOTER_LENGTH = 300
    const val MAX_ITEMS = 12
    const val MAX_ITEM_LABEL_LENGTH = 60
    const val MAX_ITEM_VALUE_LENGTH = 500
    const val MAX_ITEM_DETAIL_LENGTH = 1000
    const val MAX_ITEM_CODE_LENGTH = 2000
    const val MAX_LINK_LENGTH = 500
    const val MAX_KIND_LENGTH = 32

    private val SAFE_LINK_PREFIXES = listOf("http://", "https://")

    /** 清洗卡片声明；不合法时返回 null（不抛异常）。 */
    fun sanitize(card: GenerativeCardData): GenerativeCardData? {
        val kind = card.kind.trim()
        if (kind.isEmpty() || kind.length > MAX_KIND_LENGTH) return null
        if (card.title.length > MAX_TITLE_LENGTH) return null
        if ((card.subtitle?.length ?: 0) > MAX_SUBTITLE_LENGTH) return null
        if ((card.footer?.length ?: 0) > MAX_FOOTER_LENGTH) return null
        if (card.items.size > MAX_ITEMS) return null

        val items = card.items.mapNotNull(::sanitizeItem)
        // 条目全部非法时视为非法卡片（避免空壳卡片占据聊天区域）
        if (items.isEmpty() && card.items.isNotEmpty()) return null
        // 空卡片（无标题无条目）无展示价值
        if (card.title.isBlank() && items.isEmpty()) return null

        return GenerativeCardData(
            kind = kind,
            title = card.title.trim(),
            subtitle = card.subtitle?.trim()?.takeIf { it.isNotEmpty() },
            items = items,
            footer = card.footer?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun sanitizeItem(item: GenerativeCardItem): GenerativeCardItem? {
        if (item.label.length > MAX_ITEM_LABEL_LENGTH) return null
        if (item.value.length > MAX_ITEM_VALUE_LENGTH) return null
        if ((item.detail?.length ?: 0) > MAX_ITEM_DETAIL_LENGTH) return null
        if ((item.code?.length ?: 0) > MAX_ITEM_CODE_LENGTH) return null
        if (item.label.isBlank() && item.value.isBlank()) return null

        val link = sanitizeLink(item.link)
        return GenerativeCardItem(
            label = item.label.trim(),
            value = item.value.trim(),
            detail = item.detail?.trim()?.takeIf { it.isNotEmpty() },
            link = link,
            code = item.code?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun sanitizeLink(link: String?): String? {
        if (link == null) return null
        val trimmed = link.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LINK_LENGTH) return null
        // 仅允许白名单协议前缀；拦截 javascript:/data:/vbscript: 等注入式链接
        val lower = trimmed.lowercase()
        return if (SAFE_LINK_PREFIXES.any { lower.startsWith(it) }) trimmed else null
    }
}
