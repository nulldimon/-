package com.example.voice

sealed class VoiceCommand {
    // 1. Flashlight
    data class SetFlashlight(val enabled: Boolean) : VoiceCommand()
    data object ToggleFlashlight : VoiceCommand()
    data object CheckFlashlight : VoiceCommand()

    // 2. Wi-Fi & Internet
    data class SetWifi(val enabled: Boolean) : VoiceCommand()
    data object CheckWifiAndInternet : VoiceCommand()
    data object OpenWifiSettings : VoiceCommand()

    // 3. Bluetooth
    data class SetBluetooth(val enabled: Boolean) : VoiceCommand()
    data object CheckBluetooth : VoiceCommand()
    data object OpenBluetoothSettings : VoiceCommand()

    // 4. Screen Brightness
    data class AdjustBrightness(val brighter: Boolean) : VoiceCommand()
    data class SetBrightnessExtreme(val max: Boolean) : VoiceCommand()
    data class SetAutoBrightness(val enabled: Boolean) : VoiceCommand()
    data object CheckBrightness : VoiceCommand()

    // 5. Volume & Speech settings
    data object VolumeUp : VoiceCommand()
    data object VolumeDown : VoiceCommand()
    data object VolumeMax : VoiceCommand()
    data object VolumeMute : VoiceCommand()
    data object RingVolumeUp : VoiceCommand()
    data object RingVolumeDown : VoiceCommand()
    data object MediaVolumeUp : VoiceCommand()
    data object MediaVolumeDown : VoiceCommand()
    data object CheckVolume : VoiceCommand()
    data object SpeakLouder : VoiceCommand()
    data object SpeakQuieter : VoiceCommand()
    data object SpeakSlower : VoiceCommand()
    data object SpeakFaster : VoiceCommand()
    data object SpeakSimpler : VoiceCommand()

    // 6 & 7. Phone, Contacts, WhatsApp & Messages
    data object OpenWhatsApp : VoiceCommand()
    data object CloseWhatsApp : VoiceCommand()
    data class CallContact(val contactQuery: String, val preferWhatsApp: Boolean = false) : VoiceCommand()
    data class VideoCallContact(val contactQuery: String) : VoiceCommand()
    data class CallPhoneNumber(val digits: String) : VoiceCommand()
    data object CallLastContact : VoiceCommand()
    data object AnswerCall : VoiceCommand()
    data object RejectCall : VoiceCommand()
    data object EndCall : VoiceCommand()
    data object EnableSpeakerphone : VoiceCommand()
    data object DisableSpeakerphone : VoiceCommand()
    data object ShowMissedCalls : VoiceCommand()
    data class PrepareMessage(val recipient: String, val messageText: String) : VoiceCommand()
    data class ReplyToMessage(val recipient: String?, val messageText: String?) : VoiceCommand()
    data object ReadMessages : VoiceCommand()

    // 8. Universal App Launcher & App UI Interaction
    data class OpenApp(val appNameQuery: String) : VoiceCommand()
    data class ClickUiElement(val elementQuery: String) : VoiceCommand()

    // 9. Video & YouTube
    data object OpenYouTubeHome : VoiceCommand()
    data class OpenYouTubeSearch(val query: String) : VoiceCommand()
    data object OpenYouTubeSubscriptions : VoiceCommand()
    data object CloseYouTube : VoiceCommand()
    data object PauseVideo : VoiceCommand()
    data object ResumeVideo : VoiceCommand()
    data object StopVideo : VoiceCommand()
    data object MediaNext : VoiceCommand()
    data object MediaPrevious : VoiceCommand()
    data object SeekForward : VoiceCommand()
    data object SeekBackward : VoiceCommand()
    data object RestartVideo : VoiceCommand()
    data object SpeedUp : VoiceCommand()
    data object SpeedDown : VoiceCommand()
    data object EnterFullscreen : VoiceCommand()
    data object ExitFullscreen : VoiceCommand()
    data object OpenFirstResult : VoiceCommand()

    // 10. Screen Reading & Blind Assistance
    data object ReadScreen : VoiceCommand()
    data object ContinueReadingScreen : VoiceCommand()
    data object StopSpeaking : VoiceCommand()
    data object ReadMissedNotifications : VoiceCommand()
    data object ReadCurrentApp : VoiceCommand()
    data object ScrollDown : VoiceCommand()
    data object ScrollUp : VoiceCommand()
    data class VisionNotSupportedHint(val featureName: String) : VoiceCommand()

    // 11. Android System Controls
    data object LockScreen : VoiceCommand()
    data object GoHome : VoiceCommand()
    data object GoBack : VoiceCommand()
    data object OpenRecents : VoiceCommand()
    data object TakeScreenshot : VoiceCommand()
    data class SetAutoRotate(val enabled: Boolean) : VoiceCommand()
    data class SetDoNotDisturb(val enabled: Boolean) : VoiceCommand()
    data object OpenLocationSettings : VoiceCommand()
    data object ReadTime : VoiceCommand()
    data object ReadDate : VoiceCommand()
    data object ReadDayOfWeek : VoiceCommand()
    data object ReadBattery : VoiceCommand()

    // 12. Alarms, Timers & Reminders
    data class SetAlarm(val hour: Int?, val minute: Int?) : VoiceCommand()
    data object DisableAlarm : VoiceCommand()
    data object SnoozeAlarm : VoiceCommand()
    data class SetTimer(val minutes: Int) : VoiceCommand()
    data class SetReminder(val title: String, val hour: Int?, val minute: Int?, val inMinutes: Int?) : VoiceCommand()
    data object ReadReminders : VoiceCommand()
    data object CancelReminders : VoiceCommand()

    // 13. Weather & Info
    data class ReadWeather(
        val tomorrow: Boolean = false,
        val askPrecipitation: Boolean = false,
        val cityOverride: String? = null
    ) : VoiceCommand()
    data object ReadCurrencyRates : VoiceCommand()

    // 14. Maps & Navigation
    data object OpenMaps : VoiceCommand()
    data class NavigateToPlace(val query: String) : VoiceCommand()

    // 15. Camera
    data class OpenCamera(val videoMode: Boolean = false) : VoiceCommand()

    // 16 & 17. Emergency SOS & Assistant Control
    data object NeedHelpMenu : VoiceCommand()
    data object CallForHelpSos : VoiceCommand()
    data object CallEmergencyServices : VoiceCommand()
    data object AssistantWakeCheck : VoiceCommand()
    data object AssistantPingCheck : VoiceCommand()
    data object StopAssistantService : VoiceCommand()
    data object RepeatLastResponse : VoiceCommand()
    data object RepeatLastCommand : VoiceCommand()
    data object ExplainNotUnderstood : VoiceCommand()
    data object Help : VoiceCommand()
    data object ConfirmAction : VoiceCommand()
    data object CancelAction : VoiceCommand()
    data class SelectDisambiguationIndex(val index: Int) : VoiceCommand()
    data object FindMyPhone : VoiceCommand()

    data class Unknown(val rawText: String, val shouldPromptRepeat: Boolean = false) : VoiceCommand()
}

data class CommandLogEntry(
    val id: Long,
    val timestampLabel: String,
    val spokenText: String,
    val actionSummary: String,
    val succeeded: Boolean
)
