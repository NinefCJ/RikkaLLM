package com.ninef.rikkallm.data.ai.graph

import com.ninef.rikkallm.data.graph.FakeGraphStore
import com.ninef.rikkallm.data.graph.GraphOrchestrator
import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import com.ninef.rikkallm.data.model.Conversation
import com.ninef.rikkallm.data.model.MessageNode
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ContextAssemblerTest {
    private val cid = Uuid.random()
    private val assistantId = Uuid.random()

    private fun msg(text: String) = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(text)))

    @Test
    fun `assemble falls back to linear currentMessages when graph is empty`() {
        val conv = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(MessageNode.of(msg("a")), MessageNode.of(msg("b"))),
        )
        val assembler = ContextAssembler(FakeGraphStore())

        val ctx = assembler.assemble(conv, null)

        assertEquals(2, ctx.messages.size)
        assertEquals("a", (ctx.messages[0].parts[0] as UIMessagePart.Text).text)
        assertEquals("b", (ctx.messages[1].parts[0] as UIMessagePart.Text).text)
        assertEquals(emptyList<Uuid>(), ctx.nodeIds)
    }

    @Test
    fun `assemble traverses CONTEXT edges in topological order`() {
        val v1 = Uuid.random(); val v2 = Uuid.random(); val v3 = Uuid.random()
        val conv = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode(id = v1, messages = listOf(msg("a")), selectIndex = 0),
                MessageNode(id = v2, messages = listOf(msg("b")), selectIndex = 0),
                MessageNode(id = v3, messages = listOf(msg("c")), selectIndex = 0),
            ),
        )
        val graph = ConversationGraph.of(
            cid,
            nodes = listOf(
                GraphNode(id = v1, conversationId = cid, kind = GraphNodeKind.MESSAGE, sourceRef = v1.toString()),
                GraphNode(id = v2, conversationId = cid, kind = GraphNodeKind.MESSAGE, sourceRef = v2.toString()),
                GraphNode(id = v3, conversationId = cid, kind = GraphNodeKind.MESSAGE, sourceRef = v3.toString()),
            ),
            edges = listOf(
                GraphEdge(conversationId = cid, from = v1, to = v2, type = GraphEdgeType.CONTEXT),
                GraphEdge(conversationId = cid, from = v2, to = v3, type = GraphEdgeType.CONTEXT),
            ),
        )

        val ctx = ContextAssembler(FakeGraphStore()).assemble(conv, graph)

        assertEquals(3, ctx.messages.size)
        assertEquals(listOf("a", "b", "c"), ctx.messages.map { (it.parts[0] as UIMessagePart.Text).text })
        assertEquals(listOf(v1, v2, v3), ctx.nodeIds)
    }

    @Test
    fun `assemble merges two branches into one union context via a MERGE node`() {
        val v1 = Uuid.random(); val v2 = Uuid.random(); val merge = Uuid.random()
        val conv = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode(id = v1, messages = listOf(msg("a")), selectIndex = 0),
                MessageNode(id = v2, messages = listOf(msg("b")), selectIndex = 0),
            ),
        )
        // v1、v2 为两条独立分支，均汇入 MERGE 节点；MERGE 自身不绑定消息。
        val graph = ConversationGraph.of(
            cid,
            nodes = listOf(
                GraphNode(id = v1, conversationId = cid, kind = GraphNodeKind.MESSAGE, sourceRef = v1.toString()),
                GraphNode(id = v2, conversationId = cid, kind = GraphNodeKind.MESSAGE, sourceRef = v2.toString()),
                GraphNode(id = merge, conversationId = cid, kind = GraphNodeKind.MERGE, sourceRef = "merge"),
            ),
            edges = listOf(
                GraphEdge(conversationId = cid, from = v1, to = merge, type = GraphEdgeType.CONTEXT),
                GraphEdge(conversationId = cid, from = v2, to = merge, type = GraphEdgeType.CONTEXT),
            ),
        )

        val ctx = ContextAssembler(FakeGraphStore()).assemble(conv, graph, targetNodeId = merge)

        // 两条分支的消息都被纳入，且 MERGE 节点计入拓扑但不贡献消息体。
        assertEquals(setOf("a", "b"), ctx.messages.map { (it.parts[0] as UIMessagePart.Text).text }.toSet())
        assertEquals(2, ctx.messages.size)
        assertEquals(3, ctx.nodeIds.size)
    }

    @Test
    fun `assemble of a linear rebuilt graph equals currentMessages`() = runBlocking {
        val v1 = Uuid.random(); val v2 = Uuid.random(); val v3 = Uuid.random()
        val conv = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode(id = v1, messages = listOf(msg("a")), selectIndex = 0),
                MessageNode(id = v2, messages = listOf(msg("b")), selectIndex = 0),
                MessageNode(id = v3, messages = listOf(msg("c")), selectIndex = 0),
            ),
        )
        val store = FakeGraphStore()
        GraphOrchestrator(store, ContextAssembler(store)).rebuildFromConversation(conv)
        val graph = store.snapshot(cid)

        val ctx = ContextAssembler(store).assemble(conv, graph)

        // 线性图下装配结果须与线性 currentMessages 完全一致（保证默认无行为变化）。
        assertEquals(
            conv.currentMessages.map { (it.parts[0] as UIMessagePart.Text).text },
            ctx.messages.map { (it.parts[0] as UIMessagePart.Text).text },
        )
        assertEquals(conv.currentMessages.size, ctx.messages.size)
    }
}
