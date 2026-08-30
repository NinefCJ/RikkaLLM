package com.ninef.rikkallm.data.ai.subagent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart

const val SUBAGENT_START_TOOL_NAME = "subagent_start"
const val SUBAGENT_LIST_TOOL_NAME = "subagent_list"
const val SUBAGENT_RESULT_TOOL_NAME = "subagent_result"

/**
 * 构建子智能体三件套工具（subagent_start / subagent_list / subagent_result）。
 *
 * [model] / [provider] 来自当前调用方会话上下文，用于子智能体复用同一模型补全。
 */
fun buildSubAgentTools(
    manager: SubAgentManager,
    model: Model,
    provider: ProviderSetting,
): List<Tool> = listOf(
    Tool(
        name = SUBAGENT_START_TOOL_NAME,
        description = "启动一个后台子智能体执行一次独立的受限推理任务，立即返回 run_id；" +
            "随后用 $SUBAGENT_RESULT_TOOL_NAME 查询结果（可轮询）。可并行启动多个子智能体。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("role", buildJsonObject {
                        put("type", "string")
                        put("description", "子智能体角色，内置：${SubAgentValidator.BUILTIN_ROLES.sorted().joinToString("/")}")
                        put("enum", buildJsonArray {
                            SubAgentValidator.BUILTIN_ROLES.sorted().forEach { add(it) }
                        })
                    })
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "交给子智能体的具体任务描述")
                    })
                    put("system_prompt", buildJsonObject {
                        put("type", "string")
                        put("description", "附加系统提示（可选），追加到角色基座提示之后")
                    })
                    put("tool_profile", buildJsonObject {
                        put("type", "string")
                        put("description", "工具访问档案：${ToolProfile.entries.joinToString("/") { it.wireName }}")
                        put("enum", buildJsonArray {
                            ToolProfile.entries.forEach { add(it.wireName) }
                        })
                    })
                    put("tool_allowlist", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "允许使用的工具名白名单（可选）")
                    })
                    put("allow_dynamic_roles", buildJsonObject {
                        put("type", "boolean")
                        put("description", "是否允许内置白名单之外的自定义角色（默认 false）")
                    })
                    put("max_output_chars", buildJsonObject {
                        put("type", "integer")
                        put("description", "输出预算（字符），默认 $DEFAULT_SUBAGENT_OUTPUT_BUDGET_CHARS")
                    })
                    put("max_tokens", buildJsonObject {
                        put("type", "integer")
                        put("description", "生成 token 上限（可选）")
                    })
                },
                required = listOf("role", "task"),
            )
        },
        needsApproval = { true },
        execute = { input ->
            val obj = input as? JsonObject ?: return@Tool emptyList()
            val payload = runCatching {
                val role = obj["role"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val task = obj["task"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val systemPrompt = obj["system_prompt"]?.jsonPrimitive?.contentOrNull
                val profile = ToolProfile.fromWireName(obj["tool_profile"]?.jsonPrimitive?.contentOrNull)
                    ?: throw IllegalArgumentException(
                        "Unknown tool_profile. Allowed: " +
                            ToolProfile.entries.joinToString("/") { it.wireName },
                    )
                val allowlist = obj["tool_allowlist"]?.jsonArray
                    ?.map { it.jsonPrimitive.contentOrNull.orEmpty() }
                    .orEmpty()
                val allowDynamicRoles =
                    obj["allow_dynamic_roles"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val maxOutputChars = obj["max_output_chars"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: DEFAULT_SUBAGENT_OUTPUT_BUDGET_CHARS
                val maxTokens = obj["max_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                val config = SubAgentConfig(
                    role = role,
                    task = task,
                    systemPrompt = systemPrompt,
                    toolProfile = profile,
                    toolAllowlist = SubAgentValidator.validateToolAllowlist(allowlist),
                    allowDynamicRoles = allowDynamicRoles,
                    maxOutputChars = maxOutputChars,
                    maxTokens = maxTokens,
                    model = model,
                    provider = provider,
                )
                val run = manager.launch(config)
                buildJsonObject {
                    put("run_id", run.runId)
                    put("status", "running")
                    put("role", run.config.role)
                    put("task", run.config.task)
                    put("tip", "Use $SUBAGENT_RESULT_TOOL_NAME with run_id to fetch the result.")
                }.toString()
            }.getOrElse { e ->
                buildJsonObject {
                    put("error", e.message ?: "Failed to start sub-agent")
                }.toString()
            }
            listOf(UIMessagePart.Text(payload))
        },
    ),
    Tool(
        name = SUBAGENT_LIST_TOOL_NAME,
        description = "列出最近启动的子智能体运行记录（含状态、角色、耗时）。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "返回条数上限，默认 10，最大 $MAX_SUBAGENT_RUNS")
                    })
                },
            )
        },
        needsApproval = { false },
        execute = { input ->
            val obj = input as? JsonObject
            val limit = obj?.get("limit")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
            val runs = manager.listRuns(limit)
            val payload = buildJsonObject {
                put("runs", buildJsonArray {
                    runs.forEach { run ->
                        add(buildJsonObject {
                            put("run_id", run.runId)
                            put("role", run.config.role)
                            put("status", run.status.name.lowercase())
                            put("task_preview", run.config.task.take(120))
                            run.durationMs?.let { put("duration_ms", it) }
                        })
                    }
                })
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    ),
    Tool(
        name = SUBAGENT_RESULT_TOOL_NAME,
        description = "按 run_id 查询子智能体运行结果。仍在运行则返回当前状态；完成则包含输出文本。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("run_id", buildJsonObject {
                        put("type", "string")
                        put("description", "subagent_start 返回的 run_id")
                    })
                },
                required = listOf("run_id"),
            )
        },
        needsApproval = { false },
        execute = { input ->
            val obj = input as? JsonObject
            val runId = obj?.get("run_id")?.jsonPrimitive?.contentOrNull.orEmpty()
            val run = manager.getRun(runId)
            val payload = buildJsonObject {
                if (run == null) {
                    put("error", "run not found: $runId")
                } else {
                    put("run_id", run.runId)
                    put("role", run.config.role)
                    put("status", run.status.name.lowercase())
                    put("task", run.config.task)
                    run.durationMs?.let { put("duration_ms", it) }
                    run.output?.let { put("output", it) }
                    run.error?.let { put("error", it) }
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        },
    ),
)
