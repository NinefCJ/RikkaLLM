package com.ninef.rikkallm.data.huggingface

import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import com.ninef.rikkallm.data.mnn.CatalogModel
import com.ninef.rikkallm.data.mnn.ModelSource
import com.ninef.rikkallm.data.model.Assistant

/** 内置 "MNN 本地模型" provider 中 mnn-local 模型条目的 id */
val MNN_LOCAL_MODEL_ID = Uuid.parse("4e7a1f2c-8b93-46d5-a0c1-2f6e8d4b9a73")

internal val previewJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

internal inline fun <reified T> T.toPreviewJson(): String = previewJson.encodeToString(this)

/**
 * 将 HF 模型转换为可直接下载的 MNN CatalogModel。
 * 仅对 [HfModel.isMnnFormat] 为 true 的模型有效。
 */
fun HfModel.toCatalogModel(source: ModelMarketSource = ModelMarketSource.HUGGINGFACE): CatalogModel {
    val mnnFile = tags.firstOrNull { it.endsWith(".mnn", ignoreCase = true) } ?: "${displayName}.mnn"
    val ref = cardData?.get("base_model")?.toString()?.trim('"', '[', ']', ' ')?.takeIf { it.isNotBlank() }
        ?: "main"
    val (modelSource, sourceLabel) = when (source) {
        ModelMarketSource.MODELSCOPE -> ModelSource.ModelScope(repo = repoId, ref = "master") to "魔搭社区"
        else -> ModelSource.HuggingFace(repo = repoId, ref = ref) to "HuggingFace"
    }
    return CatalogModel(
        id = repoId,
        name = displayName,
        description = "来自 $sourceLabel 的 MNN 格式模型：$repoId",
        version = ref,
        source = modelSource,
        files = listOf("config.json", mnnFile, "tokenizer.json"),
        fileSizes = emptyMap(),
        minRamMb = estimateMinRamMb(),
    )
}

/** 为 MNN 格式模型生成指向内置 MNN 供应商的助手（加载后即可对话） */
fun HfModel.toMnnAssistant(): Assistant = Assistant(
    name = displayName,
    chatModelId = MNN_LOCAL_MODEL_ID,
    systemPrompt = "你是由 $repoId 驱动的本地模型，运行于设备端 MNN 引擎，完全离线、无需联网。",
)

/** 非 MNN 格式模型的自动配置结果 */
data class LocalServerConfig(
    val provider: ProviderSetting.LocalServer,
    val assistant: Assistant,
)

/**
 * 为非 MNN 格式模型生成 LocalServer 供应商 + 助手。
 * 用户只需在桌面端启动兼容 OpenAI 协议的本地服务（Ollama / LM Studio / llama.cpp），
 * 再把供应商地址改为本机 IP 即可对话，无需手动编写配置。
 */
fun HfModel.toLocalServerConfig(): LocalServerConfig {
    val modelUid = Uuid.random()
    val providerUid = Uuid.random()
    val model = Model(
        id = modelUid,
        modelId = displayName,
        displayName = displayName,
        inputModalities = inferInputModalities(),
        outputModalities = inferOutputModalities(),
    )
    val provider = ProviderSetting.LocalServer(
        id = providerUid,
        name = "本地: $displayName",
        baseUrl = "http://192.168.1.100:8080/v1",
        apiKey = "",
        enabled = false,
        builtIn = false,
        models = listOf(model),
    )
    val assistant = Assistant(
        name = displayName,
        chatModelId = modelUid,
        systemPrompt = "你是由 $repoId 驱动的模型，通过本地推理服务运行。请在桌面端启动对应的本地服务（Ollama / LM Studio / llama.cpp）并将供应商地址改为本机 IP 后使用。",
    )
    return LocalServerConfig(provider, assistant)
}

/** 生成可读的完整配置预览文本（供详情页展示与复制） */
fun HfModel.toConfigPreview(): String = buildString {
    val size = estimatedDownloadMb()
    val minRam = estimateMinRamMb()
    appendLine("# ${displayName} 自动配置预览")
    appendLine()
    appendLine("> 模型仓库 : $repoId")
    appendLine("> 任务类型 : ${taskType().label}")
    appendLine("> 框架     : ${framework()?.label ?: "未知"}")
    appendLine("> 许可证   : ${licenseType().label}")
    appendLine("> 参数量   : ${"%.1f".format(paramCountB())}B")
    appendLine("> 估算体积 : ${if (size > 0) "$size MB" else "未知"}")
    appendLine("> 建议内存 : ≥ ${minRam} MB")
    appendLine("> MNN 格式 : ${if (isMnnFormat) "是（可设备端离线运行）" else "否（需桌面端本地服务）"}")
    appendLine()

    if (isMnnFormat) {
        appendLine("## 1. 模型下载配置 (CatalogModel)")
        appendLine()
        appendLine("```json")
        appendLine(toCatalogModel().toPreviewJson())
        appendLine("```")
        appendLine()
        appendLine("## 2. 助手配置 (Assistant → MNN 本地模型供应商)")
        appendLine()
        appendLine("```json")
        appendLine(toMnnAssistant().toPreviewJson())
        appendLine("```")
    } else {
        val cfg = toLocalServerConfig()
        appendLine("## 1. 本地服务供应商配置 (Local LLM Server)")
        appendLine()
        appendLine("```json")
        appendLine(cfg.provider.toPreviewJson())
        appendLine("```")
        appendLine()
        appendLine("## 2. 助手配置 (Assistant)")
        appendLine()
        appendLine("```json")
        appendLine(cfg.assistant.toPreviewJson())
        appendLine("```")
    }

    appendLine()
    appendLine("> 以上配置由模型 ID 自动解析所需依赖、参数与文件生成，无需手动编写。")
}
