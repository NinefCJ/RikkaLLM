package com.ninef.rikkallm.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.deepread.DeepReadRequest
import com.ninef.rikkallm.data.deepread.DeepReadReport
import com.ninef.rikkallm.data.deepread.DeepReadRunner

private const val DEEP_READ_TOOL_NAME = "deep_read"

/**
 * 深度阅读工具：让 Agent 在对话中对一段材料（URL 或文本）发起结构化深度阅读，
 * 返回由 [DeepReadRunner] 生成的报告（摘要 / 章节 / 核心观点 / 论据）。
 *
 * 该工具会触发多轮 LLM 补全，默认需要用户确认。
 */
fun createDeepReadTool(runner: DeepReadRunner): Tool = Tool(
    name = DEEP_READ_TOOL_NAME,
    description = "Deep-read a piece of material (URL or pasted text) and produce a structured report: a summary, detailed per-section analysis, core takeaways, and quoted evidence. Use when the user wants a thorough understanding of a long article, paper, document, or webpage. Triggers several LLM calls, so it asks for confirmation before running.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("material_url", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional URL of the material to read. Takes priority over material_text when both are present.")
                })
                put("material_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional raw material text to read.")
                })
                put("language", buildJsonObject {
                    put("type", "string")
                    put("description", "Output language, e.g. 'zh-CN' or 'en'. Defaults to 'zh-CN'.")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional title; if blank the model infers one.")
                })
            },
            required = emptyList(),
        )
    },
    needsApproval = { true },
    execute = { args ->
        val obj = args as? JsonObject
        val url = (obj?.get("material_url") as? JsonPrimitive)?.contentOrNull ?: ""
        val text = (obj?.get("material_text") as? JsonPrimitive)?.contentOrNull ?: ""
        val lang = (obj?.get("language") as? JsonPrimitive)?.contentOrNull ?: "zh-CN"
        val title = (obj?.get("title") as? JsonPrimitive)?.contentOrNull ?: ""
        if (url.isBlank() && text.isBlank()) {
            return@Tool listOf(UIMessagePart.Text("[deep_read] 请提供 material_url 或 material_text"))
        }
        val report = runner.run(
            DeepReadRequest(
                materialUrl = url.trim(),
                materialText = text.trim(),
                language = lang.trim().ifBlank { "zh-CN" },
                title = title.trim(),
            ),
        )
        listOf(UIMessagePart.Text(renderReport(report)))
    },
)

fun renderReport(report: DeepReadReport): String = buildString {
    appendLine("# ${report.title}")
    appendLine()
    appendLine("> 来源：${report.source}")
    appendLine()
    appendLine("## 摘要")
    appendLine(report.summary)
    appendLine()
    if (report.keyPoints.isNotEmpty()) {
        appendLine("## 核心观点")
        report.keyPoints.forEachIndexed { i, p -> appendLine("${i + 1}. $p") }
        appendLine()
    }
    report.sections.forEach { section ->
        appendLine("## ${section.title}")
        appendLine(section.content)
        appendLine()
    }
    if (report.evidence.isNotEmpty()) {
        appendLine("## 关键论据（原文引述）")
        report.evidence.forEach { q -> appendLine("> $q") }
        appendLine()
    }
}
