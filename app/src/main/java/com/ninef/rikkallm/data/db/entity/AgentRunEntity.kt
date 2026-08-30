package com.ninef.rikkallm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import com.ninef.rikkallm.data.ai.subagent.SubAgentConfig
import com.ninef.rikkallm.data.ai.subagent.SubAgentRun
import com.ninef.rikkallm.data.ai.subagent.SubAgentRunStatus
import com.ninef.rikkallm.utils.JsonInstant

/**
 * 子智能体运行记录（Agent Store 持久化实体）。
 *
 * 保存 subagent 每次运行的状态机轨迹（RUNNING -> COMPLETED/FAILED/TIMEOUT），
 * 支持进程重启后的历史恢复、查看与审计。[configJson] 保存完整执行配置
 * （含 model/provider），便于复盘重放；role/task 冗余为列便于索引与审计查询。
 */
@Entity(
    tableName = "agent_runs",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
    ],
)
data class AgentRunEntity(
    @PrimaryKey
    @ColumnInfo("run_id")
    val runId: String,
    @ColumnInfo("status")
    val status: String,
    @ColumnInfo("role")
    val role: String,
    @ColumnInfo("task")
    val task: String,
    @ColumnInfo("output")
    val output: String? = null,
    @ColumnInfo("error")
    val error: String? = null,
    @ColumnInfo("config_json")
    val configJson: String,
    @ColumnInfo("created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo("completed_at")
    val completedAtEpochMs: Long? = null,
) {
    fun toRun(): SubAgentRun = SubAgentRun(
        runId = runId,
        config = runCatching {
            JsonInstant.decodeFromString<SubAgentConfig>(configJson)
        }.getOrElse {
            // 配置反序列化失败时保留最小可读信息（不抛，避免历史查询整体失败）
            SubAgentConfig(
                role = role,
                task = task,
                model = Model(),
                provider = ProviderSetting.OpenAI(),
            )
        },
        status = runCatching { SubAgentRunStatus.valueOf(status) }
            .getOrDefault(SubAgentRunStatus.FAILED),
        output = output,
        error = error,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
    )

    companion object {
        fun from(run: SubAgentRun): AgentRunEntity = AgentRunEntity(
            runId = run.runId,
            status = run.status.name,
            role = run.config.role,
            task = run.config.task,
            output = run.output,
            error = run.error,
            configJson = JsonInstant.encodeToString(run.config),
            createdAtEpochMs = run.createdAtEpochMs,
            completedAtEpochMs = run.completedAtEpochMs,
        )
    }
}
