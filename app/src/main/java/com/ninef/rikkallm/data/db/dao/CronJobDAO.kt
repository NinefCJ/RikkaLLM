package com.ninef.rikkallm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.ninef.rikkallm.data.db.entity.CronJobEntity

@Dao
interface CronJobDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: CronJobEntity)

    @Update
    suspend fun update(job: CronJobEntity)

    @Query("SELECT * FROM cron_jobs WHERE job_id = :jobId")
    suspend fun getById(jobId: String): CronJobEntity?

    @Query("SELECT * FROM cron_jobs ORDER BY updated_at DESC")
    fun listFlow(): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs ORDER BY updated_at DESC")
    suspend fun listAll(): List<CronJobEntity>

    /** 扫描已到期且启用的任务（调度器使用） */
    @Query("SELECT * FROM cron_jobs WHERE enabled = 1 AND next_run_at <= :nowEpochMs ORDER BY next_run_at ASC")
    suspend fun listDue(nowEpochMs: Long): List<CronJobEntity>

    @Query("SELECT * FROM cron_jobs WHERE type = :type ORDER BY updated_at DESC")
    fun listByTypeFlow(type: String): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs WHERE type = :type ORDER BY updated_at DESC LIMIT 1")
    suspend fun latestByType(type: String): CronJobEntity?

    @Query("UPDATE cron_jobs SET enabled = :enabled, updated_at = :nowEpochMs WHERE job_id = :jobId")
    suspend fun setEnabled(jobId: String, enabled: Boolean, nowEpochMs: Long): Int

    @Query("UPDATE cron_jobs SET next_run_at = :nextRunAtEpochMs, updated_at = :nowEpochMs WHERE job_id = :jobId")
    suspend fun updateNextRun(jobId: String, nextRunAtEpochMs: Long, nowEpochMs: Long): Int

    @Query("DELETE FROM cron_jobs WHERE job_id = :jobId")
    suspend fun deleteById(jobId: String): Int

    @Query("SELECT COUNT(*) FROM cron_jobs")
    suspend fun count(): Int
}
