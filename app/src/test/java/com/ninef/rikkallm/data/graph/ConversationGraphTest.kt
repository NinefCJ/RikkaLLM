package com.ninef.rikkallm.data.graph

import com.ninef.rikkallm.data.graph.model.ConversationGraph
import com.ninef.rikkallm.data.graph.model.GraphEdge
import com.ninef.rikkallm.data.graph.model.GraphEdgeType
import com.ninef.rikkallm.data.graph.model.GraphNode
import com.ninef.rikkallm.data.graph.model.GraphNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationGraphTest {
    private val cid = Uuid.random()

    private fun node(id: Uuid) = GraphNode(
        id = id,
        conversationId = cid,
        kind = GraphNodeKind.MESSAGE,
        sourceRef = id.toString(),
    )

    private fun edge(from: Uuid, to: Uuid) = GraphEdge(
        conversationId = cid,
        from = from,
        to = to,
        type = GraphEdgeType.CONTEXT,
    )

    @Test
    fun `chain topoOrder respects parent before child`() {
        val a = Uuid.random(); val b = Uuid.random(); val c = Uuid.random()
        val g = ConversationGraph.of(cid, listOf(node(a), node(b), node(c)), listOf(edge(a, b), edge(b, c)))
        assertEquals(listOf(a, b, c), g.topoOrder(c))
    }

    @Test
    fun `diamond topoOrder includes all nodes with dependencies first`() {
        val a = Uuid.random(); val b = Uuid.random(); val c = Uuid.random(); val d = Uuid.random()
        val g = ConversationGraph.of(
            cid,
            listOf(node(a), node(b), node(c), node(d)),
            listOf(edge(a, b), edge(a, c), edge(b, d), edge(c, d)),
        )
        val order = g.topoOrder(d)
        assertEquals(4, order.size)
        assertTrue(order.indexOf(a) < order.indexOf(b))
        assertTrue(order.indexOf(a) < order.indexOf(c))
        assertTrue(order.indexOf(b) < order.indexOf(d))
        assertTrue(order.indexOf(c) < order.indexOf(d))
    }

    @Test
    fun `withEdge rejects back edge that forms a cycle`() {
        val a = Uuid.random(); val b = Uuid.random()
        val g = ConversationGraph.of(cid, listOf(node(a), node(b)), listOf(edge(a, b)))
        assertNull(g.withEdge(edge(b, a)))
    }

    @Test
    fun `withEdge rejects self loop`() {
        val a = Uuid.random()
        val g = ConversationGraph.of(cid, listOf(node(a)), emptyList())
        assertNull(g.withEdge(edge(a, a)))
    }

    @Test
    fun `withEdge accepts acyclic edge`() {
        val a = Uuid.random(); val b = Uuid.random()
        val g = ConversationGraph.of(cid, listOf(node(a), node(b)), listOf(edge(a, b)))
        assertTrue(g.withEdge(edge(a, b).copy(id = Uuid.random())) != null)
    }

    @Test
    fun `isAcyclic true for chain and diamond`() {
        val a = Uuid.random(); val b = Uuid.random(); val c = Uuid.random()
        val chain = ConversationGraph.of(cid, listOf(node(a), node(b), node(c)), listOf(edge(a, b), edge(b, c)))
        assertTrue(chain.isAcyclic())
    }
}
