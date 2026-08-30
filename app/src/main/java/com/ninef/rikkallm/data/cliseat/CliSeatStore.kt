package com.ninef.rikkallm.data.cliseat

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.cliSeatDataStore by preferencesDataStore("cli_seat_prefs")
private val SEATS_KEY = stringPreferencesKey("seats")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * CLI 席位配置的持久化存储。基于 preferences DataStore 以 JSON 字符串保存，避免额外 schema 迁移。
 */
class CliSeatStore(private val context: Context) {
    val stateFlow: Flow<CliSeatState> = context.cliSeatDataStore.data.map { prefs ->
        prefs[SEATS_KEY]?.let { runCatching { json.decodeFromString<CliSeatState>(it) }.getOrNull() }
            ?: CliSeatState()
    }

    private suspend fun read(): CliSeatState =
        context.cliSeatDataStore.data.first()[SEATS_KEY]?.let {
            runCatching { json.decodeFromString<CliSeatState>(it) }.getOrNull()
        } ?: CliSeatState()

    private suspend fun write(state: CliSeatState) {
        context.cliSeatDataStore.edit { it[SEATS_KEY] = json.encodeToString(state) }
    }

    suspend fun getSeats(): List<CliSeatConfig> = read().seats

    suspend fun addSeat(seat: CliSeatConfig) {
        val current = read()
        write(current.copy(seats = current.seats + seat))
    }

    suspend fun removeSeat(id: String) {
        val current = read()
        write(current.copy(seats = current.seats.filter { it.id != id }))
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = read()
        write(
            current.copy(
                seats = current.seats.map { if (it.id == id) it.copy(enabled = enabled) else it },
            ),
        )
    }
}
