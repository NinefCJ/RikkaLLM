package com.ninef.rikkallm.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.Locale

const val TOOL_SEARCH_TOOL_NAME = "tool_search"
const val TOOLS_LIST_TOOL_NAME = "tools_list"
const val TOOL_SEARCH_AUTO_THRESHOLD = 40
const val TOOL_SEARCH_DEFAULT_LIMIT = 5

private val toolSearchJson = Json { ignoreUnknownKeys = true }

/**
 * 生成 `tool_search` 工具：按意图/类别搜索完整工具目录，并把最佳匹配的 schema
 * 暴露给下一步生成（懒加载机制的核心入口）。
 */
fun createToolSearchTool(registry: ToolRegistry): Tool = Tool(
    name = TOOL_SEARCH_TOOL_NAME,
    description = "Search RikkaHub's full tool catalog by intent/category and expose the best matching tool schemas for the next step.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", stringProp("Required. What capability you need, e.g. \"read PDF\", \"search memory\", \"webview click\", \"截图\", or an exact tool name from tools_list."))
                put("category", stringProp("Optional category filter, e.g. file_system, shell, memory, network, skill, conversation, clipboard, calendar, screen, local, hitl, mcp."))
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum tools to expose. Defaults to 5; capped at 20.")
                })
                put("display_title", stringProp("Optional short user-facing action title in Chinese, e.g. 查找写作工具."))
            },
            required = listOf("query")
        )
    },
    needsApproval = { false },
    systemPrompt = { _, _ ->
        val index = ToolSearchIndex(registry)
        val categories = index.categoryCounts().entries
            .sortedWith(compareByDescending<Map.Entry<ToolCategory, Int>> { it.value }.thenBy { it.key.name })
            .joinToString(", ") { "${it.key.name.lowercase(Locale.ROOT)}:${it.value}" }
        val residentCount = registry.tools().count { tool ->
            ToolExposureState.isResidentTool(tool.name, registry.metadataFor(tool.name)?.category)
        }
        """
        Tool discovery:
        - This run has ${registry.metadata.size} generated tools across categories: $categories.
        - If the needed tool is not currently visible, call `$TOOL_SEARCH_TOOL_NAME` with a concrete query. It exposes the best matching schemas for the next generation step.
        - `$TOOLS_LIST_TOOL_NAME` is only a debug/catalog view. A hidden tool listed by `$TOOLS_LIST_TOOL_NAME` is not callable until `$TOOL_SEARCH_TOOL_NAME` exposes it.
        - If you used `$TOOLS_LIST_TOOL_NAME` to identify a tool name, call `$TOOL_SEARCH_TOOL_NAME` again with that exact tool name, then execute a name from `expanded_tools` on the next step.
        - Resident tools currently stay visible without search: $residentCount core tools plus discovered tools.
        """.trimIndent()
    },
    execute = { input ->
        val query = input.jsonObject["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val category = input.jsonObject["category"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val limit = input.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: TOOL_SEARCH_DEFAULT_LIMIT
        val index = ToolSearchIndex(registry)
        listOf(UIMessagePart.Text(index.searchPayload(query, category, limit).toString()))
    },
)

/**
 * 生成 `tools_list` 工具：目录视图（仅列出名称/类别/风险，不暴露隐藏 schema）。
 */
