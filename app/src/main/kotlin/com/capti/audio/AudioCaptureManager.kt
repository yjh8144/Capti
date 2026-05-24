package com.capti.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    var onAudioData: ((ByteArray) -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (_isRecording.value) return@withContext
        if (!hasPermission()) return@withContext

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).also { record ->
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@withContext
            }
            record.startRecording()
            _isRecording.value = true

            val buffer = ByteArray(3200) // 100ms of 16kHz 16-bit mono
            while (_isRecording.value && isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onAudioData?.invoke(buffer.copyOf(read))
                }
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }
}
