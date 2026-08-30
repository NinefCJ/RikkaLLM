package com.ninef.rikkallm.data.cliseat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * CLI 席位管理器：持有已配置席位的内存缓存，并向 [com.ninef.rikkallm.data.ai.tools.createModelCouncilTool]
 * 提供非挂起读取能力，使外部 CLI 工具可作为席位接入模型议会。
 */
class CliSeatManager(
    private val store: CliSeatStore,
    private val runner: CliSeatRunner,
) {
    private val _seats = MutableStateFlow<List<CliSeatConfig>>(emptyList())

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            store.stateFlow.collect { _seats.value = it.seats }
        }
    }

    /** 当前已启用的席位（非挂起） */
    fun enabledSeats(): List<CliSeatConfig> = _seats.value.filter { it.enabled }

    fun runner(): CliSeatRunner = runner
}
