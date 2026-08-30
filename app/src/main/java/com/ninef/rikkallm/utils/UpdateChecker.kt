package com.ninef.rikkallm.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.getSystemService
import com.ninef.rikkallm.AppScope
import com.ninef.rikkallm.BuildConfig
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.datastore.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.DecimalFormat
import kotlin.math.pow

private const val GITHUB_API = "https://api.github.com/repos/NinefCJ/RikkaLLM/releases"
private const val USER_AGENT = "RikkaLLM-UpdateChecker"

private val json = Json { ignoreUnknownKeys = true }

/**
 * GitHub Release 列表接口返回的单个 release（仅保留需要的字段）
 */
@Serializable
private data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
    val content_type: String = "",
)

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String,
    val sizeBytes: Long,
)

data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>,
    val isPrerelease: Boolean = false,
)

/**
 * 版本号比较，支持 "1.2.3" 形式的语义化版本，并自动去除前导的 v / V。
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {
    private fun core(): List<Int> =
        value.trim().trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }

    override fun compareTo(other: Version): Int {
        val a = core()
        val b = other.core()
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    operator fun compareTo(other: String): Int = compareTo(Version(other))
}

/**
 * 下载进度状态机，供聊天页与设置页共享显示。
 */
sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(
        val progress: Float = -1f, // -1 表示未知总大小（不确定进度）
        val bytes: Long = 0,
        val total: Long = 0,
        val fileName: String = "",
    ) : DownloadProgress

    data class Completed(val uri: Uri, val fileName: String) : DownloadProgress
    data class Failed(val reason: String? = null) : DownloadProgress
}

/**
 * 更新管理器（Koin 单例）。负责从 GitHub Release 拉取版本信息、下载 APK 并引导安装。
 *
 * - 根据 [SettingsStore] 中的 [UpdateChannel] 自动决定只取正式 Release 还是同时取 Debug / 预发布。
 * - [updateState] 为应用内共享的最新版本状态；[downloadState] 为共享的下载进度。
 * - 安装为非静默：下载完成后仅跳转系统安装界面，由用户手动确认。
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
) {
    private val refreshSignal = MutableStateFlow(0)

    /**
     * 最新版本状态。随更新渠道（[UpdateChannel]）变化自动重新检测，也可通过 [refresh] 手动触发。
     */
    val updateState: StateFlow<UiState<UpdateInfo>> =
        combine(
            settingsStore.settingsFlow.map { it.updateChannel }.distinctUntilChanged(),
            refreshSignal,
        ) { channel, _ -> channel }
            .flatMapLatest { channel -> checkUpdate(channel == UpdateChannel.INCLUDE_DEBUG) }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Lazily, UiState.Loading)

    val downloadState: MutableStateFlow<DownloadProgress> = MutableStateFlow(DownloadProgress.Idle)

    /** 手动触发一次检查 */
    fun refresh() {
        refreshSignal.value++
    }

    /** 检测更新。includePrerelease 为 true 时包含所有 prerelease。 */
    fun checkUpdate(includePrerelease: Boolean): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UiState.Error(Exception("GitHub 返回 ${response.code}")))
                    return@flow
                }
                val body = response.body?.string().orEmpty()
                val releases = runCatching { json.decodeFromString<List<GithubRelease>>(body) }
                    .getOrDefault(emptyList())
                val release = releases
                    .filter { !it.draft && (includePrerelease || !it.prerelease) }
                    .maxByOrNull { it.published_at ?: "" }
                if (release == null) {
                    emit(UiState.Error(Exception("未找到可用的 Release")))
                    return@flow
                }
                val version = release.tag_name.trim().trimStart('v', 'V')
                val downloads = release.assets
                    .filter {
                        it.content_type == "application/vnd.android.package-archive" ||
                            it.name.endsWith(".apk", ignoreCase = true)
                    }
                    .map {
                        UpdateDownload(
                            name = it.name,
                            url = it.browser_download_url,
                            size = formatSize(it.size),
                            sizeBytes = it.size,
                        )
                    }
                if (downloads.isEmpty()) {
                    emit(UiState.Error(Exception("该 Release 未包含 APK 安装包")))
                    return@flow
                }
                emit(
                    UiState.Success(
                        UpdateInfo(
                            version = version,
                            publishedAt = release.published_at ?: "",
                            changelog = release.body ?: "",
                            downloads = downloads,
                            isPrerelease = release.prerelease,
                        ),
                    ),
                )
            }
        } catch (e: Exception) {
            emit(UiState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /** 通过 DownloadManager 下载更新包，并实时更新 [downloadState]。 */
    fun startDownload(context: Context, download: UpdateDownload) {
        downloadState.value = DownloadProgress.Downloading(
            progress = 0f,
            bytes = 0,
            total = download.sizeBytes,
            fileName = download.name,
        )
        val dm = context.getSystemService<DownloadManager>() ?: return
        val request = DownloadManager.Request(Uri.parse(download.url)).apply {
            setTitle(download.name)
            setDescription("RikkaLLM 更新包")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "rikkallm_${download.name}",
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val id = dm.enqueue(request)
        appScope.launch {
            while (true) {
                val query = DownloadManager.Query().setFilterById(id)
                dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        downloadState.value = DownloadProgress.Failed("下载任务丢失")
                        return@launch
                    }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = dm.getUriForDownloadedFile(id)
                            downloadState.value = if (uri != null) {
                                DownloadProgress.Completed(uri, download.name)
                            } else {
                                DownloadProgress.Failed("无法获取已下载文件")
                            }
                            return@launch
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            downloadState.value = DownloadProgress.Failed("下载失败（错误码 $reason）")
                            return@launch
                        }

                        DownloadManager.STATUS_PAUSED -> {
                            downloadState.value = DownloadProgress.Downloading(
                                progress = -1f,
                                bytes = soFar,
                                total = total,
                                fileName = download.name,
                            )
                        }

                        else -> {
                            val progress = if (total > 0) soFar.toFloat() / total else -1f
                            downloadState.value = DownloadProgress.Downloading(
                                progress = progress,
                                bytes = soFar,
                                total = total,
                                fileName = download.name,
                            )
                        }
                    }
                }
                delay(400)
            }
        }
    }

    /**
     * 引导用户手动安装已下载的 APK。返回 true 表示已成功拉起安装界面。
     * 注意：Android 不会静默安装，系统会要求用户确认“允许安装未知应用”。
     */
    fun openInstall(context: Context): Boolean {
        val prog = downloadState.value
        if (prog !is DownloadProgress.Completed) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(prog.uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "未知大小"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        val idx = digitGroups.coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(idx.toDouble())) + " " + units[idx]
    }
}
