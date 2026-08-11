// Bridge between the local MNN server (lives in the :mnn module) and the app's
// provider datastore: once the local OpenAI-compatible service is up, the built-in
// "MNN 本地模型" provider entry is rewritten with the actual port and the per-start
// bearer token, reusing the regular DataStore update path. When the service goes
// down the entry is disabled again because the token is regenerated on every start.

package me.rerere.rikkahub.data.ai

import android.util.Log
import com.alibaba.mnnllm.android.server.LocalMnnManager
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore

private const val TAG = "MnnLocalProviderSync"

// Id of the built-in "MNN 本地模型" provider (see DefaultProviders.kt).
private val MNN_LOCAL_PROVIDER_ID = Uuid.parse("9b2f8c41-6d3e-4a57-b8f2-1e5d9c7a3b64")

class MnnLocalProviderSync(
    localMnnManager: LocalMnnManager,
    private val settingsStore: SettingsStore,
    appScope: AppScope,
) {
    init {
        appScope.launch {
            localMnnManager.state.collect { state ->
                runCatching {
                    if (state.running && state.token.isNotEmpty()) {
                        syncRunning(state.port, state.token)
                    } else {
                        syncStopped()
                    }
                }.onFailure { Log.e(TAG, "sync failed", it) }
            }
        }
    }

    private suspend fun syncRunning(port: Int, token: String) {
        if (settingsStore.settingsFlow.value.init) return
        val baseUrl = "http://127.0.0.1:$port/v1"
        var changed = false
        settingsStore.update { current ->
            val providers = current.providers.map { provider ->
                if (provider.id == MNN_LOCAL_PROVIDER_ID && provider is ProviderSetting.OpenAI) {
                    if (provider.baseUrl != baseUrl || provider.apiKey != token || !provider.enabled) {
                        changed = true
                        provider.copy(baseUrl = baseUrl, apiKey = token, enabled = true)
                    } else {
                        provider
                    }
                } else {
                    provider
                }
            }
            current.copy(providers = providers)
        }
        if (changed) {
            Log.i(TAG, "Synced MNN local provider: baseUrl=$baseUrl")
        }
    }

    private suspend fun syncStopped() {
        if (settingsStore.settingsFlow.value.init) return
        var changed = false
        settingsStore.update { current ->
            val providers = current.providers.map { provider ->
                if (provider.id == MNN_LOCAL_PROVIDER_ID && provider is ProviderSetting.OpenAI) {
                    // The bearer token dies with the service: a leftover apiKey would
                    // only produce 401s, so disable the entry until the next start.
                    if (provider.enabled || provider.apiKey.isNotEmpty()) {
                        changed = true
                        provider.copy(apiKey = "", enabled = false)
                    } else {
                        provider
                    }
                } else {
                    provider
                }
            }
            current.copy(providers = providers)
        }
        if (changed) {
            Log.i(TAG, "Disabled MNN local provider (server stopped)")
        }
    }
}
