package com.ninef.rikkallm.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.cliseat.CliSeatConfig
import com.ninef.rikkallm.data.cliseat.CliSeatRunner
import com.ninef.rikkallm.data.datastore.Settings
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.datastore.findProvider

/**
 * 模型议会（Model Council）工具：并发把同一 prompt 发给多个模型，收集各自回答，
 * 再用一个合成模型产出"共识 / 分歧"摘要。
 *
 * 适用场景：需要多模型视角、第二意见，或交叉核对事实时。
 *
 * 实现复用 [ProviderManager] 直接发起非 Agent 补全（与 [com.ninef.rikkallm.data.ai.subagent.SubAgentRunner] 一致），
 * 不对参与模型注入工具循环。每次调用会消耗 N（参与模型）+ 1（合成）次模型调用，
 * 因此默认需用户确认（见 [ToolApprovalPolicy]）。
 */
fun createModelCouncilTool(
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
    defaultModel: Model? = null,
    cliSeats: List<CliSeatConfig> = emptyList(),
    cliSeatRunner: CliSeatRunner? = null,
): Tool = Tool(
    name = MODEL_COUNCIL_TOOL_NAME,
    description = "Convene a Model Council: send the same prompt to several AI models (and optionally external CLI seats like Claude Code / Gemini CLI running inside the proot rootfs) in parallel, collect each seat's answer, then ask a synthesis model to produce a consensus/disagreement summary. Use when you want a multi-model perspective, a second opinion, or to cross-check facts. Costs N+1 model calls, so it asks for confirmation before running.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("prompt", stringProp("Required. The question or task to send to every model in the council."))
                put("models", buildJsonObject {
                    put("type", "array")
                    put(
                        "description",
                        "Optional list of model identifiers to consult. Each entry can be a model id, a model display name, or a CLI seat name (external CLI seats like Claude Code / Gemini CLI configured in settings). If omitted, all configured chat models AND all enabled CLI seats are used. Example: [\"gpt-4o\", \"claude-3-5-sonnet\", \"Claude Code\"].",
                    )
                    put("items", buildJsonObject { put("type", "string") })
                })
                put(
                    "synthesis_model",
                    stringProp("Optional model identifier for the synthesis step that produces the final consensus. Defaults to the current conversation model."),
                )
                put(
                    "focus",
                    stringProp("Optional aspect to focus the synthesis on, e.g. 'security', 'conciseness', 'idiom usage'."),
                )
            },
            required = listOf("prompt"),
        )
    },
    needsApproval = { true },
    execute = { input ->
        val args = input.jsonObject
        val prompt = args["prompt"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text("[model_council] 缺少必填参数 prompt"))

        val settings = settingsStore.settingsFlow.first()
        val allModels = settings.providers.flatMap { it.models }.filter { it.type == ModelType.CHAT }

        val requested = (args["models"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
        val focus = args["focus"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val synthesisToken = args["synthesis_model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val enabledCliSeats = cliSeats.filter { it.enabled }

        val participants: List<CouncilSeat> = if (requested.isNullOrEmpty()) {
            allModels.map { CouncilSeat.Api(it) } + enabledCliSeats.map { CouncilSeat.Cli(it) }
        } else {
            requested.mapNotNull { token -> resolveSeat(token, allModels, enabledCliSeats) }.distinctBy { it.label }
        }

        if (participants.isEmpty()) {
            return@Tool listOf(UIMessagePart.Text("[model_council] 没有可用的席位（聊天模型或 CLI 席位），请先在设置中配置。"))
        }

        val answers = coroutineScope {
            participants.map { seat ->
                async(Dispatchers.IO) {
                    val result = runCatching {
                        withTimeout(PER_MODEL_TIMEOUT_MS) {
                            when (seat) {
                                is CouncilSeat.Api -> callModel(providerManager, seat.model, settings, prompt)
                                is CouncilSeat.Cli -> cliSeatRunner?.runSeat(seat.seat, prompt)
                                    ?: "⚠️ CLI 席位「${seat.label}」未配置运行器（CliSeatRunner 缺失）。"
                            }
                        }
                    }
                    val text = result.getOrElse { "⚠️ 调用失败：${it.message}" }
                    seat to text
                }
            }.awaitAll()
        }

        val apiSeats = participants.filterIsInstance<CouncilSeat.Api>()
        val synthesisModel = synthesisToken?.let { resolveModel(it, allModels) }
            ?: defaultModel?.takeIf { it.type == ModelType.CHAT }
            ?: apiSeats.firstOrNull()?.model

        if (synthesisModel == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    "[model_council] 没有可用的 API 模型用于综合步骤，请配置至少一个聊天模型，或显式指定 synthesis_model。\n\n" +
                        formatCouncilResult(prompt, "", answers, null),
                ),
            )
        }

        val synthesis = runCatching {
            withTimeout(PER_MODEL_TIMEOUT_MS) {
                synthesize(providerManager, synthesisModel, settings, prompt, answers, focus)
            }
        }.getOrElse { "⚠️ 综合步骤失败：${it.message}" }

        listOf(UIMessagePart.Text(formatCouncilResult(prompt, synthesis, answers, synthesisModel)))
    },
)

private const val MODEL_COUNCIL_TOOL_NAME = "model_council"
private const val PER_MODEL_TIMEOUT_MS = 180_000L
private const val PARTICIPANT_MAX_TOKENS = 1500
private const val SYNTHESIS_MAX_TOKENS = 2500

internal fun resolveModel(token: String, all: List<Model>): Model? {
    val t = token.trim()
    return all.firstOrNull { it.id.toString() == t }
        ?: all.firstOrNull { it.modelId == t }
        ?: all.firstOrNull { it.displayName.equals(t, ignoreCase = true) }
}

private suspend fun callModel(
    providerManager: ProviderManager,
    model: Model,
    settings: Settings,
    prompt: String,
): String {
    val provider = model.findProvider(settings.providers)
        ?: throw IllegalStateException("找不到模型 ${model.displayName.ifBlank { model.modelId }} 的提供商配置")
    val providerImpl = providerManager.getProviderByType(provider)
    val params = TextGenerationParams(
        model = model,
        temperature = 0.4f,
        maxTokens = PARTICIPANT_MAX_TOKENS,
        tools = emptyList(),
    )
    val messages = listOf(
        UIMessage.system(
            "你是一个模型议会（Model Council）的参与成员。请基于你自身的能力与知识，独立、直接地回答用户的问题。" +
                "不要提及其他模型或本次议会，只输出你自己的回答，保持简洁。",
        ),
        UIMessage.user(prompt),
    )
    val chunk = providerImpl.generateText(providerSetting = provider, messages = messages, params = params)
    return extractText(chunk)
}

private suspend fun synthesize(
    providerManager: ProviderManager,
    model: Model,
    settings: Settings,
    prompt: String,
    answers: List<Pair<CouncilSeat, String>>,
    focus: String?,
): String {
    val provider = model.findProvider(settings.providers)
        ?: throw IllegalStateException("找不到合成模型 ${model.displayName.ifBlank { model.modelId }} 的提供商配置")
    val providerImpl = providerManager.getProviderByType(provider)
    val params = TextGenerationParams(
        model = model,
        temperature = 0.3f,
        maxTokens = SYNTHESIS_MAX_TOKENS,
        tools = emptyList(),
    )
    val body = buildString {
        appendLine("问题：")
        appendLine(prompt)
        appendLine()
        appendLine("下面汇总了多个席位（可能包括多个 AI 模型与外部 CLI 工具）对同一问题的各自回答：")
        for ((seat, a) in answers) {
            appendLine("【${seat.label}】")
            appendLine(a)
            appendLine()
        }
    }
    val focusLine = if (!focus.isNullOrBlank()) "\n请特别关注以下方面：$focus" else ""
    val messages = listOf(
        UIMessage.system(
            "你是模型议会（Model Council）的主持人 / 综合者。下面汇总了多个席位（可能包括多个 AI 模型与外部 CLI 工具）对同一问题的回答。" +
                "请综合它们的观点，提炼共识，并指出关键分歧与各方案的权衡。用清晰的结构化中文输出，建议包含：\n" +
                "1. 共识\n2. 分歧\n3. 建议 / 你的综合判断$focusLine",
        ),
        UIMessage.user(body),
    )
    val chunk = providerImpl.generateText(providerSetting = provider, messages = messages, params = params)
    return extractText(chunk)
}

private fun extractText(chunk: MessageChunk): String =
    chunk.choices.firstOrNull()?.message?.parts
        ?.filterIsInstance<UIMessagePart.Text>()
        ?.joinToString("") { it.text }
        .orEmpty()

internal fun formatCouncilResult(
    prompt: String,
    synthesis: String,
    answers: List<Pair<CouncilSeat, String>>,
    synthesisModel: Model?,
): String = buildString {
    val synName = synthesisModel?.displayName?.ifBlank { synthesisModel.modelId } ?: "（无，见下方原始回答）"
    appendLine("# 模型议会 · 综合结论")
    appendLine()
    if (synthesis.isNotBlank()) {
        appendLine(synthesis)
        appendLine()
    }
    appendLine("---")
    appendLine("**问题**：$prompt")
    appendLine()
    appendLine("**参与席位（${answers.size}）**，综合模型：`$synName`")
    appendLine()
    appendLine("## 各席位回答")
    for ((seat, a) in answers) {
        appendLine("### ${seat.label}")
        appendLine(a)
        appendLine()
    }
}

/**
 * 议会席位：要么是已配置的 API 聊天模型，要么是外部 CLI 席位（在 proot rootfs 中执行的命令行工具）。
 */
internal sealed interface CouncilSeat {
    val label: String

    data class Api(val model: Model) : CouncilSeat {
        override val label: String get() = model.displayName.ifBlank { model.modelId }
    }

    data class Cli(val seat: CliSeatConfig) : CouncilSeat {
        override val label: String get() = seat.name.ifBlank { seat.id }
    }
}

/**
 * 把一个席位 token 解析为 API 模型或 CLI 席位（按 id / 名称匹配，大小写不敏感）。
 */
private fun resolveSeat(
    token: String,
    all: List<Model>,
    cliSeats: List<CliSeatConfig>,
): CouncilSeat? {
    val t = token.trim()
    resolveModel(t, all)?.let { return CouncilSeat.Api(it) }
    cliSeats.firstOrNull { it.id == t || it.name.equals(t, ignoreCase = true) }
        ?.let { return CouncilSeat.Cli(it) }
    return null
}

private fun stringProp(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}
