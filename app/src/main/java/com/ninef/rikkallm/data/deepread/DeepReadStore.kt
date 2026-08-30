package com.ninef.rikkallm.data.deepread

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.deepReadDataStore by preferencesDataStore("deep_read_prefs")
private val REPORTS_KEY = stringPreferencesKey("reports")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * 深度阅读报告的持久化存储。基于 preferences DataStore 以 JSON 字符串保存，避免额外 schema 迁移。
 */
class DeepReadStore(private val context: Context) {
    val stateFlow: Flow<DeepReadState> = context.deepReadDataStore.data.map { prefs ->
        prefs[REPORTS_KEY]?.let { runCatching { json.decodeFromString<DeepReadState>(it) }.getOrNull() }
            ?: DeepReadState()
    }

    private suspend fun read(): DeepReadState =
        context.deepReadDataStore.data.first()[REPORTS_KEY]?.let {
            runCatching { json.decodeFromString<DeepReadState>(it) }.getOrNull()
        } ?: DeepReadState()

    private suspend fun write(state: DeepReadState) {
        context.deepReadDataStore.edit { it[REPORTS_KEY] = json.encodeToString(state) }
    }

    suspend fun listReports(): List<DeepReadReport> = read().reports

    suspend fun getReport(id: String): DeepReadReport? = read().reports.firstOrNull { it.id == id }

    suspend fun saveReport(report: DeepReadReport) {
        val current = read()
        // 同一 id 覆盖，否则置顶插入
        val without = current.reports.filter { it.id != report.id }
        write(current.copy(reports = listOf(report) + without))
    }

    suspend fun deleteReport(id: String) {
        val current = read()
        write(current.copy(reports = current.reports.filter { it.id != id }))
    }
}
