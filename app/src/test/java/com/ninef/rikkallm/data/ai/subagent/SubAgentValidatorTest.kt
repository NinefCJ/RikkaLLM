package com.ninef.rikkallm.data.ai.subagent

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SubAgentValidatorTest {

    private fun config(
        role: String = "researcher",
        task: String = "summarize the docs",
        allowDynamicRoles: Boolean = false,
    ) = SubAgentConfig(
        role = role,
        task = task,
        allowDynamicRoles = allowDynamicRoles,
        model = Model(),
        provider = ProviderSetting.OpenAI(),
    )

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ---- 角色白名单 ----

    @Test
    fun `builtin roles pass validation`() {
        for (role in SubAgentValidator.BUILTIN_ROLES) {
            val validated = SubAgentValidator.validate(config(role = role))
            assertEquals(role, validated.role)
        }
    }

    @Test
    fun `dynamic role rejected by default`() {
        assertThrows { SubAgentValidator.validate(config(role = "custom")) }
    }

    @Test
    fun `dynamic role allowed with flag`() {
        val validated = SubAgentValidator.validate(config(role = "custom", allowDynamicRoles = true))
        assertEquals("custom", validated.role)
    }

    // ---- 任务边界 ----

    @Test
    fun `empty task rejected`() {
        assertThrows { SubAgentValidator.validate(config(task = "  ")) }
    }

    @Test
    fun `task too long rejected`() {
        assertThrows {
            SubAgentValidator.validate(config(task = "x".repeat(SubAgentValidator.MAX_TASK_CHARS + 1)))
        }
    }

    @Test
    fun `empty role rejected`() {
        assertThrows { SubAgentValidator.validate(config(role = " ")) }
    }

    @Test
    fun `system prompt too long rejected`() {
        assertThrows {
            SubAgentValidator.validate(
                config().copy(systemPrompt = "y".repeat(SubAgentValidator.MAX_SYSTEM_PROMPT_CHARS + 1)),
            )
        }
    }

    @Test
    fun `max output chars must be positive`() {
        assertThrows { SubAgentValidator.validate(config().copy(maxOutputChars = 0)) }
    }

    @Test
    fun `validation trims role and task`() {
        val validated = SubAgentValidator.validate(config(role = "  researcher ", task = "  do it  "))
        assertEquals("researcher", validated.role)
        assertEquals("do it", validated.task)
    }

    // ---- tool_profile 解析 ----

    @Test
    fun `tool profile from wire name`() {
        assertEquals(ToolProfile.NONE, ToolProfile.fromWireName(null))
        assertEquals(ToolProfile.NONE, ToolProfile.fromWireName(""))
        assertEquals(ToolProfile.NONE, ToolProfile.fromWireName("NONE"))
        assertEquals(ToolProfile.READ_ONLY, ToolProfile.fromWireName("read_only"))
        assertEquals(ToolProfile.WORKSPACE_READ, ToolProfile.fromWireName("workspace_read"))
        assertEquals(ToolProfile.WEB_READ, ToolProfile.fromWireName("web_read"))
        assertEquals(ToolProfile.HISTORY_READ, ToolProfile.fromWireName("history_read"))
        assertNull(ToolProfile.fromWireName("bogus"))
    }

    // ---- tool_allowlist ----

    @Test
    fun `allowlist is trimmed and deduplicated`() {
        val cleaned = SubAgentValidator.validateToolAllowlist(listOf(" a ", "a", "", " b ", "b", "a"))
        assertEquals(listOf("a", "b"), cleaned)
    }

    @Test
    fun `allowlist rejects names with spaces`() {
        assertThrows { SubAgentValidator.validateToolAllowlist(listOf("bad name")) }
    }
}
