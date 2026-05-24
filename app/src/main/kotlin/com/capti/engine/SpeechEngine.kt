package com.capti.engine

import com.capti.data.CaptionEntry
import kotlinx.coroutines.flow.Flow

interface SpeechEngine {
    val results: Flow<CaptionEntry>
    val isConnected: Flow<Boolean>

    suspend fun start()
    suspend fun stop()
    suspend fun feedAudio(data: ByteArray)
}
