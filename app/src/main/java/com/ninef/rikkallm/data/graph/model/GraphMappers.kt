package com.ninef.rikkallm.data.graph.model

import com.ninef.rikkallm.data.model.MessageNode
import kotlin.uuid.Uuid

/**
 * 图领域模型与现有 [MessageNode] / [me.rerere.ai.ui.UIMessage] 之间的映射。
 * 图以 [GraphNode.sourceRef] 指向 [MessageNode.id]，从而在不侵入 [me.rerere.ai.ui.UIMessage]
 * 的前提下复用既有消息体。
 */

/** 将 [MessageNode] 映射为 MESSAGE 类型的图节点。 */
fun MessageNode.toGraphNode(conversationId: Uuid): GraphNode =
    GraphNode(
        conversationId = conversationId,
        kind = GraphNodeKind.MESSAGE,
        sourceRef = id.toString(),
        label = roleLabel(),
    )

private fun MessageNode.roleLabel(): String =
    messages.firstOrNull()?.role?.name ?: "MESSAGE"

/**
 * 根据 [sourceRef] 在 [nodes] 中查找对应的 [MessageNode]。
 * 仅按引用匹配，不复制消息内容。
 */
fun GraphNode.resolveMessageNode(nodes: List<MessageNode>): MessageNode? {
    if (kind != GraphNodeKind.MESSAGE) return null
    val ref = runCatching { Uuid.parse(sourceRef) }.getOrNull() ?: return null
    return nodes.firstOrNull { it.id == ref }
}
