package com.capti.engine

import com.capti.data.CaptionEntry
import com.capti.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FunAsrEngine @Inject constructor(
    private val settingsRepository: SettingsRepository
) : SpeechEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null

    private val _results = MutableSharedFlow<CaptionEntry>(extraBufferCapacity = 64)
    override val results: Flow<CaptionEntry> = _results

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: Flow<Boolean> = _isConnected

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    var latencyMode: String = "medium"

    override suspend fun start() {
        val url = settingsRepository.serverUrl.first()
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                val config = JSONObject().apply {
                    put("latency_mode", latencyMode)
                    put("is_speaking", true)
                }
                webSocket.send(config.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseResult(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                // 自动重连
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (_isConnected.value.not()) {
                        start()
                    }
                }
            }
        })
    }

    override suspend fun stop() {
        val endMsg = JSONObject().apply {
            put("is_speaking", false)
        }
        webSocket?.send(endMsg.toString())
        webSocket?.close(1000, "stopped")
        webSocket = null
        _isConnected.value = false
    }

    override suspend fun feedAudio(data: ByteArray) {
        webSocket?.send(data.toByteString(0, data.size))
    }

    fun setLatency(mode: String) {
        latencyMode = mode
        val msg = JSONObject().apply {
            put("latency_mode", mode)
        }
        webSocket?.send(msg.toString())
    }

    private fun parseResult(text: String) {
        scope.launch {
            try {
                val json = JSONObject(text)
                val resultText = json.optString("text", "")
                if (resultText.isBlank()) return@launch

                val isFinal = json.optBoolean("is_final", false)
                val speakerId = json.optInt("speaker_id", 0)
                val isOverlap = json.optBoolean("is_overlap", false)

                _results.emit(
                    CaptionEntry(
                        text = resultText,
                        speakerId = if (isOverlap) -1 else speakerId,
                        isFinal = isFinal,
                        isOverlap = isOverlap
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
