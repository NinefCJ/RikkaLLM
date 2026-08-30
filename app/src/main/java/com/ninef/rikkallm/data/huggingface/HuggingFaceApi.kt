package com.ninef.rikkallm.data.huggingface

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HuggingFace Hub REST API 客户端。
 *
 * 文档：https://huggingface.co/docs/hub/api
 * 列表端点：`GET /api/models`（支持 sort / filter / search / author）
 * 详情端点：`GET /api/models/{id}`
 * README：`GET /api/models/{id}/raw/README.md`
 */
class HuggingFaceApi(
    private val client: OkHttpClient,
) : ModelMarketApi {
    private val base = "https://huggingface.co"

    override val source = ModelMarketSource.HUGGINGFACE

    // 上游返回字段不规则，使用宽松解析以避免未知字段导致失败
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 拉取热门模型列表（按下载量降序） */
    override suspend fun getModels(query: HfQuery): List<HfModel> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$base/api/models?sort=downloads&direction=-1&limit=${query.limit}")
            if (query.search.isNotBlank()) append("&search=${enc(query.search)}")
            query.pipelineTag?.let { append("&filter=pipeline_tag:${enc(it)}") }
            query.libraryName?.let { append("&filter=library:${enc(it)}") }
            query.license?.let { append("&filter=license:${enc(it)}") }
            query.author?.let { append("&author=${enc(it)}") }
        }
        // 不再吞掉异常：网络 / HTTP 失败应让上层把错误展示给用户，
        // 否则市场会表现为“空白且无法使用”而无法诊断。
        val resp = client.newCall(
            Request.Builder().url(url).header("Accept", "application/json").build(),
        ).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("Hugging Face 模型市场请求失败 (HTTP ${resp.code})")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) throw IOException("Hugging Face 返回了空响应")
            lenientJson.decodeFromString<List<HfModel>>(text)
        }
    }

    /** 拉取单个模型详情 */
    override suspend fun getModel(id: String): HfModel? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(
                Request.Builder().url("$base/api/models/${enc(id)}")
                    .header("Accept", "application/json").build(),
            ).execute()
            if (resp.isSuccessful) {
                resp.body?.string()?.let { lenientJson.decodeFromString<HfModel>(it) }
            } else {
                null
            }
        }.getOrNull()
    }

    /** 拉取模型 README（用于详情页说明） */
    override suspend fun getReadme(id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(
                Request.Builder().url("$base/api/models/${enc(id)}/raw/README.md")
                    .header("Accept", "text/plain").build(),
            ).execute()
            if (resp.isSuccessful) resp.body?.string() else null
        }.getOrNull()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