fun createToolsListTool(registry: ToolRegistry): Tool = Tool(
    name = TOOLS_LIST_TOOL_NAME,
    description = "List the full tool catalog (names, categories, risk levels) as a debug/catalog view. Use tool_search to expose a hidden tool's schema before calling it.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("category", stringProp("Optional category filter, e.g. file_system, shell, memory, network."))
            }
        )
    },
    needsApproval = { false },
    execute = { input ->
        val category = input.jsonObject["category"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val payload = buildJsonObject {
            put("status", "ok")
            put("total_tools", registry.metadata.size)
            put("tools", buildJsonArray {
                registry.metadata
                    .filter {
                        category == null || it.category.name.lowercase(Locale.ROOT) == category.lowercase(Locale.ROOT)
                    }
                    .forEach { metadata ->
                        add(buildJsonObject {
                            put("name", metadata.name)
                            put("category", metadata.category.name.lowercase(Locale.ROOT))
                            put("risk", metadata.risk.name.lowercase(Locale.ROOT))
                            put("mutates", metadata.mutates)
                            put("needs_approval", metadata.needsApproval)
                        })
                    }
            })
            put("note", "Catalog/debug view only. A hidden tool is not callable until tool_search exposes its schema.")
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

/**
 * 工具目录打分索引。
 *
 * 参照 AmberAgent 的 ToolSearchIndex：对工具元数据进行名称/描述/类别/别名打分排序，
 * 输出含 `expanded_tools` 的 JSON payload，供 `tool_search` 工具使用。
 */
class ToolSearchIndex(
    private val registry: ToolRegistry,
) {
    private val toolsByName = registry.tools().associateBy { it.name }

    fun categoryCounts(): Map<ToolCategory, Int> =
        registry.metadata.groupingBy { it.category }.eachCount()

    fun searchPayload(
        query: String,
        category: String?,
        limit: Int,
    ): JsonObject {
        val normalizedCategory = category?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
        val boundedLimit = limit.coerceIn(1, 20)
        val matches = search(query, normalizedCategory, boundedLimit)
        val expandedTools = matches.map { it.metadata.name }
        val fullSchemaChars = registry.tools().sumOf { it.schemaFootprintChars() }
        val residentSchemaChars = registry.tools()
            .filter { tool -> ToolExposureState.isResidentTool(tool.name, registry.metadataFor(tool.name)?.category) }
            .sumOf { it.schemaFootprintChars() }
        val expandedSchemaChars = matches.sumOf { it.tool.schemaFootprintChars() }
        return buildJsonObject {
            put("status", "ok")
            put("query", query)
            normalizedCategory?.let { put("category", it) }
            put("limit", boundedLimit)
            put("total_tools", registry.metadata.size)
            put("resident_tools", registry.tools().count { tool ->
                ToolExposureState.isResidentTool(tool.name, registry.metadataFor(tool.name)?.category)
            })
            put("matches_count", matches.size)
            put("expanded_tools", buildJsonArray { expandedTools.forEach(::add) })
            put("callability_note", "Only tools in expanded_tools are newly callable on the next model step. tools_list is catalog/debug only and does not expose hidden schemas.")
            put("trace", buildJsonObject {
                put("mode", if (registry.metadata.size > TOOL_SEARCH_AUTO_THRESHOLD) "lazy" else "bypass")
                put("query", query)
                put("hit_tools", buildJsonArray { expandedTools.forEach(::add) })
                put("expanded_tools", buildJsonArray { expandedTools.forEach(::add) })
                put("estimated_full_schema_chars", fullSchemaChars)
                put("estimated_resident_schema_chars", residentSchemaChars)
                put("estimated_expanded_schema_chars", expandedSchemaChars)
                put("estimated_schema_savings_chars", (fullSchemaChars - residentSchemaChars - expandedSchemaChars).coerceAtLeast(0))
            })
            put("tools", buildJsonArray { matches.forEach { add(it.toJson()) } })
            if (matches.isEmpty()) {
                put("category_candidates", buildJsonArray {
                    categoryCounts().entries
                        .sortedWith(compareByDescending<Map.Entry<ToolCategory, Int>> { it.value }.thenBy { it.key.name })
                        .take(16)
                        .forEach { (name, count) ->
                            add(buildJsonObject {
                                put("category", name.name.lowercase(Locale.ROOT))
                                put("count", count)
                            })
                        }
                })
                put("debug_hint", "No matching tool was expanded. Try a more concrete Chinese/English query, or use tools_list only to identify an exact tool name and then call tool_search(query=\"<exact_tool_name>\").")
            } else {
                put("next_step", "On the next model step, call one of expanded_tools exactly. Do not call tools only seen in tools_list unless you first expose them with tool_search(query=\"<exact_tool_name>\"). Permissions still apply.")
            }
        }
    }

    private fun search(
        query: String,
        category: String?,
        limit: Int,
    ): List<ScoredTool> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val tokens = normalizedQuery
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return registry.metadata
            .asSequence()
            .filter { it.name != TOOL_SEARCH_TOOL_NAME && it.name != TOOLS_LIST_TOOL_NAME }
            .filter { category == null || it.category.name.lowercase(Locale.ROOT) == category }
            .mapNotNull { metadata ->
                val tool = toolsByName[metadata.name] ?: return@mapNotNull null
                val score = scoreTool(metadata, tool, normalizedQuery, tokens, category)
                if (score <= 0) null else ScoredTool(metadata, tool, score)
            }
            .sortedWith(compareByDescending<ScoredTool> { it.score }.thenBy { it.metadata.name })
            .take(limit)
            .toList()
    }

    private fun scoreTool(
        metadata: ToolMetadata,
        tool: Tool,
        query: String,
        tokens: List<String>,
        category: String?,
    ): Int {
        var score = 0
        if (category != null && metadata.category.name.lowercase(Locale.ROOT) == category) score += 25
        if (tokens.isEmpty()) return if (category != null) score + 1 else 0
        val name = metadata.name.lowercase(Locale.ROOT)
        val description = tool.description.lowercase(Locale.ROOT)
        val categoryText = metadata.category.name.lowercase(Locale.ROOT)
        if (query == name) score += 240
        if (tokens.any { it == name }) score += 180
        tokens.forEach { token ->
            score += when {
                name == token -> 120
                name.startsWith(token) -> 80
                name.contains(token) -> 55
                categoryText == token -> 35
                categoryText.contains(token) -> 24
                description.contains(token) -> 16
                else -> 0
            }
        }
        searchAliases(metadata).forEach { alias ->
            val normalizedAlias = alias.lowercase(Locale.ROOT)
            score += when {
                query == normalizedAlias -> 100
                query.contains(normalizedAlias) -> 55
                normalizedAlias.contains(query) && query.length >= 2 -> 32
                tokens.any { it == normalizedAlias } -> 80
                tokens.any { normalizedAlias.contains(it) && it.length >= 2 } -> 24
                else -> 0
            }
        }
        if (tokens.any { token -> name.split('_').contains(token) }) score += 30
        if (score > 0 && !metadata.mutates && metadata.risk == ToolRisk.NORMAL) score += 2
        return score
    }

    private fun searchAliases(metadata: ToolMetadata): List<String> = buildList {
        val name = metadata.name
        val category = metadata.category
        when (name) {
            "workspace_read_file" -> addAll(listOf("文件", "读文件", "查看文件", "工作区"))
            "workspace_write_file" -> addAll(listOf("写文件", "创建文件", "保存"))
            "workspace_edit_file" -> addAll(listOf("编辑", "修改文件", "替换"))
            "workspace_shell" -> addAll(listOf("终端", "命令", "脚本", "运行命令", "shell"))
            "memory_tool" -> addAll(listOf("记忆", "长期记忆", "回忆"))
            "search_web" -> addAll(listOf("搜索", "网络搜索", "查询"))
            "scrape_web" -> addAll(listOf("抓取", "网页内容", "爬取"))
            "use_skill" -> addAll(listOf("技能", "skill", "能力"))
            "skills_list" -> addAll(listOf("技能", "skill", "能力", "目录", "列表"))
            "model_council" -> addAll(listOf("议会", "模型议会", "model council", "多模型", "综合", "共识", "分歧", "交叉验证"))
            "recent_chats" -> addAll(listOf("最近会话", "聊天记录", "会话"))
            "conversation_search" -> addAll(listOf("搜索会话", "历史搜索", "查找聊天"))
            "get_time_info" -> addAll(listOf("时间", "日期", "当前时间"))
            "text_to_speech" -> addAll(listOf("语音", "朗读", "tts"))
            "eval_javascript" -> addAll(listOf("javascript", "求值", "运行代码"))
            "calendar_query" -> addAll(listOf("日历", "日程查询", "事件"))
            "calendar_create" -> addAll(listOf("创建事件", "添加日程", "预约"))
            "clipboard_tool" -> addAll(listOf("剪贴板", "复制", "粘贴"))
            "get_screen_time" -> addAll(listOf("屏幕时间", "屏幕使用时长"))
            "ask_user" -> addAll(listOf("提问", "询问用户", "征求"))
        }
        if (name.startsWith("mcp__") || category == ToolCategory.MCP) {
            addAll(listOf("mcp", "MCP", "外部工具"))
        }
        if (name.startsWith("skill_")) {
            addAll(listOf("技能", "skill"))
        }
        if (category == ToolCategory.FILE_SYSTEM) {
            addAll(listOf("文件", "工作区"))
        }
        if (category == ToolCategory.SHELL) {
            addAll(listOf("终端", "命令", "脚本"))
        }
    }

    private fun ScoredTool.toJson(): JsonObject = buildJsonObject {
        put("name", metadata.name)
        put("category", metadata.category.name.lowercase(Locale.ROOT))
        put("description", tool.description.take(360))
        put("score", score)
        put("mutates", metadata.mutates)
        put("sensitive_read", metadata.sensitiveRead)
        put("needs_approval", metadata.needsApproval)
        put("allows_auto_approval", metadata.autoApprovable)
        put("risk", metadata.risk.name.lowercase(Locale.ROOT))
        put("output_budget_chars", metadata.outputBudgetChars)
        put("schema", tool.parameters()?.toString().orEmpty())
    }

    private data class ScoredTool(
        val metadata: ToolMetadata,
        val tool: Tool,
        val score: Int,
    )
}

/**
 * 工具暴露状态：resident/lazy 两档。
 *
 * - 工具数量 ≤ [TOOL_SEARCH_AUTO_THRESHOLD] 或不存在 `tool_search` 时进入 bypass 模式，
 *   每步全量暴露（与现有行为完全一致）。
 * - 超过阈值时进入 lazy 模式：仅暴露 resident（常驻）工具与已通过 `tool_search` 发现的工具，
 *   减少每次请求携带的 schema token 量。
 */
class ToolExposureState private constructor(
    private val allTools: List<Tool>,
    private val lazyMode: Boolean,
    initialExposedNames: Set<String>,
) {
    private val toolsByName = allTools.associateBy { it.name }
    private val exposedNames = initialExposedNames.toMutableSet()

    val enabled: Boolean
        get() = lazyMode

    fun toolsForStep(): List<Tool> =
        if (!lazyMode) allTools else allTools.filter { it.name in exposedNames }

    fun exposeToolNames(names: Iterable<String>) {
        if (!lazyMode) return
        names
            .filter { it in toolsByName }
            .forEach { exposedNames += it }
    }

    /** 从已执行的 `tool_search` 输出中提取 `expanded_tools` 并暴露。 */
    fun observeExecutedTools(executedTools: List<UIMessagePart.Tool>) {
        if (!lazyMode) return
        val expandedNames = executedTools
            .filter { it.toolName == TOOL_SEARCH_TOOL_NAME }
            .flatMap { it.expandedToolNames() }
        exposeToolNames(expandedNames)
    }

    companion object {
        fun from(tools: List<Tool>): ToolExposureState {
            val toolCount = tools.count { it.name !in DISCOVERY_UTILITY_TOOLS }
            val hasSearch = tools.any { it.name == TOOL_SEARCH_TOOL_NAME }
            if (toolCount <= TOOL_SEARCH_AUTO_THRESHOLD || !hasSearch) {
                return ToolExposureState(tools, lazyMode = false, initialExposedNames = tools.map { it.name }.toSet())
            }
            val registry = runCatching { ToolRegistry.from(tools) }.getOrNull()
            val initial = tools.filter { tool ->
                isResidentTool(tool.name, registry?.metadataFor(tool.name)?.category)
            }.map { it.name }.toSet()
            return ToolExposureState(tools, lazyMode = true, initialExposedNames = initial)
        }

        fun isResidentTool(name: String, category: ToolCategory?): Boolean = when {
            name in DISCOVERY_UTILITY_TOOLS -> true
            name in RESIDENT_EXACT_TOOLS -> true
            category == ToolCategory.FILE_SYSTEM -> true
            category == ToolCategory.SHELL -> true
            category == ToolCategory.CONVERSATION -> true
            category == ToolCategory.MEMORY -> true
            else -> false
        }

        private val DISCOVERY_UTILITY_TOOLS = setOf(
            TOOL_SEARCH_TOOL_NAME,
            TOOLS_LIST_TOOL_NAME,
        )

        private val RESIDENT_EXACT_TOOLS = setOf(
            "ask_user",
            "search_web",
            "scrape_web",
            "get_time_info",
            "use_skill",
            "skills_list",
            "model_council",
        )
    }
}

private fun UIMessagePart.Tool.expandedToolNames(): List<String> =
    output.filterIsInstance<UIMessagePart.Text>().flatMap { part ->
        runCatching {
            val payload = toolSearchJson.parseToJsonElement(part.text) as? JsonObject
                ?: return@runCatching emptyList()
            val names = payload["expanded_tools"] as? JsonArray ?: return@runCatching emptyList()
            names.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }.getOrDefault(emptyList())
    }

private fun Tool.schemaFootprintChars(): Int =
    name.length + description.length + (parameters()?.toString()?.length ?: 0)

private fun stringProp(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}
