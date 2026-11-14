package com.example.data

data class VoiceSettings(
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "слушай",
    val strictCommandsOnly: Boolean = true,
    val returnToAppOnClose: Boolean = true,
    val speakConfirmations: Boolean = true,
    val simpleSpeechMode: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val autoOpenFirstVideo: Boolean = false,
    val weatherCity: String = "Москва",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = ""
)
