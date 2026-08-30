package com.ninef.rikkallm.ui.pages.deepread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.deepread.DeepReadReport
import com.ninef.rikkallm.data.deepread.DeepReadRequest
import com.ninef.rikkallm.data.deepread.DeepReadRunner
import com.ninef.rikkallm.data.deepread.DeepReadStore

class DeepReadVM(
    private val runner: DeepReadRunner,
    private val store: DeepReadStore,
) : ViewModel() {
    val reports: StateFlow<List<DeepReadReport>> = store.stateFlow.map { it.reports }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning = MutableStateFlow(false)
    val stage = MutableStateFlow("")
    val currentReport = MutableStateFlow<DeepReadReport?>(null)

    fun generate(request: DeepReadRequest) {
        viewModelScope.launch {
            isRunning.value = true
            stage.value = "准备中…"
            runCatching {
                val report = runner.run(request) { stage.value = it }
                store.saveReport(report)
                report
            }.onSuccess { report ->
                currentReport.value = report
            }.onFailure {
                currentReport.value = null
                stage.value = "失败：${it.message}"
            }
            isRunning.value = false
        }
    }

    fun loadReport(id: String) {
        viewModelScope.launch {
            currentReport.value = store.getReport(id)
        }
    }

    fun deleteReport(id: String) {
        viewModelScope.launch {
            store.deleteReport(id)
            if (currentReport.value?.id == id) currentReport.value = null
        }
    }
}
