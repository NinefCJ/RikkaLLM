package com.ninef.rikkallm.data.webmount

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 站点鉴权方式。
 * - PAT：个人访问令牌（如 GitHub Token），直接作为 Bearer 使用。
 * - OAUTH：标准 OAuth2 授权码流程（预留，后续接入 McpOAuth 回调）。
 */
@Serializable
enum class WebMountAuthType {
    PAT,
    OAUTH,
}

/**
 * 单个站点挂载配置。启用后，对应适配器会把该站点暴露成一组可读写工具注入给 Agent。
 *
 * @param id 唯一 id，同时用作工具命名空间，确保多挂载不冲突
 * @param siteId 站点适配器 id（对应 [WebMountAdapter.siteId]），如 "github"
 * @param name 用户自定义标签（展示用）
 * @param authType 鉴权方式
 * @param token 访问令牌（PAT）或 OAuth access token
 * @param username 可选用户名/组织名（部分站点用于展示或路由）
 * @param baseUrl 可选自定义 API 地址（企业版 / 自托管场景）
 * @param enabled 是否启用（启用后其工具才会注入 agent）
 */
@Serializable
data class WebMountConfig(
    val id: String = Uuid.random().toString(),
    val siteId: String = "",
    val name: String = "",
    val authType: WebMountAuthType = WebMountAuthType.PAT,
    val token: String = "",
    val username: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class WebMountState(
    val mounts: List<WebMountConfig> = emptyList(),
)
