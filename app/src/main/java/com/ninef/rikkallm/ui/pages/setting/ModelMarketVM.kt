package com.ninef.rikkallm.ui.pages.setting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.huggingface.HfModel
import com.ninef.rikkallm.data.huggingface.HfQuery
import com.ninef.rikkallm.data.huggingface.HuggingFaceApi
import com.ninef.rikkallm.data.huggingface.ModelMarketApi
import com.ninef.rikkallm.data.huggingface.ModelMarketSource
import com.ninef.rikkallm.data.huggingface.ModelScopeApi
import com.ninef.rikkallm.data.huggingface.ModelSourceManager
import com.ninef.rikkallm.data.huggingface.SortOption
import com.ninef.rikkallm.data.huggingface.paramCountB
import com.ninef.rikkallm.data.huggingface.sliderPosToParamsB
import com.ninef.rikkallm.data.huggingface.toCatalogModel
import com.ninef.rikkallm.data.mnn.DownloadState
import com.ninef.rikkallm.data.mnn.EnvironmentReport
import com.ninef.rikkallm.data.mnn.LocalEnvironmentDetector
import com.ninef.rikkallm.data.mnn.LocalModelManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val BASE_LIMIT = 100
private const val MAX_LIMIT = 500
private const val PAGE_STEP = 100

class ModelMarketVM : ViewModel(), KoinComponent {
    private val hfApi: HuggingFaceApi by inject()
    private val msApi: ModelScopeApi by inject()
    private val sourceManager: ModelSourceManager by inject()
    private val localModelManager: LocalModelManager by inject()
    private val localEnvironmentDetector: LocalEnvironmentDetector by inject()

    /** 当前选择的模型源（与设置页共享同一份状态） */
    val sourceState: StateFlow<ModelMarketSource> get() = sourceManager.source
    val recommendedState: StateFlow<ModelMarketSource> get() = sourceManager.recommended
    val probingState: StateFlow<Boolean> get() = sourceManager.probing

    fun setSource(s: ModelMarketSource) = sourceManager.setSource(s)

    /** 根据选择 / 自动推荐返回实际使用的后端 */
    private val activeApi: ModelMarketApi
        get() = when (sourceManager.effectiveSource()) {
            ModelMarketSource.MODELSCOPE -> msApi
            else -> hfApi
        }

    var query by mutableStateOf(HfQuery())
        private set

    /** 服务器侧过滤后的原始模型集合 */
    var allModels by mutableStateOf<List<HfModel>>(emptyList())
        private set

    /** 展示给用户的模型列表（经客户端筛选 + 排序） */
    var models by mutableStateOf<List<HfModel>>(emptyList())
        private set

    /** 可选厂商（来自已加载模型去重后的作者） */
    var vendors by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 是否还能从服务器加载更多（分页） */
    var canLoadMore by mutableStateOf(false)
        private set

    // —— 客户端筛选状态（实时刷新，无需重新请求） ——
    /** 模型大小双头滑块位置 [0,1]，对数刻度映射到参数量 */
    var sizeRange by mutableStateOf(0f..1f)
        private set

    /** 选中的厂商（作者），null 表示全部 */
    var vendor by mutableStateOf<String?>(null)
        private set

    var sortOption by mutableStateOf(SortOption.DOWNLOADS)
        private set

