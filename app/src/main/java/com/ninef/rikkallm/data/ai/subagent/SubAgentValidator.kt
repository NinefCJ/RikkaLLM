package com.ninef.rikkallm.data.ai.subagent

/**
 * 子智能体任务边界 / 输出格式校验。
 *
 * - 角色白名单：内置角色可直接使用；自定义角色需显式开启 allow_dynamic_roles
 * - 任务边界：task 非空且限长，system_prompt 限长
 * - tool_profile：仅接受已知枚举值
 */
object SubAgentValidator {

    /** 内置角色白名单 */
    val BUILTIN_ROLES: Set<String> = setOf(
        "researcher",
        "coder",
        "analyst",
        "planner",
        "summarizer",
        "writer",
    )

    const val MAX_ROLE_CHARS = 64
    const val MAX_TASK_CHARS = 8_000
    const val MAX_SYSTEM_PROMPT_CHARS = 4_000

    /**
     * 校验并规范化配置。校验失败抛 [IllegalArgumentException]，由工具层转为错误信息返回给模型。
     */
    fun validate(config: SubAgentConfig): SubAgentConfig {
        val role = config.role.trim()
        require(role.isNotEmpty()) { "role must not be empty" }
        require(role.length <= MAX_ROLE_CHARS) { "role exceeds $MAX_ROLE_CHARS chars" }
        require(role in BUILTIN_ROLES || config.allowDynamicRoles) {
            "Unknown role '$role'. Allowed roles: ${BUILTIN_ROLES.joinToString(", ")}. " +
                "Set allow_dynamic_roles=true to permit custom roles."
        }

        val task = config.task.trim()
        require(task.isNotEmpty()) { "task must not be empty" }
        require(task.length <= MAX_TASK_CHARS) { "task exceeds $MAX_TASK_CHARS chars" }

        require((config.systemPrompt?.length ?: 0) <= MAX_SYSTEM_PROMPT_CHARS) {
            "system_prompt exceeds $MAX_SYSTEM_PROMPT_CHARS chars"
        }
        require(config.maxOutputChars > 0) { "max_output_chars must be positive" }
        require(config.maxTokens == null || config.maxTokens > 0) { "max_tokens must be positive" }

        return config.copy(role = role, task = task)
    }

    /** 校验 tool_allowlist 工具名非空且无重复 */
    fun validateToolAllowlist(allowlist: List<String>): List<String> {
        val cleaned = allowlist.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        cleaned.forEach { name ->
            require(!name.contains(' ')) { "Invalid tool name in allowlist: '$name'" }
        }
        return cleaned
    }
}
