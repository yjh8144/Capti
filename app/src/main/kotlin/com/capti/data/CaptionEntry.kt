package com.capti.data

data class CaptionEntry(
    val text: String,
    val speakerId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isFinal: Boolean = false,
    val isOverlap: Boolean = false
)
