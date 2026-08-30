// Singleton coordinator for the local MNN stack: owns the engine adapter, exposes
// server/engine state as a StateFlow, starts/stops the foreground service on demand
// and hands port/token out to the app bridge. Registered in Koin by the host app.

package com.alibaba.mnnllm.android.server

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.alibaba.mnnllm.android.server.MnnServerService.Companion.ACTION_START
import com.alibaba.mnnllm.android.server.MnnServerService.Companion.ACTION_STOP
import com.alibaba.mnnllm.android.server.MnnServerService.Companion.EXTRA_PORT
import com.alibaba.mnnllm.android.server.MnnServerService.Companion.EXTRA_TOKEN
import com.alibaba.mnnllm.android.utils.AppContext
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LocalMnnManager"

data class MnnServerState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val port: Int = LocalMnnManager.DEFAULT_PORT,
    val token: String = "",
    val currentModel: String? = null,
    val modelLoading: Boolean = false,
    val modelDir: String? = null,
    val error: String? = null,
)

class LocalMnnManager(private val context: Context) : LocalLlmServerBackend {

    companion object {
        // 8090 (not 8080): the app's own WebServerService defaults to 8080 and the
        // two loopback servers must not fight over the same port.
        const val DEFAULT_PORT = 8090
        const val MODEL_ID = "mnn-local"

        private const val PREFS = "mnn_local_server"
        private const val KEY_PORT = "port"
        private const val KEY_MODEL_DIR = "model_dir"

        // Marks whether the app's "MNN 本地模型" provider entry currently reflects
        // state written by the Phase 2 sync bridge (MnnLocalProviderSync). Only
        // entries carrying this marker may be reset when the local server stops,
        // so manually configured Phase 1 entries are never clobbered.
        private const val KEY_PROVIDER_MANAGED = "provider_entry_managed"
    }

    init {
        // The ported MNN engine code resolves files through this static context.
        AppContext.init(context)
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var adapter: LocalLlmEngine = MnnEngineAdapter()

    private val _state = MutableStateFlow(
        MnnServerState(
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            modelDir = prefs.getString(KEY_MODEL_DIR, null),
        )
    )
    val state: StateFlow<MnnServerState> = _state

    /** Persisted marker: the provider entry currently holds Phase 2 sync-managed values. */
    var providerEntryManaged: Boolean
        get() = prefs.getBoolean(KEY_PROVIDER_MANAGED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PROVIDER_MANAGED, value).apply()
        }

    // ------------------------------------------------------------------
    // MnnServerBackend (consumed by the Ktor routes)
    // ------------------------------------------------------------------

    override val engine: LocalLlmEngine get() = adapter

    override val modelId: String get() = MODEL_ID

    override val token: String get() = _state.value.token

    private val generationLock = AtomicBoolean(false)

    override fun tryAcquireGeneration(): Boolean = generationLock.compareAndSet(false, true)

    override fun releaseGeneration() {
        generationLock.set(false)
    }

    // ------------------------------------------------------------------
    // server lifecycle
    // ------------------------------------------------------------------

