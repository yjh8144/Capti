package com.capti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capti.audio.AudioCaptureManager
import com.capti.data.CaptionEntry
import com.capti.engine.EngineManager
import com.capti.speaker.SpeakerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptionViewModel @Inject constructor(
    private val engineManager: EngineManager,
    private val audioCaptureManager: AudioCaptureManager,
    val speakerManager: SpeakerManager
) : ViewModel() {

    private val _captions = MutableStateFlow<List<CaptionEntry>>(emptyList())
    val captions: StateFlow<List<CaptionEntry>> = _captions

    val activeEngine = engineManager.activeEngine
    val isRecording = audioCaptureManager.isRecording

    init {
        viewModelScope.launch {
            engineManager.results.collect { entry ->
                _captions.update { current ->
                    val updated = current.toMutableList()
                    if (!entry.isFinal && updated.isNotEmpty() &&
                        !updated.last().isFinal &&
                        updated.last().speakerId == entry.speakerId
                    ) {
                        updated[updated.lastIndex] = entry
                    } else {
                        updated.add(entry)
                    }
                    if (updated.size > 200) {
                        updated.subList(0, updated.size - 100).clear()
                    }
                    updated
                }
            }
        }
    }

    fun clearCaptions() {
        _captions.value = emptyList()
    }
}
