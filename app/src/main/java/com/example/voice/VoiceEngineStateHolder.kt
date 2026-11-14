package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SpeechEngineKind {
    VOSK_OFFLINE,
    SYSTEM_RECOGNIZER,
    NONE
}

enum class ListeningStatus {
    STOPPED,
    INITIALIZING,
    LISTENING,
    PAUSED,
    ERROR
}

data class VoiceRuntimeState(
    val listeningStatus: ListeningStatus = ListeningStatus.STOPPED,
    val activeEngine: SpeechEngineKind = SpeechEngineKind.NONE,
    val partialTranscript: String = "",
    val lastFinalTranscript: String = "",
    val lastActionFeedback: String = "Готов к работе",
    val errorMessage: String? = null,
    val isAccessibilityConnected: Boolean = false,
    val isAppInForeground: Boolean = false,
    val currentForegroundApp: String? = null,
    val isYouTubeOpen: Boolean = false,
    val commandHistory: List<CommandLogEntry> = emptyList()
)

object VoiceEngineStateHolder {
    private val _state = MutableStateFlow(VoiceRuntimeState())
    val state: StateFlow<VoiceRuntimeState> = _state.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale("ru", "RU"))
    private var nextId = 1L

    fun setAppInForeground(inForeground: Boolean) {
        _state.update { it.copy(isAppInForeground = inForeground) }
    }

    fun updateStatus(status: ListeningStatus, engine: SpeechEngineKind? = null, error: String? = null) {
        _state.update { current ->
            current.copy(
                listeningStatus = status,
                activeEngine = engine ?: current.activeEngine,
                errorMessage = error
            )
        }
    }

    fun updatePartialTranscript(text: String) {
        _state.update { it.copy(partialTranscript = text) }
    }

    fun updateAccessibilityStatus(connected: Boolean) {
        _state.update { it.copy(isAccessibilityConnected = connected) }
    }

    fun updateForegroundPackage(packageName: String?) {
        val ytPackages = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.vanced.android.youtube",
            "org.schabi.newpipe"
        )
        _state.update {
            it.copy(
                currentForegroundApp = packageName,
                isYouTubeOpen = packageName in ytPackages || it.isYouTubeOpen && packageName != "com.aistudio.golosgid.hwtblt" && packageName != "com.example"
            )
        }
    }

    fun markYouTubeOpened(opened: Boolean) {
        _state.update { it.copy(isYouTubeOpen = opened) }
    }

    fun recordCommand(spokenText: String, actionSummary: String, succeeded: Boolean) {
        val entry = CommandLogEntry(
            id = nextId++,
            timestampLabel = timeFormatter.format(Date()),
            spokenText = spokenText,
            actionSummary = actionSummary,
            succeeded = succeeded
        )
        _state.update { current ->
            current.copy(
                lastFinalTranscript = spokenText,
                partialTranscript = "",
                lastActionFeedback = actionSummary,
                commandHistory = (listOf(entry) + current.commandHistory).take(25)
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
