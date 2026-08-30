package com.ninef.rikkallm.data.huggingface

import java.io.IOException
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 魔搭社区（ModelScope）模型市场后端实现。
 * 通过公开 API 获取模型列表 / 详情，并映射为统一的 [HfModel]，
 * 解决国内用户直连 Hugging Face 体验不佳的问题。
 */
class ModelScopeApi(private val client: OkHttpClient) : ModelMarketApi {
    override val source = ModelMarketSource.MODELSCOPE

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun listUrl(query: HfQuery, pageSize: Int, page: Int): String {
        val sb = StringBuilder("https://modelscope.cn/api/v1/models")
        sb.append("?PageSize=$pageSize&PageNumber=$page&SortBy=Downloads&SortOrder=Descend")
        if (query.search.isNotBlank()) {
            sb.append("&Search=").append(URLEncoder.encode(query.search, UTF_8.name()))
        }
        return sb.toString()
    }

    override suspend fun getModels(query: HfQuery): List<HfModel> {
        val pageSize = query.limit.coerceIn(1, 100)
        val url = listUrl(query, pageSize, 1)
        // 不再吞掉异常：网络 / HTTP 失败应让上层把错误展示给用户，
        // 否则市场会表现为“空白且无法使用”而无法诊断。
        val resp = client.newCall(
            Request.Builder().url(url).header("Accept", "application/json").get().build()
        ).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("ModelScope 模型市场请求失败 (HTTP ${resp.code})")
        }
        resp.use {
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("ModelScope 返回了空响应")
            val parsed = json.decodeFromString<MsListResponse>(body)
            parsed.data?.models?.map { m -> m.toHfModel() } ?: emptyList()
        }
    }

    override suspend fun getModel(id: String): HfModel? {
        val url = "https://modelscope.cn/api/v1/models/${encodeId(id)}"
        val resp = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute()
        }.getOrNull() ?: return null
        if (!resp.isSuccessful) { resp.close(); return null }
        val body = resp.body.string().also { resp.close() }
        return runCatching {
            val ms = json.decodeFromString<MsModel>(body)
            ms.takeIf { it.modelId != null }?.toHfModel(detail = true)
        }.getOrNull()
    }

    override suspend fun getReadme(id: String): String? {
        // 魔搭文件 resolve 端点（与 HuggingFace resolve 同源语义），公开模型无需鉴权
        val url = "https://modelscope.cn/models/${encodeId(id)}/resolve/master/README.md"
        val resp = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute()
        }.getOrNull() ?: return null
        if (!resp.isSuccessful) { resp.close(); return null }
        val text = resp.body.string().also { resp.close() }
        return text.takeIf { it.isNotBlank() }
    }

    private fun encodeId(id: String): String = id // 魔搭 modelId 形如 org/name，直接作为路径
}
