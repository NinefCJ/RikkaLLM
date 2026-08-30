package com.ninef.rikkallm.data.huggingface

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 管理模型市场当前选择的源，并基于网络探测提供自动推荐。
 *
 * 作为 Koin 单例，供设置页与模型市场页共享同一份选择状态。
 * 默认 [ModelMarketSource.AUTO]，首次进入页面时调用 [recommendNow] 探测
 * huggingface.co 与 modelscope.cn 的可达性与延迟，给出推荐源。
 */
class ModelSourceManager(private val client: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _source = MutableStateFlow(ModelMarketSource.AUTO)
    val source: StateFlow<ModelMarketSource> = _source.asStateFlow()

    private val _recommended = MutableStateFlow(ModelMarketSource.HUGGINGFACE)
    val recommended: StateFlow<ModelMarketSource> = _recommended.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    /** 用户手动选择模型源 */
    fun setSource(s: ModelMarketSource) {
        _source.value = s
    }

    /** 自动模式下实际生效的源 */
    fun effectiveSource(): ModelMarketSource {
        val s = _source.value
        return if (s.isAuto) _recommended.value else s
    }

    /** 探测两个源的可达性与延迟，并刷新推荐结果（并发起探测，避免重复） */
    fun recommendNow() {
        if (_probing.value) return
        _probing.value = true
        scope.launch {
            try {
                _recommended.value = probe()
            } finally {
                _probing.value = false
            }
        }
    }

    private data class Ping(val ok: Boolean, val ms: Long)

    private fun ping(url: String): Ping {
        val start = System.nanoTime()
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val elapsed = (System.nanoTime() - start) / 1_000_000
                // 404 也代表 host 可达（DNS/TLS/路由正常）
                Ping(resp.isSuccessful || resp.code == 404, elapsed)
            }
        }.getOrDefault(Ping(false, Long.MAX_VALUE))
    }

    private fun probe(): ModelMarketSource {
        val hf = ping("https://huggingface.co")
        val ms = ping("https://modelscope.cn")
        return when {
            hf.ok && ms.ok -> if (hf.ms <= ms.ms) ModelMarketSource.HUGGINGFACE else ModelMarketSource.MODELSCOPE
            hf.ok -> ModelMarketSource.HUGGINGFACE
            ms.ok -> ModelMarketSource.MODELSCOPE
            else -> ModelMarketSource.HUGGINGFACE
        }
    }
}
