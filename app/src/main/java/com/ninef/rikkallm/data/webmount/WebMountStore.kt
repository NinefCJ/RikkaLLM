package com.ninef.rikkallm.data.webmount

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.webMountDataStore by preferencesDataStore("webmount_prefs")
private val MOUNTS_KEY = stringPreferencesKey("mounts")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * 网页挂载配置的持久化存储。基于 preferences DataStore 以 JSON 字符串保存，
 * 避免引入额外的 schema 迁移成本。
 */
class WebMountStore(private val context: Context) {
    val stateFlow: Flow<WebMountState> = context.webMountDataStore.data.map { prefs ->
        prefs[MOUNTS_KEY]?.let { runCatching { json.decodeFromString<WebMountState>(it) }.getOrNull() }
            ?: WebMountState()
    }

    private suspend fun read(): WebMountState =
        context.webMountDataStore.data.first()[MOUNTS_KEY]?.let {
            runCatching { json.decodeFromString<WebMountState>(it) }.getOrNull()
        } ?: WebMountState()

    private suspend fun write(state: WebMountState) {
        context.webMountDataStore.edit { it[MOUNTS_KEY] = json.encodeToString(state) }
    }

    suspend fun getMounts(): List<WebMountConfig> = read().mounts

    suspend fun addMount(mount: WebMountConfig) {
        val current = read()
        write(current.copy(mounts = current.mounts + mount))
    }

    suspend fun removeMount(id: String) {
        val current = read()
        write(current.copy(mounts = current.mounts.filter { it.id != id }))
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = read()
        write(
            current.copy(
                mounts = current.mounts.map { if (it.id == id) it.copy(enabled = enabled) else it },
            ),
        )
    }
}
