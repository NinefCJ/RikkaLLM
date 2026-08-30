package com.ninef.rikkallm.data.ai.generativeui

/**
 * 生成式 UI 提示词构建器（对应 AmberAgent 的 buildGenerativeUiPrompt）。
 *
 * 以 `:::generative-ui` 围栏形式注入 system prompt，指导模型在合适的时机
 * （搜索结果、文件列表、设备状态、代码片段等）输出结构化卡片声明，
 * 并明确安全约束（禁协议注入、禁脚本/HTML、禁伪造数据）。
 *
 * 围栏格式：
 * ```
 * :::generative-ui
 * {"kind":"...","title":"...","items":[{"label":"...","value":"..."}]}
 * :::
 * ```
 */
object GenerativeUiPrompt {
    const val FENCE_START = ":::generative-ui"
    const val FENCE_END = ":::"

    /** 生成注入用的提示词；[modelName] 为当前模型名（用于提示词调试/日志）。 */
    fun build(modelName: String? = null): String {
        val modelHint = if (modelName.isNullOrBlank()) "" else " (model: $modelName)"
        return buildString {
            appendLine("<generative_ui>")
            appendLine("You may render structured results as a card so the client shows them as a compact UI component instead of plain text$modelHint.")
            appendLine("Use this ONLY when a structured presentation genuinely helps: search results, file/workspace listings, device or system status, command output summaries, step-by-step recipes, or short code samples.")
            appendLine("Wrap exactly one JSON object in the fenced block below:")
            appendLine("```")
            appendLine(FENCE_START)
            appendLine("{\"kind\": \"search_result\", \"title\": \"Search results\", \"subtitle\": \"optional\", \"items\": [{\"label\": \"label\", \"value\": \"value\", \"detail\": \"optional\", \"link\": \"optional https link\"}], \"footer\": \"optional\"}")
            appendLine(FENCE_END)
            appendLine("```")
            appendLine("Card schema rules:")
            appendLine("- `kind` is one of: `card`, `search_result`, `file_list`, `data_list`, `code`, `status`. Default `card`.")
            appendLine("- `title` required, at most 120 chars. `subtitle`/`footer` optional, at most 200/300 chars.")
            appendLine("- `items` at most 12. Each item: `label` (short, at most 60) + `value` (at most 500); `detail`, `link`, `code` are optional.")
            appendLine("- All `link` values MUST start with `https://` or `http://`. Never use `javascript:` or other schemes.")
            appendLine("- Never embed HTML, scripts, iframes, or external CDN resources in any field — all content is rendered as plain text.")
            appendLine("- Only include data actually produced in this conversation; never fabricate results.")
            appendLine("If none of the above applies, keep answering in plain text without emitting the fenced block.")
            append("</generative_ui>")
        }
    }
}
