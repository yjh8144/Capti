package com.capti.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.capti.data.CaptionEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val funAsrEngine: FunAsrEngine,
    private val sherpaEngine: SherpaEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeEngine = MutableStateFlow<EngineType>(EngineType.FUNASR)
    val activeEngine: StateFlow<EngineType> = _activeEngine

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable

    val results: Flow<CaptionEntry> = merge(funAsrEngine.results, sherpaEngine.results)

    private var isRunning = false

    init {
        registerNetworkCallback()
    }

    private val currentEngine: SpeechEngine
        get() = when (_activeEngine.value) {
            EngineType.FUNASR -> funAsrEngine
            EngineType.SHERPA -> sherpaEngine
        }

    suspend fun start() {
        isRunning = true
        currentEngine.start()
    }

    suspend fun stop() {
        isRunning = false
        funAsrEngine.stop()
        sherpaEngine.stop()
    }

    suspend fun feedAudio(data: ByteArray) {
        currentEngine.feedAudio(data)
    }

    private suspend fun switchEngine(type: EngineType) {
        if (_activeEngine.value == type) return
        currentEngine.stop()
        _activeEngine.value = type
        if (isRunning) {
            currentEngine.start()
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isNetworkAvailable.value = true
                scope.launch { switchEngine(EngineType.FUNASR) }
            }

            override fun onLost(network: Network) {
                _isNetworkAvailable.value = false
                scope.launch { switchEngine(EngineType.SHERPA) }
            }
        })
    }

    enum class EngineType {
        FUNASR, SHERPA
    }
}
