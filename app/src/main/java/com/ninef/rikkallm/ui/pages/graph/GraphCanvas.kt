package com.ninef.rikkallm.ui.pages.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ninef.rikkallm.data.ai.graph.ContextPreview
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import me.rerere.ai.ui.UIMessagePart
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

private val NODE_WIDTH = 160.dp
private val NODE_HEIGHT = 64.dp

/**
 * 可视化画布（M4）：纯 Compose 自绘，无 WebView / 无第三方图形库。
 *
 * - 背景层：`detectTransformGestures` 实现平移 + 捏合缩放，并在 [Canvas] 上绘制边。
 * - 节点层：每个节点可拖拽（更新坐标）、点击选中（Inspect）、通过右下角手柄进入连线模式。
 * - 连线模式：点击源节点手柄后，再点击目标节点即建立一条 CONTEXT 边（环检测由 [com.ninef.rikkallm.data.graph.GraphStore] 负责）。
 * - Inspect 浮层：展示节点详情、入边（逐条 Prune）与"以该节点为目标的上下文预览"（节点数/消息数/估算 token）。
 *
 * 选中态 [selected] 由父级持有，便于在页面层联动 [preview]（经 [com.ninef.rikkallm.data.graph.GraphOrchestrator.inspect] 取得）。
 */
@Composable
fun GraphCanvas(
    graph: ConversationGraph,
    selected: Uuid?,
    onSelectedChange: (Uuid?) -> Unit,
    onNodeMoved: (Uuid, Float, Float) -> Unit,
    onConnect: (Uuid, Uuid) -> Unit,
    onPruneEdge: (Uuid) -> Unit,
    preview: ContextPreview?,
    /** 合并模式：开启后点击节点切换多选（用于 Merge 分支），而非单选取 Inspect。 */
    mergeMode: Boolean = false,
    /** 合并模式下的多选集合（高亮显示）。 */
    multiSelected: Set<Uuid> = emptySet(),
    /** 合并模式下点击节点的多选切换回调。 */
    onToggleSelect: (Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var connecting by remember { mutableStateOf<Uuid?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val overrides = remember { mutableStateOf<Map<Uuid, Offset>>(emptyMap()) }

    val nodePos = remember(graph.nodes, pan, scale, overrides.value) {
        graph.nodes.associate { node ->
            val base = overrides.value[node.id] ?: Offset(node.x, node.y)
            node.id to base * scale + pan
        }
    }

    Box(modifier.fillMaxSize()) {
        // 背景层：平移 / 缩放 + 绘制边
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoom, _ ->
                        pan += panChange
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        pointer = centroid
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = NODE_WIDTH.toPx() / 2f
                val cy = NODE_HEIGHT.toPx() / 2f
                graph.edges.forEach { edge ->
                    if (edge.type == GraphEdgeType.PRUNED) return@forEach // 剪枝记录不绘制为连线
                    val a = nodePos[edge.from] ?: return@forEach
                    val b = nodePos[edge.to] ?: return@forEach
                    drawLine(
                        color = if (edge.type == GraphEdgeType.SOURCE) Color.Gray else Color(0xFF6C8CEFUL),
                        start = a + Offset(cx, cy),
                        end = b + Offset(cx, cy),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                connecting?.let { from ->
                    val a = nodePos[from] ?: return@let
                    drawLine(
                        color = Color(0xFF4CAF50UL),
                        start = a + Offset(cx, cy),
                        end = pointer,
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        // 节点层
        graph.nodes.forEach { node ->
            val pos = nodePos[node.id] ?: return@forEach
            val isSelected = selected == node.id || (mergeMode && node.id in multiSelected)
            Box(
                Modifier
                    .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                    .pointerInput(node.id, scale) {
                        detectDragGestures(
                            onDragStart = {},
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val base = overrides.value[node.id] ?: Offset(node.x, node.y)
                                overrides.value = overrides.value + (node.id to (base + dragAmount / scale))
                            },
                            onDragEnd = {
                                val final = overrides.value[node.id] ?: Offset(node.x, node.y)
                                onNodeMoved(node.id, final.x, final.y)
                            }
                        )
                    }
            ) {
                GraphNodeCard(
                    node = node,
                    selected = isSelected,
                    isConnectingSource = connecting == node.id,
                    enableConnect = !mergeMode,
                    onTap = {
                        if (mergeMode) {
                            onToggleSelect(node.id)
                        } else if (connecting != null && connecting != node.id) {
                            onConnect(connecting!!, node.id)
                            connecting = null
                        } else {
                            onSelectedChange(node.id)
                        }
                    },
                    onConnectHandle = { connecting = if (connecting == node.id) null else node.id },
                )
            }
        }

        // Inspect 浮层
        selected?.let { id ->
            val node = graph.nodes.firstOrNull { it.id == id }
            if (node != null) {
                val incoming = graph.edges.filter { it.to == id }
                NodeInspectSheet(
                    node = node,
                    incomingEdges = incoming,
                    preview = preview,
                    onPruneEdge = onPruneEdge,
                    onDismiss = { onSelectedChange(null) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }

        // 连线模式提示
        if (connecting != null) {
            Text(
                "连线模式：点击目标节点完成连接，点击空白取消",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun GraphNodeCard(
    node: GraphNode,
    selected: Boolean,
    isConnectingSource: Boolean,
    enableConnect: Boolean = true,
    onTap: () -> Unit,
    onConnectHandle: () -> Unit,
) {
    Box(Modifier.width(NODE_WIDTH).height(NODE_HEIGHT)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onTap() },
            colors = CardDefaults.cardColors(
                containerColor = when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    isConnectingSource -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(node.kind.name, style = MaterialTheme.typography.labelSmall)
                Text(
                    node.label.ifBlank { node.sourceRef.take(8) },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        if (enableConnect) {
            TextButton(
                onClick = onConnectHandle,
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).padding(0.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun NodeInspectSheet(
    node: GraphNode,
    incomingEdges: List<GraphEdge>,
    preview: ContextPreview?,
    onPruneEdge: (Uuid) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("节点详情", style = MaterialTheme.typography.titleMedium)
        Text("类型：${node.kind.name}")
        Text("标签：${node.label.ifBlank { "(无)" }}")
        Text("引用：${node.sourceRef}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "以此节点为目标的上下文预览：",
            style = MaterialTheme.typography.labelMedium,
        )
        if (preview != null) {
            Text(
                "节点 ${preview.nodeCount} · 消息 ${preview.messageCount} · 估算 ${preview.tokenEstimate} token",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text("将发送的上下文：", style = MaterialTheme.typography.labelMedium)
            if (preview.messages.isEmpty()) {
                Text("（无消息）", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        )
                        .padding(8.dp),
                ) {
                    preview.messages.forEach { msg ->
                        val text = msg.parts
                            .filterIsInstance<UIMessagePart.Text>()
                            .joinToString("\n") { it.text }
                            .trim()
                        if (text.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(
                                    msg.role.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        } else {
            Text("（计算中…）", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Text("入边（Prune 可移除，不删数据）：", style = MaterialTheme.typography.labelMedium)
        if (incomingEdges.isEmpty()) {
            Text("（无入边）", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                incomingEdges.forEach { edge ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "← ${edge.from}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onPruneEdge(edge.id) }) { Text("Prune") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("关闭") }
    }
}
