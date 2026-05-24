package com.capti.speaker

import androidx.compose.ui.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeakerManager @Inject constructor() {

    private val colors = listOf(
        Color(0xFF4FC3F7), // 浅蓝
        Color(0xFFAED581), // 浅绿
        Color(0xFFFFB74D), // 橙色
        Color(0xFFBA68C8), // 紫色
        Color(0xFFE57373), // 红色
        Color(0xFF4DB6AC), // 青色
        Color(0xFFF06292), // 粉色
        Color(0xFFFFD54F), // 黄色
    )

    private val speakerNames = mutableMapOf<Int, String>()

    fun getColor(speakerId: Int): Color {
        return colors[speakerId % colors.size]
    }

    fun getName(speakerId: Int): String {
        return speakerNames.getOrPut(speakerId) { "说话人 ${speakerId + 1}" }
    }

    fun setName(speakerId: Int, name: String) {
        speakerNames[speakerId] = name
    }
}