    // —— 详情 / 下载 ——
    var detail by mutableStateOf<HfModel?>(null)
        private set
    var readme by mutableStateOf<String?>(null)
        private set
    var envReport by mutableStateOf<EnvironmentReport?>(null)
        private set
    var isDetailLoading by mutableStateOf(false)
        private set
    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set
    var isConfiguring by mutableStateOf(false)
        private set
    var pendingCatalogId by mutableStateOf<String?>(null)
        private set

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        sourceManager.recommendNow()
        loadPopular()
        viewModelScope.launch {
            localModelManager.downloadState.collect { downloadState = it }
        }
    }

    private fun deriveVendors(list: List<HfModel>): List<String> =
        list.mapNotNull { it.author }.distinct().sorted()

    /** 重新计算展示列表：应用模型大小 / 厂商筛选，并按所选维度排序（实时） */
    private fun recompute() {
        val minB = if (sizeRange.start <= 0.02f) 0.0 else sliderPosToParamsB(sizeRange.start)
        val maxB = if (sizeRange.endInclusive >= 0.98f) Double.MAX_VALUE else sliderPosToParamsB(sizeRange.endInclusive)
        val filtered = allModels.filter { m ->
            val p = m.paramCountB()
            p >= minB && p <= maxB && (vendor == null || m.author == vendor)
        }
        models = when (sortOption) {
            SortOption.DOWNLOADS -> filtered.sortedByDescending { it.downloads }
            SortOption.UPDATED -> filtered.sortedByDescending { it.lastModified ?: "" }
            SortOption.SIZE -> filtered.sortedByDescending { it.paramCountB() }
        }
    }

    private fun refetch(showFull: Boolean = true) {
        viewModelScope.launch {
            if (showFull) isLoading = true else isLoadingMore = true
            error = null
            runCatching { activeApi.getModels(query) }
                .onSuccess { fetched ->
                    allModels = fetched
                    vendors = deriveVendors(fetched)
                    canLoadMore = query.limit < MAX_LIMIT
                    recompute()
                }
                .onFailure { error = it.message ?: "加载失败" }
            if (showFull) isLoading = false else isLoadingMore = false
        }
    }

    fun loadPopular() = refetch(showFull = true)

    /** 加载更多（服务器分页）：提升 limit 重新拉取 */
    fun loadMore() {
        if (isLoadingMore || !canLoadMore) return
        query = query.copy(limit = (query.limit + PAGE_STEP).coerceAtMost(MAX_LIMIT))
        refetch(showFull = false)
    }

    fun onSearch(text: String) {
        query = query.copy(search = text, limit = BASE_LIMIT)
        refetch(showFull = true)
    }

    fun setTaskFilter(tag: String?) {
        query = query.copy(pipelineTag = tag, limit = BASE_LIMIT)
        refetch(showFull = true)
    }

    fun setFramework(lib: String?) {
        query = query.copy(libraryName = lib, limit = BASE_LIMIT)
        refetch(showFull = true)
    }

    fun setLicense(license: String?) {
        query = query.copy(license = license, limit = BASE_LIMIT)
        refetch(showFull = true)
    }

    /** 模型大小筛选（实时，不重新请求） */
    fun updateSizeRange(range: ClosedFloatingPointRange<Float>) {
        sizeRange = range
        recompute()
    }

    /** 模型厂商筛选（实时，不重新请求） */
    fun updateVendor(v: String?) {
        vendor = v
        recompute()
    }

    /** 排序维度切换（实时，不重新请求） */
    fun setSort(option: SortOption) {
        sortOption = option
        recompute()
    }

    /** 重置所有筛选条件 */
    fun resetFilters() {
        query = HfQuery()
        sizeRange = 0f..1f
        vendor = null
        sortOption = SortOption.DOWNLOADS
        refetch(showFull = true)
    }

    fun openDetail(modelId: String) {
        detail = null
        readme = null
        envReport = null
        isDetailLoading = true
        viewModelScope.launch {
            runCatching {
                val model = activeApi.getModel(modelId)
                if (model != null) {
                    detail = model
                    readme = activeApi.getReadme(model.id)
                    envReport = localEnvironmentDetector.analyze(model)
                }
            }
            isDetailLoading = false
        }
    }

    fun onDownloadFinished(downloadedId: String) {
        downloadState = localModelManager.downloadState.value
    }

    fun loadOrConfigure() {
        val model = detail ?: return
        val catalog = model.toCatalogModel(sourceManager.effectiveSource())
        pendingCatalogId = catalog.id
        isConfiguring = true
        localModelManager.download(catalog)
        isConfiguring = false
        pendingCatalogId = null
        _events.trySend("已下载模型，请在设置-模型管理中配置")
    }
}
