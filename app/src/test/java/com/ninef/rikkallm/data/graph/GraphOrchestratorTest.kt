package com.ninef.rikkallm.data.graph

import com.ninef.rikkallm.data.ai.graph.ContextAssembler
import com.ninef.rikkallm.data.ai.graph.ContextPreview
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import com.ninef.rikkallm.data.model.Conversation
import com.ninef.rikkallm.data.model.MessageNode
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class GraphOrchestratorTest {
    private val cid = Uuid.random()
    private val assistantId = Uuid.random()

    private fun msg(text: String): UIMessage = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun conversationWithMessages(vararg texts: String): Conversation = Conversation(
        id = cid,
        assistantId = assistantId,
        messageNodes = texts.map { MessageNode.of(msg(it)) },
    )

    /** 在已有 [base] 消息节点基础上追加若干新消息，复用 base 的 id（用于测试 sync 的增量语义）。 */
    private fun conversationWith(base: List<MessageNode>, vararg extra: String): Conversation = Conversation(
        id = cid,
        assistantId = assistantId,
        messageNodes = base + extra.map { MessageNode.of(msg(it)) },
    )

    @Test
    fun `rebuildFromConversation builds chain of N nodes and N-1 CONTEXT edges`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val conversation = conversationWithMessages("a", "b", "c", "d")

        orchestrator.rebuildFromConversation(conversation)

        val g = store.snapshot(cid)
        assertEquals(4, g.nodes.size)
        assertEquals(3, g.edges.size)
        assertTrue(g.edges.all { it.type == GraphEdgeType.CONTEXT })
        val byRef = g.nodes.associateBy { it.sourceRef }
        val chain = conversation.messageNodes.map { it.id.toString() }
        assertEquals(chain.toSet(), byRef.keys.toSet())
    }

    @Test
    fun `connect rejects edge that would create a cycle`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        orchestrator.rebuildFromConversation(conversationWithMessages("a", "b", "c"))
        val g = store.snapshot(cid)
        val ids = g.nodes.map { it.id }
        val head = ids.first()
        val tail = ids.last()

        val cyclic = orchestrator.connect(cid, from = tail, to = head)
        assertFalse(cyclic)

        val ok = orchestrator.connect(cid, from = head, to = tail)
        assertTrue(ok)
    }

    @Test
    fun `prune removes the given context edge and records a PRUNED edge`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        orchestrator.rebuildFromConversation(conversationWithMessages("a", "b", "c"))
        val edgeToRemove = store.snapshot(cid).edges.first()

        orchestrator.prune(cid, edgeToRemove.id)

        val after = store.snapshot(cid)
        assertFalse(after.edges.any { it.id == edgeToRemove.id })
        assertTrue(
            after.edges.any {
                it.type == GraphEdgeType.PRUNED && it.from == edgeToRemove.from && it.to == edgeToRemove.to
            },
        )
    }

    @Test
    fun `rebuild is idempotent and clears previous graph`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        orchestrator.rebuildFromConversation(conversationWithMessages("a", "b"))
        orchestrator.rebuildFromConversation(conversationWithMessages("x", "y", "z", "w"))

        val g = store.snapshot(cid)
        assertEquals(4, g.nodes.size)
        assertEquals(3, g.edges.size)
    }

    @Test
    fun `inspect returns context preview for the assembled graph`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val conversation = conversationWithMessages("alpha", "bravo", "charlie")
        orchestrator.rebuildFromConversation(conversation)
        val graph = store.snapshot(cid)

        val preview: ContextPreview = orchestrator.inspect(conversation, graph, null)

        assertEquals(3, preview.nodeCount)
        assertEquals(3, preview.messageCount)
        assertTrue(preview.tokenEstimate > 0)
    }

    @Test
    fun `sync appends only missing nodes and preserves existing graph edits`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val base = conversationWithMessages("a", "b")
        orchestrator.rebuildFromConversation(base)

        // 先 prune 一条边，证明 sync 不会清除用户编辑（剪枝以 PRUNED 边保留）
        val aNode = store.snapshot(cid).nodes.first { it.sourceRef == base.messageNodes[0].id.toString() }
        val bNode = store.snapshot(cid).nodes.first { it.sourceRef == base.messageNodes[1].id.toString() }
        val abEdge = store.snapshot(cid).edges.first { it.from == aNode.id && it.to == bNode.id }
        orchestrator.prune(cid, abEdge.id)

        // 同一会话新增一条消息，触发增量同步
        val grown = conversationWith(base.messageNodes, "c")
        orchestrator.syncFromConversation(grown)

        val g = store.snapshot(cid)
        assertEquals(3, g.nodes.size) // 仅追加了 "c" 节点
        assertTrue(g.edges.any { it.type == GraphEdgeType.PRUNED && it.from == aNode.id && it.to == bNode.id })
        assertFalse(g.edges.any { it.type == GraphEdgeType.CONTEXT && it.from == aNode.id && it.to == bNode.id })
        val cRef = grown.messageNodes[2].id.toString()
        val cNode = g.nodes.first { it.sourceRef == cRef }
        assertTrue(g.edges.any { it.to == cNode.id }) // 新节点已接入链
    }

    @Test
    fun `sync is idempotent and creates no duplicate nodes`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val base = conversationWithMessages("a", "b")
        orchestrator.rebuildFromConversation(base)

        val grown = conversationWith(base.messageNodes, "c", "d")
        orchestrator.syncFromConversation(grown)
        orchestrator.syncFromConversation(grown) // 重复调用

        val g = store.snapshot(cid)
        assertEquals(4, g.nodes.size)
        assertEquals(3, g.edges.size)
    }

    @Test
    fun `merge creates a convergence node with one incoming edge per source`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        orchestrator.rebuildFromConversation(conversationWithMessages("a", "b", "c"))
        val ids = store.snapshot(cid).nodes.map { it.id }

        val mergeId = orchestrator.merge(
            cid,
            sourceNodeIds = ids,
            sourceRef = "merge",
            kind = GraphNodeKind.MERGE,
            label = "合并",
        )

        val g = store.snapshot(cid)
        assertEquals(4, g.nodes.size)                         // 原 3 + 1 汇聚节点
        assertEquals(2 + ids.size, g.edges.size)             // rebuild 2 条 + 汇聚 3 条入边
        val mergeNode = g.nodes.first { it.id == mergeId }
        assertEquals(GraphNodeKind.MERGE, mergeNode.kind)
        assertEquals(ids.size, g.edges.count { it.to == mergeId })
    }

    @Test
    fun `sync after regeneration yields context matching currentMessages`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val v1 = Uuid.random(); val v2 = Uuid.random(); val v2b = Uuid.random()

        val base = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode(id = v1, messages = listOf(msg("a")), selectIndex = 0),
                MessageNode(id = v2, messages = listOf(msg("b1")), selectIndex = 0),
            ),
        )
        orchestrator.rebuildFromConversation(base)

        // 用户重新生成 b -> b2（旧节点 v2 被替换）
        val regen = Conversation(
            id = cid,
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode(id = v1, messages = listOf(msg("a")), selectIndex = 0),
                MessageNode(id = v2b, messages = listOf(msg("b2")), selectIndex = 0),
            ),
        )
        orchestrator.syncFromConversation(regen)

        val g = store.snapshot(cid)
        // 陈旧节点 v2 应被清理
        assertFalse(g.nodes.any { it.sourceRef == v2.toString() })
        // 装配结果须等于 currentMessages [a, b2]，不含陈旧 b1
        val assembled = ContextAssembler(store).assemble(regen, g).messages
            .map { (it.parts[0] as UIMessagePart.Text).text }
        assertEquals(listOf("a", "b2"), assembled)
    }

    @Test
    fun `pruned edge survives subsequent sync and excludes its target from context`() = runBlocking {
        val store = FakeGraphStore()
        val orchestrator = GraphOrchestrator(store, ContextAssembler(store))
        val conv = conversationWithMessages("a", "b", "c")
        orchestrator.rebuildFromConversation(conv)

        val nodes = store.snapshot(cid).nodes
        val bId = nodes.first { it.sourceRef == conv.messageNodes[1].id.toString() }.id
        val cId = nodes.first { it.sourceRef == conv.messageNodes[2].id.toString() }.id
        val bcEdge = store.snapshot(cid).edges.first { it.from == bId && it.to == cId }
        orchestrator.prune(cid, bcEdge.id)

        // 再次同步（会话未变）
        orchestrator.syncFromConversation(conv)

        val g = store.snapshot(cid)
        assertTrue(g.edges.any { it.type == GraphEdgeType.PRUNED && it.from == bId && it.to == cId })
        assertFalse(g.edges.any { it.type == GraphEdgeType.CONTEXT && it.from == bId && it.to == cId })

        // 以 c 为目标装配：因 b->c 缺口，b 不应出现在其上下文
        val assembled = ContextAssembler(store).assemble(conv, g).messages
            .map { (it.parts[0] as UIMessagePart.Text).text }
        assertEquals(listOf("c"), assembled)
    }
}
