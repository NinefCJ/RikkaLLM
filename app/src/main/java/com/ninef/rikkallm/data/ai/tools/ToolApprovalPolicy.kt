package com.ninef.rikkallm.data.ai.tools

import me.rerere.ai.core.Tool

/**
 * 工具风险等级。
 *
 * 参照 AmberAgent 的 ToolRisk，用于把 RikkaHub 原有的单一 `needsApproval: Boolean`
 * 判断升级为分级审批：
 * - [NORMAL]：只读、无副作用或副作用可忽略（如搜索、读文件、查时间）
 * - [SENSITIVE]：有副作用但可控（写文件、改记忆、写剪贴板、创建日历事件）
 * - [HIGH]：执行任意命令/代码（shell、JS 求值）或高风险外部副作用
 */
enum class ToolRisk {
    NORMAL,
    SENSITIVE,
    HIGH,
}

/**
 * 工具功能类别，用于 UI 分组与策略说明。
 */
enum class ToolCategory {
    FILE_SYSTEM,
    SHELL,
    MEMORY,
    NETWORK,
    SKILL,
    CONVERSATION,
    CLIPBOARD,
    CALENDAR,
    SCREEN,
    LOCAL,
    HITL,
    MCP,
    COUNCIL,
    UNKNOWN,
}

/**
 * 工具的静态调用策略。
 *
 * 该模型与 AmberAgent 的 ToolInvocationPolicy 对齐，但保持对 RikkaHub 的最小侵入：
 * 不修改 `@Serializable` 的 [Tool] 数据类，改为通过扩展函数 [Tool.invocationPolicy]
 * 从注册表按工具名查询。
 *
 * @property risk 工具固有风险等级
 * @property category 工具功能类别
 * @property mutates 是否会产生副作用（写入/执行/外部影响）。注意：workspace 沙箱内
 *   的普通文件读写视为工具自身的常规能力，不在此列；真正的高副作用如 shell 为 true。
 * @property mandatoryApproval 无论其它设置如何，只要未开启"全部自动批准"就必须人工审批
 * @property alwaysAsk 总是询问用户（如 ask_user 这类需要用户输入的 HITL 工具）
 * @property autoApprovable 在开启全局自动批准（settings.autoApproveTools）时允许自动放行
 * @property reason 策略说明（供决策追踪与 UI 展示）
 */
data class ToolInvocationPolicy(
    val risk: ToolRisk = ToolRisk.NORMAL,
    val category: ToolCategory = ToolCategory.UNKNOWN,
    val mutates: Boolean = false,
    val mandatoryApproval: Boolean = false,
    val alwaysAsk: Boolean = false,
    val autoApprovable: Boolean = false,
    val reason: String? = null,
)

/**
 * 内置工具策略注册表。
 *
 * 命名约定：
 * - 只读类（搜索/查询/时间等）→ NORMAL + autoApprovable
 * - 有副作用类（写文件、改记忆、剪贴板、日历创建）→ SENSITIVE，不自动放行
 * - 任意代码执行类（shell、eval_javascript）→ HIGH
 * - 需用户输入类（ask_user）→ alwaysAsk
 * - workspace 沙箱内的文件读写保持现有"默认不拦截"行为（mutates = false）
 */
