package com.ninef.rikkallm.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ninef.rikkallm.data.webmount.WebMountConfig
import com.ninef.rikkallm.data.webmount.WebMountStore

class WebMountVM(private val store: WebMountStore) : ViewModel() {
    val mounts: StateFlow<List<WebMountConfig>> = store.stateFlow.map { it.mounts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMount(mount: WebMountConfig) = viewModelScope.launch { store.addMount(mount) }

    fun removeMount(id: String) = viewModelScope.launch { store.removeMount(id) }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { store.setEnabled(id, enabled) }
}
