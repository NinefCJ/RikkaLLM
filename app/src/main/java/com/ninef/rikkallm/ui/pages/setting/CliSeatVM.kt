package com.ninef.rikkallm.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.cliseat.CliSeatConfig
import com.ninef.rikkallm.data.cliseat.CliSeatStore

class CliSeatVM(private val store: CliSeatStore) : ViewModel() {
    val seats: StateFlow<List<CliSeatConfig>> = store.stateFlow.map { it.seats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSeat(seat: CliSeatConfig) = viewModelScope.launch { store.addSeat(seat) }

    fun removeSeat(id: String) = viewModelScope.launch { store.removeSeat(id) }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { store.setEnabled(id, enabled) }
}
