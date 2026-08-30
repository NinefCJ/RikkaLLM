package com.ninef.rikkallm.ui.pages.graph

import com.ninef.rikkallm.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninef.rikkallm.data.ai.graph.ContextPreview
import com.ninef.rikkallm.ui.context.LocalNavController
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 上下文图谱页面（M4）：承载 [GraphCanvas]，并作为进入 DAG 可视化视图的导航目标。
 * 通过 [LocalNavController] 提供返回能力；持有选中态与上下文预览，驱动 Inspect 浮层。
 *
 * 顶栏支持两种结构操作：
 * - "重建"：依据会话树重置为线性 CONTEXT 链（破坏性，覆盖用户编辑）；
 * - "合并模式"：进入多选，选中 ≥2 个节点后"合并"会创建一个 [com.ninef.rikkallm.data.graph.model.GraphNodeKind.MERGE]
 *   汇聚节点，把各分支上下文汇入同一目标（非破坏性）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphCanvasPage(conversationId: Uuid) {
    val nav = LocalNavController.current
    val vm: GraphCanvasVM = koinViewModel(parameters = { parametersOf(conversationId) })
    val graph by vm.graph.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 选中态与上下文预览上提至此，使两者联动：选中节点即异步取预览。
    var selected by remember { mutableStateOf<Uuid?>(null) }
    var preview by remember { mutableStateOf<ContextPreview?>(null) }

    // 合并模式：多选集合与开关。
    var mergeMode by remember { mutableStateOf(false) }
    var multiSelected by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    fun refreshPreview(id: Uuid) {
        preview = null
        scope.launch { preview = vm.inspect(id) }
    }

    fun toggleMergeMode() {
        mergeMode = !mergeMode
        multiSelected = emptySet()
        selected = null
        preview = null
    }

    fun toggleSelect(id: Uuid) {
        multiSelected = if (id in multiSelected) multiSelected - id else multiSelected + id
    }

    fun doMerge() {
        val ids = multiSelected.toList()
        scope.launch {
            vm.mergeNodes(ids)
            multiSelected = emptySet()
            mergeMode = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_page_graph_context)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Text("‹")
                    }
                },
                actions = {
                    if (mergeMode) {
                        IconButton(onClick = { doMerge() }, enabled = multiSelected.size >= 2) {
                            Text("合并(${multiSelected.size})")
                        }
                        IconButton(onClick = { toggleMergeMode() }) {
                            Text("取消")
                        }
                    } else {
                        IconButton(onClick = { toggleMergeMode() }) {
                            Text("合并模式")
                        }
                        IconButton(onClick = {
                            scope.launch { vm.rebuild() }
                            if (selected != null) refreshPreview(selected!!)
                        }) {
                            Text("重建")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            GraphCanvas(
                graph = graph,
                selected = if (mergeMode) null else selected,
                onSelectedChange = { id ->
                    selected = id
                    if (id != null) refreshPreview(id) else preview = null
                },
                onNodeMoved = { id, x, y -> vm.updateNodePosition(id, x, y) },
                onConnect = { from, to -> scope.launch { vm.connect(from, to) } },
                onPruneEdge = { edgeId ->
                    vm.pruneEdge(edgeId)
                    // 修剪后上下文构成变化，刷新预览
                    if (selected != null) refreshPreview(selected!!)
                },
                preview = if (mergeMode) null else preview,
                mergeMode = mergeMode,
                multiSelected = multiSelected,
                onToggleSelect = { toggleSelect(it) },
            )

            if (mergeMode) {
                Text(
                    "合并模式：点选 ≥2 个节点，再点\"合并\"汇聚为同一目标（非破坏性）",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
