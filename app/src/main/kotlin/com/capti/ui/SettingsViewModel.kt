package com.capti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capti.data.SettingsRepository
import com.capti.engine.FunAsrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val funAsrEngine: FunAsrEngine
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("ws://192.168.1.100:10095")
    val serverUrl: StateFlow<String> = _serverUrl

    private val _latencyMode = MutableStateFlow("medium")
    val latencyMode: StateFlow<String> = _latencyMode

    init {
        viewModelScope.launch {
            settingsRepository.serverUrl.collect { url ->
                _serverUrl.value = url
            }
        }
        viewModelScope.launch {
            settingsRepository.latencyMode.collect { mode ->
                _latencyMode.value = mode
            }
        }
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        viewModelScope.launch {
            settingsRepository.setServerUrl(url)
        }
    }

    fun updateLatencyMode(mode: String) {
        _latencyMode.value = mode
        viewModelScope.launch {
            settingsRepository.setLatencyMode(mode)
            funAsrEngine.setLatency(mode)
        }
    }
}
