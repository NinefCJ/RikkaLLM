package com.ninef.rikkallm.data.huggingface

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
        val resp = runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").get().build())
                .execute()
        }.getOrNull() ?: return emptyList()
        if (!resp.isSuccessful) { resp.close(); return emptyList() }
        val body = resp.body.string().also { resp.close() }
        return runCatching {
            val parsed = json.decodeFromString<MsListResponse>(body)
            parsed.data?.models?.map { it.toHfModel() } ?: emptyList()
        }.getOrDefault(emptyList())
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
