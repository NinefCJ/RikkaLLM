package com.ninef.rikkallm.data.graph.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import me.rerere.ai.ui.UIMessage

/**
 * 图节点类型。
 *
 * - [MESSAGE] 普通聊天消息节点（对应 [com.ninef.rikkallm.data.model.MessageNode] 的选中版本）
 * - [SOURCE] 来源 / 引用（来源溯源）
 * - [TOOL] 工具调用
 * - [REASONING] 推理过程
 * - [EDIT] 文件编辑动作（借鉴 jieapi/aicode 的 editor-first 闭环）
 * - [TERMINAL] Shell 执行（复用 workspace_shell 通道）
 * - [SSH] 远程执行后端（P2 新能力，填补 aicode 对照中的环境缺口）
 * - [MERGE] 汇聚节点：将多条分支（多个入边）的上下文合并为同一目标的路由点，
 *   自身不绑定具体消息，选中它作为目标即得到各分支祖先的并集上下文（"边即上下文"）。
 */
enum class GraphNodeKind {
    MESSAGE,
    SOURCE,
    TOOL,
    REASONING,
    EDIT,
    TERMINAL,
    SSH,
    MERGE,
}

/**
 * 边类型：
 * - [CONTEXT] 主干上下文 / 父依赖（由 sync / rebuild 依据会话顺序自动维护，即"边即上下文"）
 * - [CONTEXT_USER] 用户手动连线/汇聚产生的上下文边；同样参与装配，但 sync 不会自动增删，保留用户编辑
 * - [PRUNED] 剪枝记录：标记"某对相邻节点之间不应存在主干 CONTEXT 边"，使 sync 在自动重建时不补全该缺口
 * - [SOURCE] 来源溯源（不进入上下文，仅用于证明某片段出自何处）
 */
enum class GraphEdgeType {
    CONTEXT,
    CONTEXT_USER,
    PRUNED,
    SOURCE,
}

@Serializable
data class GraphNode(
    val id: Uuid = Uuid.random(),
    val conversationId: Uuid,
    val kind: GraphNodeKind,
    /** 指向 MessageNode.id / 来源片段 / Tool id 的引用（字符串以兼容异构引用） */
    val sourceRef: String,
    val label: String = "",
    val state: String = "PENDING",
    val x: Float = 0f,
    val y: Float = 0f,
)

@Serializable
data class GraphEdge(
    val id: Uuid = Uuid.random(),
    val conversationId: Uuid,
    val from: Uuid,
    val to: Uuid,
    val type: GraphEdgeType,
)

/**
 * 一次"将发送给模型"的装配结果。
 * [nodeIds] 为拓扑序的节点 id；[messages] 为对应的 [UIMessage] 序列；
 * [tokenEstimate] 为 token 预算估算（由装配器填充）。
 */
data class GraphContext(
    val nodeIds: List<Uuid> = emptyList(),
    val messages: List<UIMessage> = emptyList(),
    val tokenEstimate: Int = 0,
)