private val BUILTIN_TOOL_POLICIES: Map<String, ToolInvocationPolicy> = mapOf(
    // ---- Workspace：文件读写沙箱内常规操作，保持现有直接执行行为 ----
    "workspace_read_file" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.FILE_SYSTEM,
        autoApprovable = true,
        reason = "Workspace 只读文件",
    ),
    "workspace_write_file" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.FILE_SYSTEM,
        autoApprovable = true,
        reason = "Workspace 写文件",
    ),
    "workspace_edit_file" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.FILE_SYSTEM,
        autoApprovable = true,
        reason = "Workspace 编辑文件",
    ),
    // ---- Shell：任意命令执行，最高风险 ----
    "workspace_shell" to ToolInvocationPolicy(
        risk = ToolRisk.HIGH,
        category = ToolCategory.SHELL,
        mutates = true,
        reason = "执行 Shell 命令",
    ),
    // ---- 记忆 ----
    "memory_tool" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.MEMORY,
        mutates = true,
        reason = "读写长期记忆",
    ),
    // ---- 搜索 / 抓取 ----
    "search_web" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.NETWORK,
        autoApprovable = true,
        reason = "网络搜索",
    ),
    "scrape_web" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.NETWORK,
        autoApprovable = true,
        reason = "抓取网页内容",
    ),
    // ---- 技能 ----
    "use_skill" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.SKILL,
        mutates = true,
        reason = "执行技能（可能包含任意操作）",
    ),
    "skills_list" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.SKILL,
        autoApprovable = true,
        reason = "浏览可用技能目录（只读）",
    ),
    // ---- 模型议会：并发调用多个模型并合成（消耗多次模型调用）----
    "model_council" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.COUNCIL,
        reason = "并发征集多个模型的回答并合成（消耗 N+1 次模型调用）",
    ),
    // ---- 会话查询 ----
    "recent_chats" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.CONVERSATION,
        autoApprovable = true,
        reason = "读取最近会话",
    ),
    "conversation_search" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.CONVERSATION,
        autoApprovable = true,
        reason = "搜索会话历史",
    ),
    // ---- 本地工具 ----
    "get_time_info" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.LOCAL,
        autoApprovable = true,
        reason = "获取时间信息",
    ),
    "text_to_speech" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.LOCAL,
        autoApprovable = true,
        reason = "文本转语音",
    ),
    "eval_javascript" to ToolInvocationPolicy(
        risk = ToolRisk.HIGH,
        category = ToolCategory.LOCAL,
        mutates = true,
        mandatoryApproval = true,
        reason = "求值任意 JavaScript 代码",
    ),
    "calendar_query" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.CALENDAR,
        autoApprovable = true,
        reason = "查询日历事件",
    ),
    "calendar_create" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.CALENDAR,
        mutates = true,
        reason = "创建日历事件",
    ),
    "clipboard_tool" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.CLIPBOARD,
        mutates = true,
        reason = "读写系统剪贴板",
    ),
    "get_screen_time" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.SCREEN,
        reason = "读取屏幕使用时间（隐私数据）",
    ),
    // ---- 人类介入 ----
    "ask_user" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.HITL,
        alwaysAsk = true,
        reason = "向用户提问并等待回答",
    ),
    // ---- 工具发现（只读目录，无副作用） ----
    "tool_search" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.UNKNOWN,
        autoApprovable = true,
        reason = "搜索工具目录并暴露 schema（只读）",
    ),
    "tools_list" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.UNKNOWN,
        autoApprovable = true,
        reason = "浏览工具目录（只读）",
    ),
    // ---- 子智能体编排 ----
    "subagent_start" to ToolInvocationPolicy(
        risk = ToolRisk.SENSITIVE,
        category = ToolCategory.CONVERSATION,
        reason = "启动后台子智能体（消耗模型调用，输出可能进入对话）",
    ),
    "subagent_list" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.CONVERSATION,
        autoApprovable = true,
        reason = "查询子智能体运行列表（只读）",
    ),
    "subagent_result" to ToolInvocationPolicy(
        risk = ToolRisk.NORMAL,
        category = ToolCategory.CONVERSATION,
        autoApprovable = true,
        reason = "查询子智能体运行结果（只读）",
    ),
)

/**
 * MCP 工具的默认策略：外部服务工具，按工具自身声明的 `needsApproval` 决定，
 * 注册表不为其预设风险（保持现有行为）。
 */
internal val MCP_TOOL_PREFIX = "mcp__"

/**
 * 查询工具对应的调用策略。
 *
 * 未在内置注册表中的工具（如 `mcp__*`）返回保守默认策略：
 * - MCP 工具：SENSITIVE 风险，mutates = true，不自动放行，但实际是否拦截
 *   仍由工具自身声明的 `needsApproval` 决定（见 [PermissionDecisionResolver]）。
 * - 其它未知工具：SENSITIVE 风险，同样遵循工具自身声明。
 */
fun Tool.invocationPolicy(): ToolInvocationPolicy {
    BUILTIN_TOOL_POLICIES[name]?.let { return it }
    return if (name.startsWith(MCP_TOOL_PREFIX)) {
        ToolInvocationPolicy(
            risk = ToolRisk.SENSITIVE,
            category = ToolCategory.MCP,
            mutates = true,
            reason = "MCP 外部工具（遵循其自身审批声明）",
        )
    } else {
        ToolInvocationPolicy(
            risk = ToolRisk.SENSITIVE,
            category = ToolCategory.UNKNOWN,
            mutates = true,
            reason = "未注册策略的未知工具（保守处理）",
        )
    }
}
