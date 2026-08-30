package com.ninef.rikkallm.data.ai.subagent

/**
 * 子智能体运行记录的持久化仓库（Agent Store）。
 *
 * 抽象自 Room DAO，便于在 JVM 单测中注入内存实现、以及在无持久层时降级为纯内存态。
 * 默认实现见 `RoomAgentRunStore`（Room 实体 `agent_runs` 表）。
 */
interface AgentRunStore {
    /** 幂等写入一条运行记录（INSERT OR REPLACE，用于 RUNNING 态与终态两次落盘） */
    suspend fun upsert(run: SubAgentRun)

    suspend fun getById(runId: String): SubAgentRun?

    /** 按启动时间倒序返回最近 [limit] 条 */
    suspend fun listRecent(limit: Int): List<SubAgentRun>

    suspend fun count(): Int
}
