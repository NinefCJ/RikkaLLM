package me.rerere.ai.provider.providers

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient

/**
 * 本地大模型服务适配层。
 *
 * 设计要点（零网络层重复、原有功能 100% 不受影响）：
 * 众多本地推理服务（Mobile LLM Server、LM Studio、Ollama、llama.cpp server、GPT4All 等）
 * 都以 OpenAI 兼容的 Chat Completions 协议对外暴露 HTTP 接口，且底层模型格式各不相同
 * （GGUF / MNN / Safetensors）。它们之间真正的"差异"只是地址与鉴权习惯，协议完全统一。
 * 因此本适配器不做任何请求/解析的重复实现，而是把 [ProviderSetting.LocalServer] 就地投影为
 * 一个等价的 [ProviderSetting.OpenAI]，再直接委托给久经考验的 [OpenAIProvider]。
 * 这就是不同模型来源之间统一的"转换层"——无论本地服务基于哪种格式，只要说出 OpenAI 兼容
 * 协议，即可无缝复用现有的流式生成、工具调用、嵌入、模型列举与图像生成等全部能力。
 */
class LocalServerProvider(
    private val okHttpClient: OkHttpClient,
    private val context: Context,
) : Provider<ProviderSetting.LocalServer> {

    private val delegate = OpenAIProvider(okHttpClient, context)

    /** 把本地服务设置投影为等价的 OpenAI 设置——适配层的核心转换逻辑 */
    private fun ProviderSetting.LocalServer.toOpenAI(): ProviderSetting.OpenAI =
        ProviderSetting.OpenAI(
            id = id,
            enabled = enabled,
            name = name,
            models = models,
            balanceOption = balanceOption,
            builtIn = builtIn,
            description = description,
            shortDescription = shortDescription,
            apiKey = apiKey,
            baseUrl = baseUrl,
            chatCompletionsPath = chatCompletionsPath,
            useResponseApi = useResponseApi,
            includeHistoryReasoning = includeHistoryReasoning,
        )

    override suspend fun listModels(providerSetting: ProviderSetting.LocalServer): List<Model> =
        delegate.listModels(providerSetting.toOpenAI())

    /** 本地服务通常没有账单/额度概念，返回不适用占位符而非默认 "TODO" */
    override suspend fun getBalance(providerSetting: ProviderSetting.LocalServer): String = "—"

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalServer,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = delegate.generateText(providerSetting.toOpenAI(), messages, params)

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalServer,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = delegate.streamText(providerSetting.toOpenAI(), messages, params)

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalServer,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult =
        delegate.generateEmbedding(providerSetting.toOpenAI(), params)

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): Flow<ImageGenerationItem> =
        delegate.generateImage(projectForImages(providerSetting), params)

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): Flow<ImageGenerationItem> =
        delegate.editImage(projectForImages(providerSetting), params)

    /** 图像接口接收基类设置，需先投影为 OpenAI 才能交给委托方 */
    private fun projectForImages(providerSetting: ProviderSetting): ProviderSetting =
        (providerSetting as? ProviderSetting.LocalServer)?.toOpenAI() ?: providerSetting
}
