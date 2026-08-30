package com.ninef.rikkallm.data.webmount

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.core.Tool
import com.ninef.rikkallm.data.webmount.adapters.GitHubAdapter
import okhttp3.OkHttpClient

/**
 * 网页挂载管理器：持有已配置挂载的内存缓存，并据此把各站点适配器暴露的工具注入给 Agent。
 *
 * 设计为「非挂起产出工具」以便接入 [com.ninef.rikkallm.service.ChatService.send]：
 * 工具列表在每次生成时从缓存重建，工具实际的网络调用在各自的 execute 内挂起执行。
 */
class WebMountManager(
    context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val store = WebMountStore(context)
    private val adapters: List<WebMountAdapter> = listOf(GitHubAdapter())

    private val _mounts = MutableStateFlow<List<WebMountConfig>>(emptyList())

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            store.stateFlow.collect { _mounts.value = it.mounts }
        }
    }

    /** 当前已启用的挂载（非挂起） */
    fun getEnabledMounts(): List<WebMountConfig> = _mounts.value.filter { it.enabled }

    /** 构建所有已启用挂载对应的工具（非挂起） */
    fun buildTools(): List<Tool> = getEnabledMounts().flatMap { mount ->
        adapters.firstOrNull { it.siteId == mount.siteId }?.buildTools(mount, okHttpClient)
            ?: emptyList()
    }
}