    fun startServer(port: Int = _state.value.port) {
        val safePort = if (port in 1..65535) port else DEFAULT_PORT
        val newToken = generateToken()
        prefs.edit().putInt(KEY_PORT, safePort).apply()
        _state.update { it.copy(starting = true, port = safePort, token = newToken, error = null) }
        val intent = Intent(context, MnnServerService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_PORT, safePort)
            putExtra(EXTRA_TOKEN, newToken)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MNN server service", e)
            _state.update { it.copy(starting = false, running = false, token = "", error = e.message) }
        }
    }

    fun stopServer() {
        try {
            context.startService(Intent(context, MnnServerService::class.java).setAction(ACTION_STOP))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MNN server service", e)
            _state.update { it.copy(running = false, starting = false, token = "") }
        }
    }

    /** Called by [MnnServerService] to sync the credentials carried by the start intent. */
    internal fun adoptCredentials(port: Int, newToken: String) {
        if (newToken.isNotEmpty()) {
            _state.update { it.copy(port = port, token = newToken) }
        }
    }

    /** Called by [MnnServerService] once the HTTP server accepts connections. */
    internal fun onServerStarted(port: Int) {
        Log.i(TAG, "Server started on 127.0.0.1:$port")
        _state.update { it.copy(running = true, starting = false, port = port) }
        // Opportunistically load the previously selected model, if any.
        val dir = _state.value.modelDir
        if (dir != null && adapter.loadedModel == null) {
            loadModel(dir)
        }
    }

    /** Called by [MnnServerService] when the HTTP server went down. */
    internal fun onServerStopped(error: String? = null) {
        Log.i(TAG, "Server stopped${error?.let { ": $it" } ?: ""}")
        _state.update { it.copy(running = false, starting = false, token = "", error = error) }
    }

    // ------------------------------------------------------------------
    // model lifecycle (engine access is serialized; concurrent generations
    // are rejected with 429 by the route layer via tryAcquireGeneration)
    // ------------------------------------------------------------------

    fun loadModel(modelDirectory: String) {
        val dir = modelDirectory.trim()
        if (dir.isEmpty()) return
        prefs.edit().putString(KEY_MODEL_DIR, dir).apply()
        _state.update { it.copy(modelDir = dir, modelLoading = true, error = null) }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (generationLock.get()) {
                        throw IllegalStateException("Cannot swap the model while a generation is running")
                    }
                    // Pick the backend that can run this directory (GGUF -> llama.cpp, else MNN)
                    // and swap engines when the model type changes.
                    val required = detectBackend(dir)
                    val current = adapter
                    if ((required == LocalLlmBackend.LLAMA && current !is LlamaEngine) ||
                        (required == LocalLlmBackend.MNN && current !is MnnEngineAdapter)
                    ) {
                        current.unload()
                        adapter = when (required) {
                            LocalLlmBackend.LLAMA -> LlamaEngine(context.contentResolver, context.cacheDir)
                            LocalLlmBackend.MNN -> MnnEngineAdapter()
                        }
                    }
                    adapter.load(dir)
                }
            }
            _state.update {
                it.copy(
                    modelLoading = false,
                    currentModel = adapter.loadedModel,
                    error = result.exceptionOrNull()?.message,
                )
            }
            result.onFailure { Log.e(TAG, "loadModel failed", it) }
        }
    }

    fun unloadModel() {
        if (generationLock.get()) {
            _state.update { it.copy(error = "Cannot unload the model while a generation is running") }
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) { adapter.unload() }
            _state.update { it.copy(currentModel = null) }
        }
    }

    /**
     * Lists models already present on disk under the engine's models root
     * (`filesDir/.mnnmodels`). Powers the Phase 3 in-app model manager so users can see
     * installed models (and their version, when a downloader wrote a marker) and load
     * one without manually pasting a directory path. Returns an empty list when the
     * directory does not exist yet.
     */
    fun listInstalledModels(): List<LocalModelRegistry.InstalledModel> {
        val root = File(context.filesDir, ".mnnmodels")
        return LocalModelRegistry.list(root)
    }

    /** Root directory that holds every installed local model (each in its own sub-folder). */
    fun modelsRoot(): File = File(context.filesDir, ".mnnmodels")

    /**
     * Removes an installed model's folder. If the model is the one currently selected for
     * auto-load, the persisted selection is cleared too so the engine won't try to reload a
     * missing directory on next start. The engine keeps a memory-mapped handle to a loaded
     * model, so callers should unload first; deletion still proceeds (the OS keeps the
     * mapped inode alive until unload) and the in-memory model is released on the next unload.
     */
    fun deleteModel(id: String): Boolean {
        val dir = File(modelsRoot(), id)
        if (!dir.exists()) return false
        val wasCurrent = _state.value.modelDir?.let { File(it).name } == id
        val deleted = dir.deleteRecursively()
        if (deleted && wasCurrent) {
            prefs.edit().remove(KEY_MODEL_DIR).apply()
            _state.update { it.copy(modelDir = null, currentModel = null) }
        }
        return deleted
    }

    /** Directory name of the model currently selected for auto-load, or null. */
    fun currentModelId(): String? = _state.value.modelDir?.let { File(it).name }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
}
