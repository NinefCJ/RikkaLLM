package com.ninef.rikkallm.data.ai.graph

import com.ninef.rikkallm.data.graph.GraphStore
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphContext
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import com.ninef.rikkallm.data.graph.model.resolveMessageNode
import com.ninef.rikkallm.data.model.Conversation
import kotlinx.coroutines.flow.first
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 上下文装配器（M2，对应 06 方案挂点②）。
 *
 * 把"将发送给模型的消息序列"从 [Conversation.currentMessages] 的线性映射，
 * 升级为基于 DAG 的拓扑装配：从目标节点沿 CONTEXT / CONTEXT_USER 边做拓扑遍历，按拓扑序收集祖先
 * 节点的选中消息版本。无图 / 图为空时**回退**到 [Conversation.currentMessages]，
 * 保证与现有行为一致、可向后兼容。
 */
class ContextAssembler(
    private val graphStore: GraphStore,
) {
    /**
     * 装配上下文。
     * @param conversation 当前会话（含 [Conversation.messageNodes]）
     * @param graph 当前 DAG；为 null 或不含节点时回退到线性 [Conversation.currentMessages]
     * @param targetNodeId 目标节点 id；为 null 时自动选取图中最后一个 MESSAGE 节点
     */
    fun assemble(
        conversation: Conversation,
        graph: ConversationGraph?,
        targetNodeId: kotlin.uuid.Uuid? = null,
    ): GraphContext {
        if (graph == null || graph.nodes.isEmpty()) {
            val linear = conversation.currentMessages
            return GraphContext(
                nodeIds = emptyList(),
                messages = linear,
                tokenEstimate = estimateTokens(linear),
            )
        }

        val targetId = targetNodeId
            ?: run {
                // 优先取"会话中最后一个 MessageNode 对应的图节点"，避免重新生成落在中间时误选旧的末节点。
                val lastRef = conversation.messageNodes.lastOrNull()?.id?.toString()
                graph.nodes.lastOrNull { it.kind == GraphNodeKind.MESSAGE && it.sourceRef == lastRef }?.id
                    ?: graph.nodes.lastOrNull { it.kind == GraphNodeKind.MESSAGE }?.id
            }
            ?: graph.nodes.last().id

        val ordered = graph.topoOrder(targetId)
        val nodeById = graph.nodes.associateBy { it.id }

        val messages = ordered.mapNotNull { nodeId ->
            val node = nodeById[nodeId] ?: return@mapNotNull null
            val messageNode = node.resolveMessageNode(conversation.messageNodes) ?: return@mapNotNull null
            messageNode.messages.getOrNull(messageNode.selectIndex)
        }

        return GraphContext(
            nodeIds = ordered,
            messages = messages,
            tokenEstimate = estimateTokens(messages),
        )
    }

    /** 直接基于会话加载图并装配，供编排器/UI 便捷调用。 */
    suspend fun assembleFor(conversation: Conversation): GraphContext {
        val graph = graphStore.observeGraph(conversation.id).first()
        return assemble(conversation, graph)
    }

    /** 由装配结果生成预览信息（token / 节点数 / 消息数）。 */
    fun preview(context: GraphContext): ContextPreview = ContextPreview(
        nodeCount = context.nodeIds.size,
        messageCount = context.messages.size,
        tokenEstimate = context.tokenEstimate,
        messages = context.messages,
    )

    /**
     * 粗略 token 估算（启发式：文本长度 / 4）。
     * 后续可替换为模型 tokenizer 或复用 [com.ninef.rikkallm.data.db.dao.MessageNodeDAO]
     * 的 JSON 展开统计逻辑做更精确估算。
     */
    private fun estimateTokens(messages: List<UIMessage>): Int =
        messages.sumOf { msg ->
            msg.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Text -> part.text.length
                    is UIMessagePart.Reasoning -> part.reasoning.length
                    is UIMessagePart.Tool -> part.input.length + part.output.sumOf {
                        (it as? UIMessagePart.Text)?.text?.length ?: 0
                    }
                    else -> 0
                }
            } / 4
        }
}

/** 上下文预览信息（Inspect 浮层展示用）。 */
data class ContextPreview(
    val nodeCount: Int,
    val messageCount: Int,
    val tokenEstimate: Int,
    val messages: List<UIMessage> = emptyList(),
)
