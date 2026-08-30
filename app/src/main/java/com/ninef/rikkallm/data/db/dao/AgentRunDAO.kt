package com.ninef.rikkallm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ninef.rikkallm.data.db.entity.AgentRunEntity

@Dao
interface AgentRunDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: AgentRunEntity)

    @Query("SELECT * FROM agent_runs WHERE run_id = :runId")
    suspend fun getById(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs ORDER BY created_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<AgentRunEntity>

    @Query("SELECT * FROM agent_runs WHERE status = :status ORDER BY created_at DESC LIMIT :limit")
    suspend fun listByStatus(status: String, limit: Int): List<AgentRunEntity>

    @Query("SELECT * FROM agent_runs ORDER BY created_at DESC LIMIT :limit")
    fun listRecentFlow(limit: Int): Flow<List<AgentRunEntity>>

    @Query("SELECT COUNT(*) FROM agent_runs")
    suspend fun count(): Int

    @Query("DELETE FROM agent_runs WHERE run_id = :runId")
    suspend fun deleteById(runId: String): Int
}
