package com.ninef.rikkallm.data.deepread

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.datastore.Settings
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.datastore.findProvider
import com.ninef.rikkallm.data.datastore.getCurrentChatModel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

/**
 * 深度阅读执行器：基于给定材料（URL 或粘贴文本），通过分阶段 LLM 管线生成结构化深度阅读报告。
 *
 * 管线：获取材料 → 推断标题 → 规划章节大纲 → 逐章撰写 → 提炼核心观点与论据 → 生成摘要。
 * 复用 [ProviderManager] 的 [me.rerere.ai.provider.LLMProvider.generateText] 进行每次补全。
 */
class DeepReadRunner(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    suspend fun run(
        request: DeepReadRequest,
        onStage: (String) -> Unit = {},
    ): DeepReadReport {
        val settings: Settings = settingsStore.settingsFlow.first()
        val model: Model = settings.getCurrentChatModel()
            ?: throw IllegalStateException("深度阅读：请先在设置中配置一个聊天模型")
        val provider: ProviderSetting = model.findProvider(settings.providers)
            ?: throw IllegalStateException("深度阅读：未找到当前模型的 Provider 配置")

        // 解析材料
        onStage("正在获取材料…")
        val material = if (request.materialUrl.isNotBlank()) {
            fetchUrl(request.materialUrl)
        } else {
            request.materialText
        }.trim()
        if (material.isBlank()) throw IllegalStateException("深度阅读：材料为空，请提供 URL 或文本")
        val source = if (request.materialUrl.isNotBlank()) request.materialUrl else "粘贴文本"

        // 标题
        val title = request.title.ifBlank {
            onStage("正在推断标题…")
            generate(model, provider,
                "你是一名编辑。根据给定材料，提取或概括一个简洁的标题（不超过 20 字），只输出标题本身，不要解释。语言：${request.language}。",
                material.take(4000),
            ).trim().trim('"', '「', '」', '【', '】', '"')
        }.ifBlank { "深度阅读报告" }

        // 大纲
        onStage("正在规划章节大纲…")
        val outlineRaw = generate(
            model, provider,
            "你是一名深度阅读分析师。请基于给定材料，规划一份深度阅读报告的章节大纲。" +
                "只输出一个 JSON 字符串数组，每个元素是一个章节标题（字符串），不要输出其它内容。语言：${request.language}。",
            material.take(8000),
        )
        val sectionTitles = parseStringArray(outlineRaw)
            .ifEmpty { listOf("概述", "核心内容", "要点分析", "结论") }

        // 逐章撰写
        onStage("正在撰写各章节（共 ${sectionTitles.size} 节）…")
        val sections = sectionTitles.mapIndexed { _, t ->
            val content = generate(
                model, provider,
                "你是深度阅读作者，正在撰写报告章节「$t」。" +
                    "要求：严格基于下方【材料】，不得编造材料之外的事实；关键论断请引用原文（用 > 引用块）；" +
                    "语言：${request.language}；详实但不啰嗦。",
                "【材料】\n$material\n\n请撰写章节：$t",
            )
            DeepReadSection(title = t, content = content)
        }

        // 核心观点 + 论据
        onStage("正在提炼核心观点与论据…")
        val keRaw = generate(
            model, provider,
            "你是一名阅读分析助手。请基于材料提取 3-7 条核心观点，并为每条观点附上一条最能支撑它的原文引述。" +
                "只输出 JSON 数组，每个元素为 {\"point\": 观点, \"quote\": 原文引述}。语言：${request.language}。",
            material.take(8000),
        )
        val (keyPoints, evidence) = parseKeyEvidence(keRaw)

        // 摘要
        onStage("正在生成摘要…")
        val summary = generate(
            model, provider,
            "请用 3-5 句话概括这份材料的核心内容，客观、不引申。语言：${request.language}。",
            "【标题】$title\n【章节概要】\n" + sections.joinToString("\n") { "## ${it.title}\n${it.content.take(500)}" },
        )

        return DeepReadReport(
            title = title,
            source = source,
            language = request.language,
            summary = summary,
            sections = sections,
            keyPoints = keyPoints,
            evidence = evidence,
        )
    }

    private suspend fun generate(
        model: Model,
        provider: ProviderSetting,
        system: String,
        user: String,
    ): String = withTimeout(180_000L) {
        val providerImpl = providerManager.getProviderByType(provider)
        val params = TextGenerationParams(
            model = model,
            temperature = 0.3f,
            maxTokens = 4096,
            tools = emptyList(),
        )
        val messages = listOf(UIMessage.system(system), UIMessage.user(user))
        val chunk: MessageChunk = providerImpl.generateText(
            providerSetting = provider,
            messages = messages,
            params = params,
        )
        chunk.choices.firstOrNull()?.message?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("深度阅读：模型返回为空")
    }

    private suspend fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).get().build()
        val body = okHttpClient.newCall(request).execute().use { resp ->
            resp.body?.string().orEmpty()
        }
        return htmlToText(body)
    }

    private fun htmlToText(html: String): String {
        var text = REGEX_SCRIPT_STYLE.matcher(html).replaceAll(" ")
        text = REGEX_TAG.matcher(text).replaceAll(" ")
        text = REGEX_WS.matcher(text).replaceAll(" ")
        return decodeEntities(text).trim()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    private fun parseStringArray(raw: String): List<String> {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonArray
            arr?.mapNotNull { str(it) }?.filter { it.isNotBlank() } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun parseKeyEvidence(raw: String): Pair<List<String>, List<String>> {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList<String>() to emptyList()
        val points = mutableListOf<String>()
        val quotes = mutableListOf<String>()
        runCatching {
            val arr = json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonArray
            arr?.forEach { el ->
                val obj = (el as? JsonObject)?.jsonObject
                str(obj?.get("point"))?.let { points.add(it) }
                str(obj?.get("quote"))?.let { quotes.add(it) }
            }
        }
        return points to quotes
    }

    private fun str(el: JsonElement?): String? = (el as? JsonPrimitive)?.content

    private fun stripCodeFence(raw: String): String {
        val fenced = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL).matcher(raw)
        return if (fenced.find()) fenced.group(1)?.trim().orEmpty() else raw.trim()
    }

    private companion object {
        val REGEX_SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>")
        val REGEX_TAG = Pattern.compile("(?is)<[^>]+>")
        val REGEX_WS = Pattern.compile("[ \\t\\r\\n]+")
    }
}
