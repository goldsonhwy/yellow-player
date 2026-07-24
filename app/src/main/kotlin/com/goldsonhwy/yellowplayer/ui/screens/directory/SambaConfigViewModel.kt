package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldsonhwy.yellowplayer.data.model.SambaServer
import com.goldsonhwy.yellowplayer.data.repository.VideoRepository
import com.goldsonhwy.yellowplayer.smb.SambaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SambaConfigUiState(
    val servers: List<SambaServer> = emptyList(),
    val isDiscovering: Boolean = false,
    val discoveredHosts: List<String> = emptyList(),
    val error: String? = null
)

class SambaConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)
    private val sambaClient = SambaClient()

    private val _uiState = MutableStateFlow(SambaConfigUiState())
    val uiState: StateFlow<SambaConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSambaServers().collect { servers ->
                _uiState.value = _uiState.value.copy(servers = servers)
            }
        }
    }

    fun discoverServers() {
        if (_uiState.value.isDiscovering) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiscovering = true, discoveredHosts = emptyList(), error = null)
            val result = sambaClient.discoverServers()
            _uiState.value = result.fold(
                onSuccess = { hosts -> _uiState.value.copy(isDiscovering = false, discoveredHosts = hosts) },
                onFailure = { err -> _uiState.value.copy(isDiscovering = false, error = err.message ?: "扫描失败") }
            )
        }
    }

    fun saveServer(server: SambaServer) {
        viewModelScope.launch {
            try {
                repository.saveSambaServer(server)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(error = "保存失败：${t.message.orEmpty()}")
            }
        }
    }

    fun deleteServer(server: SambaServer) {
        viewModelScope.launch {
            try {
                repository.deleteSambaServer(server)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(error = "删除失败：${t.message.orEmpty()}")
            }
        }
    }

    fun clearDiscovered() {
        _uiState.value = _uiState.value.copy(discoveredHosts = emptyList())
    }
}
