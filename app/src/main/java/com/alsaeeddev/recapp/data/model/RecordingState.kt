package com.alsaeeddev.recapp.data.model

sealed class RecordingState {
    object Idle : RecordingState()
    data class Countdown(val secondsRemaining: Int) : RecordingState()
    data class Recording(val durationMs: Long) : RecordingState()
    data class Paused(val durationMs: Long) : RecordingState()
    object Processing : RecordingState()
    data class Error(val message: String) : RecordingState()
}
