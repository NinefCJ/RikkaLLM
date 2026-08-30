package com.ninef.rikkallm.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchTest {

    private fun tool(name: String) = Tool(
        name = name,
        description = "test tool $name",
        execute = { emptyList() },
    )

    /** 构造包含 tool_search / tools_list 的完整工具集 */
    private fun completeTools(tools: List<Tool>): List<Tool> {
        val registry = ToolRegistry.from(tools)
        return tools + createToolSearchTool(registry) + createToolsListTool(registry)
    }

    private fun expandedNames(payload: JsonObject): List<String> =
        payload["expanded_tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    private fun rankedNames(payload: JsonObject): List<String> =
        payload["tools"]?.jsonArray?.map { it.jsonObject["name"]!!.jsonPrimitive.content } ?: emptyList()

    private fun traceMode(payload: JsonObject): String =
        payload["trace"]?.jsonObject?.get("mode")?.jsonPrimitive?.content ?: ""

    // ---- ToolSearchIndex：打分与排序 ----

    @Test
    fun `exact tool name query ranks first`() {
        val registry = ToolRegistry.from(
            listOf(tool("workspace_shell"), tool("memory_tool"), tool("search_web"))
        )
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload("workspace_shell", null, 10)
        assertEquals("workspace_shell", rankedNames(payload).first())
    }

    @Test
    fun `chinese alias query matches shell tool`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_shell"), tool("memory_tool")))
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload("终端", null, 10)
        assertTrue("终端 alias should match workspace_shell", rankedNames(payload).contains("workspace_shell"))
    }

    @Test
    fun `category filter restricts results`() {
        val registry = ToolRegistry.from(
            listOf(tool("workspace_shell"), tool("workspace_read_file"), tool("memory_tool"))
        )
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload("tool", "shell", 10)
        assertEquals(listOf("workspace_shell"), rankedNames(payload))
    }

    @Test
    fun `limit caps returned tools`() {
        val registry = ToolRegistry.from(
            listOf(
                tool("workspace_read_file"),
                tool("workspace_write_file"),
                tool("workspace_edit_file"),
                tool("memory_tool"),
            )
        )
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload("workspace", null, 2)
        assertEquals(2, rankedNames(payload).size)
        assertEquals(2, expandedNames(payload).size)
    }

    @Test
    fun `no match returns empty expanded tools and category candidates`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_shell")))
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload("zzzz_not_here", null, 5)
        assertTrue(expandedNames(payload).isEmpty())
        assertNotNull(payload["category_candidates"])
    }

    @Test
    fun `discovery tools are never returned as search hits`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_shell")))
        val index = ToolSearchIndex(registry)
        val payload = index.searchPayload(TOOL_SEARCH_TOOL_NAME, null, 10)
        assertTrue("tool_search itself must not be a hit", rankedNames(payload).none { it == TOOL_SEARCH_TOOL_NAME })
    }

    // ---- ToolSearchIndex：trace 模式 ----

    @Test
    fun `trace mode is bypass under threshold`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_shell"), tool("memory_tool")))
        val index = ToolSearchIndex(registry)
        assertEquals("bypass", traceMode(index.searchPayload("shell", null, 5)))
    }

    @Test
    fun `trace mode is lazy over threshold`() {
        val tools = (1..41).map { tool("custom_tool_$it") } + tool("workspace_shell")
        val registry = ToolRegistry.from(tools)
        val index = ToolSearchIndex(registry)
        assertEquals("lazy", traceMode(index.searchPayload("custom", null, 5)))
    }

    // ---- tools_list ----

    @Test
    fun `tools list returns catalog without schemas`() {
        val tools = completeTools(listOf(tool("workspace_shell"), tool("memory_tool")))
        val listTool = tools.first { it.name == TOOLS_LIST_TOOL_NAME }
        val output = runBlocking { listTool.execute(JsonObject(emptyMap())) }
        val text = output.single() as UIMessagePart.Text
        val payload = Json.parseToJsonElement(text.text).jsonObject

        assertEquals(2, payload["total_tools"]?.jsonPrimitive?.content?.toInt())
        val catalog = payload["tools"]!!.jsonArray
        assertEquals(2, catalog.size)
        // 目录视图不暴露 schema 字段
        catalog.forEach { entry ->
            val json = entry.jsonObject
            assertFalse(json.containsKey("schema"))
            assertFalse(json.containsKey("parameters"))
        }
        assertEquals(
            listOf("workspace_shell", "memory_tool"),
            catalog.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `tools list supports category filter`() {
        val tools = completeTools(listOf(tool("workspace_shell"), tool("memory_tool")))
        val listTool = tools.first { it.name == TOOLS_LIST_TOOL_NAME }
        val input = buildJsonObject { put("category", "memory") }
        val output = runBlocking { listTool.execute(input) }
        val text = output.single() as UIMessagePart.Text
        val payload = Json.parseToJsonElement(text.text).jsonObject
        assertEquals(
            listOf("memory_tool"),
            payload["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
    }

    // ---- ToolExposureState：bypass ----

    @Test
    fun `bypass mode exposes all tools when under threshold`() {
        val tools = (1..40).map { tool("custom_tool_$it") }
        val state = ToolExposureState.from(tools)
        assertFalse(state.enabled)
        assertEquals(40, state.toolsForStep().size)
    }

    @Test
    fun `bypass mode when no search tool present`() {
        val tools = (1..41).map { tool("custom_tool_$it") }
        val state = ToolExposureState.from(tools)
        assertFalse(state.enabled)
        assertEquals(41, state.toolsForStep().size)
    }

    // ---- ToolExposureState：lazy ----

    @Test
    fun `lazy mode only exposes resident tools initially`() {
        val tools = completeTools((1..41).map { tool("custom_tool_$it") } + tool("workspace_read_file"))
        val state = ToolExposureState.from(tools)
        assertTrue(state.enabled)
        val exposed = state.toolsForStep().map { it.name }.toSet()
        // 常驻：FILE_SYSTEM 工具 + 发现工具自身
        assertTrue(exposed.contains("workspace_read_file"))
        assertTrue(exposed.contains(TOOL_SEARCH_TOOL_NAME))
        assertTrue(exposed.contains(TOOLS_LIST_TOOL_NAME))
        // 非常驻工具不暴露
        assertFalse(exposed.contains("custom_tool_1"))
        assertEquals(3, exposed.size)
    }

    @Test
    fun `resident exact tools stay exposed`() {
        val tools = completeTools(
            (1..41).map { tool("custom_tool_$it") } +
                listOf(
                    tool("ask_user"),
                    tool("search_web"),
                    tool("get_time_info"),
                    tool("use_skill"),
                    tool("scrape_web"),
                )
        )
        val state = ToolExposureState.from(tools)
        val exposed = state.toolsForStep().map { it.name }.toSet()
        assertTrue(
            exposed.containsAll(
                listOf("ask_user", "search_web", "get_time_info", "use_skill", "scrape_web"),
            )
        )
    }

    @Test
    fun `executed tool search expands exposed set`() {
        val tools = completeTools((1..41).map { tool("custom_tool_$it") } + tool("workspace_read_file"))
        val state = ToolExposureState.from(tools)
        assertTrue(state.enabled)

        val payload = buildJsonObject {
            put("expanded_tools", buildJsonArray {
                add("custom_tool_1")
                add("custom_tool_5")
            })
        }
        val executed = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = TOOL_SEARCH_TOOL_NAME,
                input = "{}",
                output = listOf(UIMessagePart.Text(payload.toString())),
            )
        )
        state.observeExecutedTools(executed)

        val exposed = state.toolsForStep().map { it.name }.toSet()
        assertTrue(exposed.contains("custom_tool_1"))
        assertTrue(exposed.contains("custom_tool_5"))
        assertFalse(exposed.contains("custom_tool_2"))
        // 发现工具保持常驻
        assertTrue(exposed.contains(TOOL_SEARCH_TOOL_NAME))
    }

    @Test
    fun `executed non search tool does not expand`() {
        val tools = completeTools((1..41).map { tool("custom_tool_$it") } + tool("workspace_read_file"))
        val state = ToolExposureState.from(tools)
        val executed = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = "workspace_shell",
                input = "{}",
                output = listOf(UIMessagePart.Text("done")),
            )
        )
        state.observeExecutedTools(executed)
        val exposed = state.toolsForStep().map { it.name }.toSet()
        assertFalse(exposed.contains("custom_tool_1"))
    }

    @Test
    fun `malformed search output is ignored`() {
        val tools = completeTools((1..41).map { tool("custom_tool_$it") } + tool("workspace_read_file"))
        val state = ToolExposureState.from(tools)
        val executed = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = TOOL_SEARCH_TOOL_NAME,
                input = "{}",
                output = listOf(UIMessagePart.Text("not json at all")),
            )
        )
        state.observeExecutedTools(executed)
        val exposed = state.toolsForStep().map { it.name }.toSet()
        assertFalse(exposed.contains("custom_tool_1"))
    }

    @Test
    fun `observe is a no-op in bypass mode`() {
        val tools = completeTools((1..10).map { tool("custom_tool_$it") })
        val state = ToolExposureState.from(tools)
        assertFalse(state.enabled)
        val executed = listOf(
            UIMessagePart.Tool(
                toolCallId = "call-1",
                toolName = TOOL_SEARCH_TOOL_NAME,
                input = "{}",
                output = listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("expanded_tools", buildJsonArray { add("custom_tool_1") })
                        }.toString()
                    )
                ),
            )
        )
        state.observeExecutedTools(executed)
        // bypass 模式始终全量暴露
        assertEquals(12, state.toolsForStep().size)
    }
}
