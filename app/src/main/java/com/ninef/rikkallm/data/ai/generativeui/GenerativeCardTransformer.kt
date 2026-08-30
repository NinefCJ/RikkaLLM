package com.ninef.rikkallm.data.ai.generativeui

import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.GenerativeCardData
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.ai.transformers.InputMessageTransformer
import com.ninef.rikkallm.data.ai.transformers.OutputMessageTransformer
import com.ninef.rikkallm.data.ai.transformers.TransformerContext

/**
 * 生成式 UI 卡片转换器。
 *
 * 双向职责：
 * - 输入侧（[transform]）：向 system prompt 注入 `buildGenerativeUiPrompt`，
 *   指导模型在合适时机输出 `:::generative-ui` 围栏 JSON。
 * - 输出侧（[onGenerationFinish]）：扫描 assistant 文本中的围栏，解析并清洗
 *   卡片声明，替换为 [UIMessagePart.GenerativeCard] 部件。解析/清洗失败时
 *   保留原始文本，绝不破坏消息。
 *
 * 核心逻辑抽为模块级函数（[injectGenerativeUiPrompt] / [extractGenerativeCards] /
 * [parseGenerativeCard]），便于 JVM 单元测试直测。
 */
object GenerativeCardTransformer : InputMessageTransformer, OutputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> =
        injectGenerativeUiPrompt(messages, modelName = ctx.model.displayName)

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = finishGenerativeCards(messages)
}

/**
 * 输出侧：对每条 assistant 文本消息执行围栏提取；含卡片时重建 parts，
 * 否则原样返回（避免无意义重建）。
 */
internal fun finishGenerativeCards(messages: List<UIMessage>): List<UIMessage> {
    return messages.map { message ->
        if (message.role != MessageRole.ASSISTANT || !message.hasPart<UIMessagePart.Text>()) {
            return@map message
        }

        var foundCard = false
        val newParts = message.parts.flatMap { part ->
            if (part is UIMessagePart.Text) {
                extractGenerativeCards(part.text).also { parts ->
                    if (parts.any { it is UIMessagePart.GenerativeCard }) foundCard = true
                }
            } else {
                listOf(part)
            }
        }
        if (foundCard) message.copy(parts = newParts) else message
    }
}

// ---- 模块级逻辑（可单测） ----

/** 围栏块：`:::generative-ui` 与 `:::` 之间捕获 JSON（容忍 CRLF 与单行写法） */
private val GENERATIVE_UI_BLOCK =
    Regex(""":::generative-ui\s*\r?\n?([\s\S]*?)\r?\n?:::\s*""")

private val cardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 输入侧：向首条 system 消息追加生成式 UI 提示词（无 system 消息时插入新 system 消息）。
 */
internal fun injectGenerativeUiPrompt(
    messages: List<UIMessage>,
    modelName: String?,
): List<UIMessage> {
    val prompt = GenerativeUiPrompt.build(modelName = modelName)

    val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
    return if (systemIndex >= 0) {
        messages.toMutableList().apply {
            this[systemIndex] = this[systemIndex].appendText("\n\n$prompt")
        }
    } else {
        listOf(UIMessage.system(prompt)) + messages
    }
}

/**
 * 输出侧：将文本中的围栏块提取为 [UIMessagePart.GenerativeCard] 部件；
 * 非围栏片段保留为文本，解析/清洗失败的围栏保留原文。
 */
internal fun extractGenerativeCards(text: String): List<UIMessagePart> {
    if (!GENERATIVE_UI_BLOCK.containsMatchIn(text)) return listOf(UIMessagePart.Text(text))

    val result = mutableListOf<UIMessagePart>()
    var cursor = 0
    for (match in GENERATIVE_UI_BLOCK.findAll(text)) {
        if (match.range.first > cursor) {
            result.add(UIMessagePart.Text(text.substring(cursor, match.range.first)))
        }
        val card = parseGenerativeCard(match.groupValues[1])
        if (card != null) {
            result.add(UIMessagePart.GenerativeCard(card))
        } else {
            result.add(UIMessagePart.Text(match.value))
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        result.add(UIMessagePart.Text(text.substring(cursor)))
    }
    return result
}

/** 解析围栏内 JSON 并清洗；任何失败返回 null。 */
internal fun parseGenerativeCard(jsonText: String): GenerativeCardData? {
    if (jsonText.isBlank()) return null
    return runCatching {
        val card = cardJson.decodeFromString<GenerativeCardData>(jsonText)
        GenerativeUiSanitizer.sanitize(card)
    }.getOrNull()
}

private fun UIMessage.appendText(extra: String): UIMessage {
    val updatedParts = parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return copy(parts = updatedParts)
}
