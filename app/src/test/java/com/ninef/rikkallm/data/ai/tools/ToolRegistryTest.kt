package com.ninef.rikkallm.data.ai.tools

import me.rerere.ai.core.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolRegistryTest {

    private fun tool(name: String, needsApproval: Boolean = false) = Tool(
        name = name,
        description = "test tool $name",
        needsApproval = { needsApproval },
        execute = { emptyList() },
    )

    // ---- 1. 重名校验 ----

    @Test
    fun `duplicate tool name throws`() {
        try {
            ToolRegistry.from(listOf(tool("workspace_read_file"), tool("workspace_read_file")))
            fail("Expected IllegalArgumentException for duplicate tool name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Duplicate tool name"))
        }
    }

    // ---- 2. 元数据聚合（来自 invocationPolicy） ----

    @Test
    fun `metadata aggregates built-in policy`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_shell"), tool("workspace_read_file")))
        val shell = registry.metadataFor("workspace_shell")!!
        assertEquals(ToolRisk.HIGH, shell.risk)
        assertEquals(ToolCategory.SHELL, shell.category)
        assertTrue(shell.mutates)
        assertTrue(shell.needsApproval)
        assertFalse(shell.autoApprovable)
        assertFalse(shell.sensitiveRead)

        val read = registry.metadataFor("workspace_read_file")!!
        assertEquals(ToolRisk.NORMAL, read.risk)
        assertEquals(ToolCategory.FILE_SYSTEM, read.category)
        assertFalse(read.mutates)
        assertFalse(read.needsApproval)
        assertTrue(read.autoApprovable)
    }

    @Test
    fun `sensitive read is derived from clipboard and screen categories`() {
        val registry = ToolRegistry.from(
            listOf(tool("clipboard_tool"), tool("get_screen_time"), tool("calendar_query"))
        )
        assertTrue(registry.metadataFor("clipboard_tool")!!.sensitiveRead)
        assertTrue(registry.metadataFor("get_screen_time")!!.sensitiveRead)
        assertFalse(registry.metadataFor("calendar_query")!!.sensitiveRead)
    }

    @Test
    fun `mandatory approval and always ask are reflected in needs approval`() {
        val registry = ToolRegistry.from(listOf(tool("eval_javascript"), tool("ask_user")))
        val js = registry.metadataFor("eval_javascript")!!
        assertTrue(js.mandatoryApproval)
        assertTrue(js.needsApproval)
        assertFalse(js.autoApprovable)
        assertTrue(registry.metadataFor("ask_user")!!.needsApproval)
    }

    @Test
    fun `tool declared needs approval is aggregated`() {
        val registry = ToolRegistry.from(listOf(tool("workspace_read_file", needsApproval = true)))
        assertTrue(registry.metadataFor("workspace_read_file")!!.needsApproval)
    }

    // ---- 3. 查询与顺序 ----

    @Test
    fun `tool for and metadata for queries`() {
        val registry = ToolRegistry.from(listOf(tool("memory_tool")))
        assertEquals("memory_tool", registry.toolFor("memory_tool")?.name)
        assertEquals(ToolCategory.MEMORY, registry.metadataFor("memory_tool")?.category)
        assertNull(registry.toolFor("missing"))
        assertNull(registry.metadataFor("missing"))
    }

    @Test
    fun `tools preserves registration order`() {
        val registry = ToolRegistry.from(listOf(tool("b_tool"), tool("a_tool"), tool("c_tool")))
        assertEquals(listOf("b_tool", "a_tool", "c_tool"), registry.tools().map { it.name })
        assertEquals(listOf("b_tool", "a_tool", "c_tool"), registry.metadata.map { it.name })
    }

    // ---- 4. 未知工具保守策略 ----

    @Test
    fun `unknown tool gets conservative policy`() {
        val registry = ToolRegistry.from(listOf(tool("custom_tool")))
        val meta = registry.metadataFor("custom_tool")!!
        assertEquals(ToolRisk.SENSITIVE, meta.risk)
        assertEquals(ToolCategory.UNKNOWN, meta.category)
        assertTrue(meta.mutates)
        // SENSITIVE 不等于 HIGH，且工具自身未声明审批 → 不强制审批
        assertFalse(meta.needsApproval)
    }

    @Test
    fun `mcp tool gets mcp policy`() {
        val registry = ToolRegistry.from(listOf(tool("mcp__server__query")))
        val meta = registry.metadataFor("mcp__server__query")!!
        assertEquals(ToolCategory.MCP, meta.category)
        assertTrue(meta.mutates)
    }
}
