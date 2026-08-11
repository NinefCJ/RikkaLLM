// Bridge between the local MNN server (lives in the :mnn module) and the app's
// provider datastore: once the local OpenAI-compatible service is up, the built-in
// "MNN 本地模型" provider entry is rewritten with the actual port and the per-start
// bearer token, reusing the regular DataStore update path. When the service goes
// down the entry is disabled again because the token is regenerated on every start —
// but ONLY when the entry actually carries state written by this bridge (see
// [mnnEntryIsSyncManaged]); entries the user configured manually (Phase 1 style)
// are never clobbered.

package me.rerere.rikkahub.data.ai

import android.util.Log
import com.alibaba.mnnllm.android.server.LocalMnnManager
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore

private const val TAG = "MnnLocalProviderSync"

// Id of the built-in "MNN 本地模型" provider (see DefaultProviders.kt).
private val MNN_LOCAL_PROVIDER_ID = Uuid.parse("9b2f8c41-6d3e-4a57-b8f2-1e5d9c7a3b64")

/**
 * Whether the provider entry may be treated as Phase 2 sync-managed and thus reset
 * when the local server stops. Requires the persisted marker (written whenever the
 * bridge rewrites the entry) AND a loopback baseUrl sanity check, so a user who
 * pointed the entry at a remote / manually configured endpoint keeps their values.
 */
internal fun mnnEntryIsSyncManaged(baseUrl: String, managedFlag: Boolean): Boolean =
    managedFlag && baseUrl.startsWith("http://127.0.0.1")

class MnnLocalProviderSync(
    private val localMnnManager: LocalMnnManager,
    private val settingsStore: SettingsStore,
    appScope: AppScope,
) {
    init {
        appScope.launch {
            // combine() also re-emits whenever the settings DataStore finishes
            // loading, so a server state observed before the first settings emission
            // (init == true) is replayed instead of being dropped forever.
            combine(localMnnManager.state, settingsStore.settingsFlow) { state, settings ->
                state to settings
            }.collect { (state, settings) ->
                if (settings.init) return@collect
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
        // The entry now carries bridge-written values: mark it so a later stop may
        // reset it again.
        localMnnManager.providerEntryManaged = true
        if (changed) {
            Log.i(TAG, "Synced MNN local provider: baseUrl=$baseUrl")
        }
    }

    private suspend fun syncStopped() {
        // Only clean up state this bridge wrote itself; a manually configured entry
        // (e.g. Phase 1 against a standalone MNN Chat) must survive cold starts.
        if (!localMnnManager.providerEntryManaged) return
        var changed = false
        settingsStore.update { current ->
            val providers = current.providers.map { provider ->
                if (provider.id == MNN_LOCAL_PROVIDER_ID && provider is ProviderSetting.OpenAI &&
                    mnnEntryIsSyncManaged(provider.baseUrl, managedFlag = true)
                ) {
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
        localMnnManager.providerEntryManaged = false
        if (changed) {
            Log.i(TAG, "Disabled MNN local provider (server stopped)")
        }
    }
}
