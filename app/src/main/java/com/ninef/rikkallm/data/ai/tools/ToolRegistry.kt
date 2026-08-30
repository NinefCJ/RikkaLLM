package com.ninef.rikkallm.data.ai.tools

import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.Tool

/**
 * 工具在统一注册表中的元数据。
 *
 * 参照 AmberAgent 的 ToolMetadata，把 RikkaHub 现有的 [Tool.invocationPolicy]
 * （风险/类别/副作用声明）与工具自身的 `needsApproval` 声明汇总为一份可查询的元数据，
 * 供工具目录（tools_list）、工具搜索（tool_search）与 UI 展示使用。
 */
data class ToolMetadata(
    val name: String,
    val description: String,
    val category: ToolCategory,
    val risk: ToolRisk,
    val mutates: Boolean,
    val sensitiveRead: Boolean,
    val needsApproval: Boolean,
    val autoApprovable: Boolean,
    val mandatoryApproval: Boolean,
    val outputBudgetChars: Int,
)

/**
 * 统一工具注册表。
 *
 * 汇总当前会话所有可用工具并补齐元数据，同时提供：
 * - 重名校验（同一会话不允许注册两个同名工具）
 * - 按名称查询工具定义与元数据
 * - 完整工具列表 / 元数据列表导出
 *
 * 由于 RikkaHub 的 [Tool] 数据类包含 `@Serializable` 与 lambda 字段，
 * 注册表不修改工具对象本身，仅做查询封装。
 */
class ToolRegistry private constructor(
    private val entries: Map<String, ToolRegistryEntry>,
) {
    /** 全部已注册工具的元数据（按注册顺序） */
    val metadata: List<ToolMetadata>
        get() = entries.values.map { it.metadata }

    /** 全部已注册工具定义 */
    fun tools(): List<Tool> = entries.values.map { it.tool }

    /** 按名称查询工具定义 */
    fun toolFor(name: String): Tool? = entries[name]?.tool

    /** 按名称查询元数据 */
    fun metadataFor(name: String): ToolMetadata? = entries[name]?.metadata

    private data class ToolRegistryEntry(
        val tool: Tool,
        val metadata: ToolMetadata,
    )

    companion object {
        const val DEFAULT_TOOL_OUTPUT_BUDGET_CHARS = 80_000

        /**
         * 从工具列表构建注册表。
         *
         * @throws IllegalArgumentException 存在重名工具
         */
        fun from(tools: List<Tool>): ToolRegistry {
            val entries = linkedMapOf<String, ToolRegistryEntry>()
            tools.forEach { tool ->
                require(tool.name !in entries) {
                    "Duplicate tool name registered in ToolRegistry: ${tool.name}"
                }
                entries[tool.name] = ToolRegistryEntry(tool, tool.toMetadata())
            }
            return ToolRegistry(entries)
        }

        private fun Tool.toMetadata(): ToolMetadata {
            val policy = invocationPolicy()
            return ToolMetadata(
                name = name,
                description = description,
                category = policy.category,
                risk = policy.risk,
                mutates = policy.mutates,
                sensitiveRead = policy.category == ToolCategory.CLIPBOARD || policy.category == ToolCategory.SCREEN,
                needsApproval = policy.mandatoryApproval ||
                    policy.alwaysAsk ||
                    policy.risk == ToolRisk.HIGH ||
                    needsApproval(JsonObject(emptyMap())),
                autoApprovable = policy.autoApprovable,
                mandatoryApproval = policy.mandatoryApproval,
                outputBudgetChars = DEFAULT_TOOL_OUTPUT_BUDGET_CHARS,
            )
        }
    }
}
