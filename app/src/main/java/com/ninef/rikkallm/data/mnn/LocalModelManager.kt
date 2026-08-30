package com.ninef.rikkallm.data.mnn

import com.alibaba.mnnllm.android.server.LocalModelRegistry
import com.alibaba.mnnllm.android.server.LocalMnnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * App-side orchestrator for Phase 3 in-app model management. Bridges the curated
 * [ModelCatalog] and the [ModelDownloader] to the engine's on-disk registry
 * ([LocalMnnManager] / [LocalModelRegistry]), exposing install / update / delete actions
 * and reactive [downloadState] / [installed] flows for the settings UI to bind to.
 */
class LocalModelManager(
    private val mnnManager: LocalMnnManager,
    private val downloader: ModelDownloader,
) {
    val catalog: List<CatalogModel> = ModelCatalog.default

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledModelInfo>>(emptyList())
    val installed: StateFlow<List<InstalledModelInfo>> = _installed.asStateFlow()

    /** Recomputes the installed-models list from disk. Call after install / delete / load. */
    fun refresh() {
        val catalogById = catalog.associateBy { it.id }
        _installed.value = mnnManager.listInstalledModels().map { im ->
            val id = im.dir.name
            InstalledModelInfo(
                id = id,
                dir = im.dir,
                version = im.version,
                sizeBytes = im.sizeBytes,
                catalogEntry = catalogById[id],
                isLoaded = mnnManager.currentModelId() == id,
            )
        }
    }

    /** True when an installed model's version lags the catalog version (i.e. updatable). */
    fun needsUpdate(id: String): Boolean {
        val info = _installed.value.firstOrNull { it.id == id } ?: return false
        val entry = catalog.firstOrNull { it.id == id } ?: return false
        return info.version != entry.version
    }

    /** Downloads (or re-downloads) a catalog model into the engine's models root. */
    fun download(model: CatalogModel) {
        activeJob?.cancel()
        _downloadState.value = DownloadState.Idle
        activeJob = scope.launch {
            val root = File(mnnManager.modelsRoot(), model.id)
            val specs = model.files.map { rel ->
                DownloadFileSpec(
                    url = model.source.urlFor(rel),
                    target = File(root, rel),
                    sizeHint = model.fileSizes[rel] ?: 0L,
                )
            }
            val result = downloader.download(
                files = specs,
                cancel = { activeJob?.isCancelled == true },
                onProgress = { p -> _downloadState.value = DownloadState.Progress(model.id, p) },
            )
            when (result) {
                is DownloadResult.Success -> {
                    LocalModelRegistry.writeVersion(root, model.version)
                    _downloadState.value = DownloadState.Success(model.id)
                    refresh()
                }

                is DownloadResult.Error -> _downloadState.value =
                    DownloadState.Error(model.id, result.message)

                is DownloadResult.Canceled -> _downloadState.value =
                    DownloadState.Canceled(model.id)
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        _downloadState.value = DownloadState.Canceled(
            (_downloadState.value as? DownloadState.Progress)?.modelId ?: "",
        )
    }

    /** Removes an installed model from disk. */
    fun delete(id: String): Boolean {
        val ok = mnnManager.deleteModel(id)
        refresh()
        return ok
    }
}

/** A model present on disk, enriched with catalog + load state for the UI. */
data class InstalledModelInfo(
    val id: String,
    val dir: File,
    val version: String?,
    val sizeBytes: Long,
    val catalogEntry: CatalogModel?,
    val isLoaded: Boolean,
)

/** Reactive download lifecycle for the UI. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Progress(val modelId: String, val progress: DownloadProgress) : DownloadState
    data class Success(val modelId: String) : DownloadState
    data class Error(val modelId: String, val message: String) : DownloadState
    data class Canceled(val modelId: String) : DownloadState
}
