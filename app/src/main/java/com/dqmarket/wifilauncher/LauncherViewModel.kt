package com.dqmarket.wifilauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dqmarket.wifilauncher.network.NetworkRepository
import com.dqmarket.wifilauncher.network.NetworkSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LauncherUiState(
    val snapshot: NetworkSnapshot? = null,
    val message: String? = null,
    val loading: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NetworkRepository(application)
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh(silent = _uiState.value.snapshot != null)
                delay(3_000)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.value = _uiState.value.copy(loading = true)
            val snap = repo.snapshot()
            _uiState.value = _uiState.value.copy(snapshot = snap, loading = false)
        }
    }

    fun toggleHotspot(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                repo.requestHotspot { ok, err ->
                    _uiState.value = _uiState.value.copy(
                        message = if (ok) "热点已开启" else (err ?: "无法开启热点"),
                    )
                    refresh(silent = true)
                }
            } else {
                repo.stopHotspot { ok ->
                    _uiState.value = _uiState.value.copy(
                        message = if (ok) "热点已关闭" else "无法关闭热点",
                    )
                    refresh(silent = true)
                }
            }
        }
    }

    fun openWifiPanel() = repo.openWifiPanel()
    fun openTetherSettings() = repo.openTetherSettings()
    fun openDataUsage() = repo.openDataUsageSettings()

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
