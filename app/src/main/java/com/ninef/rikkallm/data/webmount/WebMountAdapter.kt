package com.ninef.rikkallm.data.webmount

import me.rerere.ai.core.Tool
import okhttp3.OkHttpClient

/**
 * 站点适配器：把某个外部站点（如 GitHub）暴露成一组 Agent 可调用的工具。
 * 实现类需注册到 [WebMountManager.adapters]。
 */
interface WebMountAdapter {
    /** 站点 id，对应 [WebMountConfig.siteId] */
    val siteId: String

    /** 展示名 */
    val displayName: String

    /** 该站点支持的鉴权方式 */
    val supportedAuth: List<WebMountAuthType>

    /** 根据挂载配置构建工具列表（非挂起，工具的实际网络调用在 execute 内挂起执行） */
    fun buildTools(mount: WebMountConfig, client: OkHttpClient): List<Tool>
}
