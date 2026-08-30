package com.ninef.rikkallm.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionDecisionResolverTest {

    private fun tool(name: String, needsApproval: Boolean = false) = Tool(
        name = name,
        description = "test tool $name",
        needsApproval = { needsApproval },
        execute = { emptyList() },
    )

    private fun toolCall(
        name: String,
        state: ToolApprovalState = ToolApprovalState.Auto,
    ) = UIMessagePart.Tool(
        toolCallId = "call-1",
        toolName = name,
        input = "{}",
        approvalState = state,
    )

    private fun resolve(
        toolName: String,
        toolDef: Tool?,
        state: ToolApprovalState = ToolApprovalState.Auto,
        autoApproveTools: Boolean = false,
        autoApproveHighRiskTools: Boolean = false,
        autoApprovedToolNames: Set<String> = emptySet(),
    ): PermissionDecision {
        return PermissionDecisionResolver.resolve(
            toolDef = toolDef,
            tool = toolCall(toolName, state),
            autoApproveTools = autoApproveTools,
            autoApproveHighRiskTools = autoApproveHighRiskTools,
            autoApprovedToolNames = autoApprovedToolNames,
        )
    }

    // ---- 1. 工具未注册 ----

    @Test
    fun `unregistered tool is denied`() {
        val decision = resolve(toolName = "unknown_tool", toolDef = null)
        assertEquals(PermissionDecisionType.DENY, decision.type)
        assertEquals(DecisionSource.TOOL_NOT_FOUND, decision.source)
    }

    // ---- 2. 用户已决定 ----

    @Test
    fun `already approved tool is allowed`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
            state = ToolApprovalState.Approved,
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.USER_ALREADY_DECIDED, decision.source)
    }

    @Test
    fun `already denied tool is allowed to resume as user decided`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
            state = ToolApprovalState.Denied("no"),
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
    }

    // ---- 3. HITL 工具 ----

    @Test
    fun `ask_user always asks when it needs approval`() {
        val decision = resolve(
            toolName = "ask_user",
            toolDef = tool("ask_user", needsApproval = true),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
        assertEquals(DecisionSource.HITL, decision.source)
    }

    // ---- 4. 全局全开 ----

    @Test
    fun `unattended mode allows everything`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
            autoApproveTools = true,
            autoApproveHighRiskTools = true,
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.SETTINGS_UNATTENDED, decision.source)
    }

    // ---- 5. 强制审批（eval_javascript） ----

    @Test
    fun `mandatory approval tool is asked even if it declares no approval`() {
        val decision = resolve(
            toolName = "eval_javascript",
            toolDef = tool("eval_javascript", needsApproval = false),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
        assertEquals(DecisionSource.MANDATORY_APPROVAL, decision.source)
    }

    // ---- 6. 工具声明无需审批（向后兼容） ----

    @Test
    fun `read-only tool declaring no approval is allowed`() {
        val decision = resolve(
            toolName = "workspace_read_file",
            toolDef = tool("workspace_read_file", needsApproval = false),
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.TOOL_DECLARED_NO_APPROVAL, decision.source)
    }

    @Test
    fun `write tool declaring no approval is allowed to keep existing behaviour`() {
        val decision = resolve(
            toolName = "workspace_write_file",
            toolDef = tool("workspace_write_file", needsApproval = false),
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
    }

    // ---- 7. 高风险工具 ----

    @Test
    fun `high risk tool asks for approval`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
        assertEquals(DecisionSource.HIGH_RISK, decision.source)
    }

    @Test
    fun `high risk tool is allowed when high risk auto approve enabled`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
            autoApproveHighRiskTools = true,
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.SETTINGS_AUTO_APPROVE, decision.source)
    }

    // ---- 8. run-trust ----

    @Test
    fun `sensitive tool trusted in session is allowed`() {
        val decision = resolve(
            toolName = "calendar_create",
            toolDef = tool("calendar_create", needsApproval = true),
            autoApprovedToolNames = setOf("calendar_create"),
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.RUN_TRUST, decision.source)
    }

    @Test
    fun `high risk tool is not covered by run trust`() {
        val decision = resolve(
            toolName = "workspace_shell",
            toolDef = tool("workspace_shell", needsApproval = true),
            autoApprovedToolNames = setOf("workspace_shell"),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
        assertEquals(DecisionSource.HIGH_RISK, decision.source)
    }

    // ---- 9. 全局自动批准（autoApprovable） ----

    @Test
    fun `auto approvable tool is allowed when global auto approve enabled`() {
        val decision = resolve(
            toolName = "workspace_write_file",
            toolDef = tool("workspace_write_file", needsApproval = true),
            autoApproveTools = true,
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.SETTINGS_AUTO_APPROVE, decision.source)
    }

    // ---- 10. 兜底询问 ----

    @Test
    fun `non auto approvable tool falls back to ask`() {
        val decision = resolve(
            toolName = "memory_tool",
            toolDef = tool("memory_tool", needsApproval = true),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
        assertEquals(DecisionSource.UI_CONFIRMATION, decision.source)
    }

    // ---- 11. MCP 默认策略 ----

    @Test
    fun `mcp tool follows its own declaration`() {
        val decision = resolve(
            toolName = "mcp__server__query",
            toolDef = tool("mcp__server__query", needsApproval = false),
        )
        assertEquals(PermissionDecisionType.ALLOW, decision.type)
        assertEquals(DecisionSource.TOOL_DECLARED_NO_APPROVAL, decision.source)
    }

    @Test
    fun `mcp tool declaring approval asks`() {
        val decision = resolve(
            toolName = "mcp__server__write",
            toolDef = tool("mcp__server__write", needsApproval = true),
        )
        assertEquals(PermissionDecisionType.ASK, decision.type)
    }

    // ---- 策略注册表 ----

    @Test
    fun `built-in policies are registered`() {
        assertEquals(ToolRisk.HIGH, tool("workspace_shell").invocationPolicy().risk)
        assertEquals(ToolRisk.NORMAL, tool("workspace_read_file").invocationPolicy().risk)
        assertEquals(ToolRisk.SENSITIVE, tool("memory_tool").invocationPolicy().risk)
        assertEquals(ToolCategory.HITL, tool("ask_user").invocationPolicy().category)
        assert(tool("ask_user").invocationPolicy().alwaysAsk)
        assert(tool("eval_javascript").invocationPolicy().mandatoryApproval)
    }
}
