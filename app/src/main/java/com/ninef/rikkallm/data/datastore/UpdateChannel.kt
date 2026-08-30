package com.ninef.rikkallm.data.datastore

/**
 * 更新渠道：
 * - [STABLE]        仅获取 GitHub 正式 Release（非预发布）
 * - [INCLUDE_DEBUG] 同时获取 Debug / 预发布（prerelease）更新
 */
enum class UpdateChannel {
    STABLE,
    INCLUDE_DEBUG,
}
