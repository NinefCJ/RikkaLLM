package com.ninef.rikkallm.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart

/**
 * 工具调用的审批决策结果。
 *
 * @property type 决策类型
 * @property reason 人类可读的决策原因
 * @property source 决策来源（用于追踪"为什么放行/为什么询问"）
 */
data class PermissionDecision(
    val type: PermissionDecisionType,
    val reason: String,
    val source: DecisionSource,
)

enum class PermissionDecisionType {
    /** 允许执行 */
    ALLOW,

    /** 需要用户审批/询问 */
    ASK,

    /** 拒绝执行（工具未注册等无法执行的场景） */
    DENY,
}

/** 决策来源，用于追踪与调试 */
enum class DecisionSource {
    TOOL_NOT_FOUND,      // 工具未注册
    USER_ALREADY_DECIDED, // 用户已经批准/拒绝过
    SETTINGS_UNATTENDED,  // 全局自动批准（含高风险）
    HITL,                 // ask_user 等需要用户输入的 HITL 工具
    MANDATORY_APPROVAL,   // 策略强制要求审批
    TOOL_DECLARED_NO_APPROVAL, // 工具自身声明无需审批（保持现有行为）
    HIGH_RISK,            // 高风险工具（shell / eval 等）
    RUN_TRUST,            // 本次会话已信任该工具
    SETTINGS_AUTO_APPROVE, // 全局自动批准（不含高风险）
    UI_CONFIRMATION,      // 兜底：需要用户确认
}

/**
 * 工具权限决策解析器。
 *
 * 参照 AmberAgent 的 PermissionDecisionResolver 决策树，结合 RikkaHub 现有
 * `Tool.needsApproval` 声明实现分级审批：
 *
 * ```
 * 工具固有风险(ToolRisk)
 *   → 全局自动批准设置(autoApproveTools / autoApproveHighRiskTools)
 *   → 会话 run-trust（用户本次会话已信任的工具）
 *   → 工具自身 needsApproval 声明
 *   → 兜底 UI 确认
 * ```
 *
 * 设计要点：
 * - **向后兼容**：工具声明 `needsApproval == false` 且非高风险时直接放行，
 *   与改造前行为完全一致。
 * - **承重墙**：高风险工具（[ToolRisk.HIGH]）无论工具自身如何声明都要求审批，
 *   除非开启全局高风险自动批准——这正是"工具权限审批引擎"的核心安全保证。
 * - **run-trust**：用户批准过的工具在本次会话内自动放行（高风险除外）。
 */
object PermissionDecisionResolver {

    fun resolve(
        toolDef: Tool?,
        tool: UIMessagePart.Tool,
        autoApproveTools: Boolean,
        autoApproveHighRiskTools: Boolean,
        autoApprovedToolNames: Set<String>,
    ): PermissionDecision {
        // 1. 工具未注册：无法执行，拒绝
        if (toolDef == null) {
            return PermissionDecision(
                type = PermissionDecisionType.DENY,
                reason = "Tool '${tool.toolName}' is not registered",
                source = DecisionSource.TOOL_NOT_FOUND,
            )
        }

        // 2. 用户已做出决定（Approved/Denied/Answered）：直接放行
        if (tool.approvalState !is ToolApprovalState.Auto) {
            return PermissionDecision(
                type = PermissionDecisionType.ALLOW,
                reason = "User already decided for '${tool.toolName}'",
                source = DecisionSource.USER_ALREADY_DECIDED,
            )
        }

        val policy = toolDef.invocationPolicy()
        val toolNeedsApproval = toolDef.needsApproval(tool.inputAsJson())

        // 3. HITL 工具（ask_user）：需要用户输入，必须询问
        if (policy.category == ToolCategory.HITL && toolNeedsApproval) {
            return PermissionDecision(
                type = PermissionDecisionType.ASK,
                reason = "Tool '${tool.toolName}' requires user input",
                source = DecisionSource.HITL,
            )
        }

        // 4. 全部自动批准开启：无条件放行
        if (autoApproveTools && autoApproveHighRiskTools) {
            return PermissionDecision(
                type = PermissionDecisionType.ALLOW,
                reason = "Auto-approve all tools enabled",
                source = DecisionSource.SETTINGS_UNATTENDED,
            )
        }

        // 5. 策略强制审批（如 eval_javascript）：即使工具声明无需审批也拦截
        if (policy.mandatoryApproval) {
            return PermissionDecision(
                type = PermissionDecisionType.ASK,
                reason = "Tool '${tool.toolName}' requires mandatory approval",
                source = DecisionSource.MANDATORY_APPROVAL,
            )
        }

        // 6. 工具自身声明无需审批且非高风险：保持现有行为直接放行
        if (!toolNeedsApproval && policy.risk != ToolRisk.HIGH) {
            return PermissionDecision(
                type = PermissionDecisionType.ALLOW,
                reason = "Tool '${tool.toolName}' declares no approval needed",
                source = DecisionSource.TOOL_DECLARED_NO_APPROVAL,
            )
        }

        // 7. 高风险工具（shell / eval）：需审批，除非开启高风险自动批准
        if (policy.risk == ToolRisk.HIGH) {
            if (autoApproveHighRiskTools) {
                return PermissionDecision(
                    type = PermissionDecisionType.ALLOW,
                    reason = "High-risk tool '${tool.toolName}' auto-approved by settings",
                    source = DecisionSource.SETTINGS_AUTO_APPROVE,
                )
            }
            return PermissionDecision(
                type = PermissionDecisionType.ASK,
                reason = "High-risk tool '${tool.toolName}' requires approval",
                source = DecisionSource.HIGH_RISK,
            )
        }

        // 8. run-trust：用户本次会话已批准过该工具 → 自动放行
        if (tool.toolName in autoApprovedToolNames) {
            return PermissionDecision(
                type = PermissionDecisionType.ALLOW,
                reason = "Tool '${tool.toolName}' is trusted in this session",
                source = DecisionSource.RUN_TRUST,
            )
        }

        // 9. 全局自动批准（仅 autoApprovable 工具）
        if (autoApproveTools && policy.autoApprovable) {
            return PermissionDecision(
                type = PermissionDecisionType.ALLOW,
                reason = "Tool '${tool.toolName}' auto-approved by settings",
                source = DecisionSource.SETTINGS_AUTO_APPROVE,
            )
        }

        // 10. 兜底：询问用户
        return PermissionDecision(
            type = PermissionDecisionType.ASK,
            reason = "Tool '${tool.toolName}' requires user confirmation",
            source = DecisionSource.UI_CONFIRMATION,
        )
    }
}
