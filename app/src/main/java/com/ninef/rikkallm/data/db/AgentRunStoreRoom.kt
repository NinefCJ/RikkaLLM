package com.ninef.rikkallm.data.db

import com.ninef.rikkallm.data.ai.subagent.AgentRunStore
import com.ninef.rikkallm.data.ai.subagent.SubAgentRun
import com.ninef.rikkallm.data.db.dao.AgentRunDAO
import com.ninef.rikkallm.data.db.entity.AgentRunEntity

/** [AgentRunStore] 的 Room 实现，持久化到 `agent_runs` 表。 */
class RoomAgentRunStore(
    private val dao: AgentRunDAO,
) : AgentRunStore {
    override suspend fun upsert(run: SubAgentRun) {
        dao.upsert(AgentRunEntity.from(run))
    }

    override suspend fun getById(runId: String): SubAgentRun? =
        dao.getById(runId)?.toRun()

    override suspend fun listRecent(limit: Int): List<SubAgentRun> =
        dao.listRecent(limit.coerceAtLeast(1)).map { it.toRun() }

    override suspend fun count(): Int = dao.count()
}
