package com.ninef.rikkallm.data.ai.tools

import androidx.documentfile.provider.DocumentFile
import com.ninef.rikkallm.data.editor.EditorSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 轻量 IDE 工具集（替代原先依赖 code-server 桥接的 IdeTools）。
 *
 * 全部基于本地 [EditorSessionManager]，直接操作应用内/SAF 文件，无需 180MB 运行时。
 * - ide_open_file：在编辑器打开一个文件
 * - ide_get_active_file：读取当前活动文件内容
 * - ide_list_open_tabs：列出已打开的标签
 * - ide_get_diagnostics：返回当前诊断（轻量编辑器不含静态分析，返回空）
 */
fun createIdeTools(session: EditorSessionManager): List<Tool> = listOf(
    Tool(
        name = "ide_open_file",
        description = "在 IDE 编辑器中打开一个工作区文件。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put(
                        "name",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("要打开的文件名（在编辑器根目录中查找）"))
                        },
                    )
                },
                required = listOf("name"),
            )
        },
        needsApproval = { false },
        execute = { args ->
            val name = args.jsonObject["name"]?.jsonPrimitive?.content ?: return@Tool err("缺少参数 name")
            val doc = findInBase(session.getBase(), name)
            if (doc == null) {
                err("未找到文件：$name")
            } else {
                session.openFile(doc)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive("已在编辑器中打开：${doc.name}"))
                }.toString()))
            }
        },
    ),
    Tool(
        name = "ide_get_active_file",
        description = "读取 IDE 编辑器当前活动文件的名称与内容。",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = { false },
        execute = {
            val tab = session.getActiveTab()
            if (tab == null) {
                err("编辑器当前没有打开任何文件")
            } else {
                val snippet = tab.content.take(16000)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put(
                        "text",
                        JsonPrimitive("文件：${tab.name}（${tab.language}）\n```${tab.language}\n$snippet\n```"),
                    )
                }.toString()))
            }
        },
    ),
    Tool(
        name = "ide_list_open_tabs",
        description = "列出 IDE 编辑器中已打开的所有文件标签。",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = { false },
        execute = {
            val tabs = session.tabs.value
            if (tabs.isEmpty()) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive("（没有打开的文件）"))
                }.toString()))
            } else {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(tabs.joinToString("\n") { "- ${it.name}${if (it.dirty) " *" else ""}" }))
                }.toString()))
            }
        },
    ),
    Tool(
        name = "ide_get_diagnostics",
        description = "返回编辑器当前诊断信息。轻量编辑器不含静态分析，通常返回空。",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = { false },
        execute = {
            listOf(UIMessagePart.Text(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive("（轻量编辑器未启用静态分析，暂无诊断）"))
            }.toString()))
        },
    ),
)

private fun err(message: String): List<UIMessagePart> = listOf(
    UIMessagePart.Text(
        buildJsonObject {
            put("type", JsonPrimitive("text"))
            put("text", JsonPrimitive("错误：$message"))
        }.toString(),
    ),
)

private suspend fun findInBase(base: DocumentFile, name: String, depth: Int = 4): DocumentFile? = withContext(Dispatchers.IO) {
    if (depth < 0) return@withContext null
    base.listFiles().forEach { doc ->
        if (doc.name == name && !doc.isDirectory) return@withContext doc
        if (doc.isDirectory) {
            findInBase(doc, name, depth - 1)?.let { return@withContext it }
        }
    }
    null
}
