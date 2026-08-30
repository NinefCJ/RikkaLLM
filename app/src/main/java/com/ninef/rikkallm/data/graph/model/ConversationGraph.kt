package com.ninef.rikkallm.data.graph.model

import kotlin.uuid.Uuid

/**
 * 内存中的会话 DAG 容器。
 *
 * 持有 [GraphNode] 与 [GraphEdge]，提供：
 * - [ancestors] / [topoOrder]：基于 CONTEXT 边做拓扑遍历，用于"边即上下文"的序列装配；
 * - [isAcyclic]：DFS 三色标记校验无环；
 * - [withEdge]：在加入新边前做环检测，保证图始终无环。
 *
 * 遍历结果带轻量缓存（[ancestorCache] / [topoCache]），避免对同一 target 重复计算。
 */
class ConversationGraph(
    val conversationId: Uuid,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
) {
    private val contextEdges =
        edges.filter { it.type == GraphEdgeType.CONTEXT || it.type == GraphEdgeType.CONTEXT_USER }

    private val childrenMap: Map<Uuid, List<Uuid>> =
        contextEdges.groupBy({ it.from }, { it.to })
    private val parentsMap: Map<Uuid, List<Uuid>> =
        contextEdges.groupBy({ it.to }, { it.from })

    private val ancestorCache = mutableMapOf<Uuid, Set<Uuid>>()
    private val topoCache = mutableMapOf<Uuid, List<Uuid>>()

    /** 返回 [target] 的全部 CONTEXT 祖先（含自身），结果去重并按发现顺序保序。 */
    fun ancestors(target: Uuid): Set<Uuid> = ancestorCache.getOrPut(target) {
        val result = LinkedHashSet<Uuid>()
        fun visit(id: Uuid) {
            if (!result.add(id)) return
            parentsMap[id].orEmpty().forEach(::visit)
        }
        visit(target)
        result
    }

    /**
     * 按拓扑序返回 [target] 的祖先序列（父在前、子在后）。
     * 该顺序即为发送给模型的上下文顺序，保证任一节点的依赖先于自身出现。
     */
    fun topoOrder(target: Uuid): List<Uuid> = topoCache.getOrPut(target) {
        val scope = ancestors(target)
        val indegree = scope.associateWith { id ->
            parentsMap[id].orEmpty().count { it in scope }
        }.toMutableMap()
        val queue = ArrayDeque(scope.filter { indegree[it] == 0 })
        val ordered = mutableListOf<Uuid>()
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            ordered.add(n)
            childrenMap[n].orEmpty().filter { it in scope }.forEach { c ->
                val d = indegree.getValue(c) - 1
                indegree[c] = d
                if (d == 0) queue.add(c)
            }
        }
        // 兜底：若理论上因环导致未完全输出（isAcyclic 应已拦截），追加剩余节点避免丢失
        if (ordered.size != scope.size) {
            scope.filter { it !in ordered }.forEach { ordered.add(it) }
        }
        ordered
    }

    /** 校验 CONTEXT 边构成的子图是否无环（DFS 三色标记：0=白 1=灰 2=黑）。 */
    fun isAcyclic(): Boolean {
        val color = mutableMapOf<Uuid, Int>()
        fun dfs(id: Uuid): Boolean {
            color[id] = 1
            for (c in childrenMap[id].orEmpty()) {
                when (color[c]) {
                    1 -> return false            // 遇到灰色 → 回边 → 有环
                    null, 0 -> if (!dfs(c)) return false
                }
            }
            color[id] = 2
            return true
        }
        return nodes.none { color[it.id] == null && !dfs(it.id) }
    }

    /**
     * 返回加入 [edge] 后的新图；若 [edge] 会造成环（或自环）则返回 null，
     * 调用方据此拒绝非法边，保证图始终无环。
     */
    fun withEdge(edge: GraphEdge): ConversationGraph? {
        if (edge.type in setOf(GraphEdgeType.CONTEXT, GraphEdgeType.CONTEXT_USER) && edge.to == edge.from) return null
        val next = ConversationGraph(conversationId, nodes, edges + edge)
        return if (next.isAcyclic()) next else null
    }

    companion object {
        /** 由节点与边列表构建图。 */
        fun of(
            conversationId: Uuid,
            nodes: List<GraphNode>,
            edges: List<GraphEdge>,
        ): ConversationGraph = ConversationGraph(conversationId, nodes, edges)
    }
}
