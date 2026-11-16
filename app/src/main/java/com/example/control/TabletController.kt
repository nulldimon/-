package com.example.control

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.example.MainActivity
import com.example.data.SettingsRepository
import com.example.data.VoiceSettings
import com.example.service.VoiceBackgroundService
import com.example.service.VoiceControlAccessibilityService
import com.example.service.VoiceNotificationListenerService
import com.example.voice.VoiceCommand
import com.example.voice.VoiceEngineStateHolder
import com.example.voice.VoiceFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ResolvedCallTarget(
    val displayName: String,
    val phoneNumber: String?,
    val waCandidate: ContactMatchCandidate?,
    val preferWhatsApp: Boolean,
    val videoCall: Boolean
)

sealed class PendingConfirmationAction(val expiresAtMs: Long) {
    class SendMessage(
        val recipientQuery: String,
        val resolvedName: String,
        val messageText: String,
        expiresAtMs: Long
    ) : PendingConfirmationAction(expiresAtMs)

    class EmergencyHelpCall(
        val contactName: String,
        val contactPhone: String,
        expiresAtMs: Long
    ) : PendingConfirmationAction(expiresAtMs)

    class ConfirmContactCall(
        val target: ResolvedCallTarget,
        expiresAtMs: Long
    ) : PendingConfirmationAction(expiresAtMs)

    class DisambiguateContact(
        val options: List<ResolvedCallTarget>,
        expiresAtMs: Long
    ) : PendingConfirmationAction(expiresAtMs)

    class ConfirmNumberCall(
        val digits: String,
        val label: String,
        expiresAtMs: Long
    ) : PendingConfirmationAction(expiresAtMs)
}

class TabletController(
    private val context: Context,
    private val feedbackManager: VoiceFeedbackManager,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository? = null
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var pendingAction: PendingConfirmationAction? = null

    @Volatile
    private var lastExecutedCommand: Pair<VoiceCommand, String>? = null

    init {
        PhoneAndDeviceController.ensureTorchStateTracking(context)
    }

    fun hasPendingConfirmation(): Boolean {
        val pending = pendingAction ?: return false
        if (System.currentTimeMillis() > pending.expiresAtMs) {
            pendingAction = null
            return false
        }
        return true
    }

    fun execute(
        command: VoiceCommand,
        spokenText: String,
        settings: VoiceSettings,
        fromVoice: Boolean = true
    ) {
        feedbackManager.setSpeechRate(settings.ttsSpeechRate)

        if (command !is VoiceCommand.RepeatLastCommand &&
            command !is VoiceCommand.RepeatLastResponse &&
            command !is VoiceCommand.ConfirmAction &&
            command !is VoiceCommand.CancelAction &&
            command !is VoiceCommand.SelectDisambiguationIndex &&
            command !is VoiceCommand.Unknown
        ) {
            lastExecutedCommand = command to spokenText
        }

        when (command) {
            // --- 1. Flashlight ---
            is VoiceCommand.SetFlashlight -> handleTorchResult(
                PhoneAndDeviceController.setFlashlight(context, command.enabled),
                spokenText,
                settings
            )
            VoiceCommand.ToggleFlashlight -> handleTorchResult(
                PhoneAndDeviceController.toggleFlashlight(context),
                spokenText,
                settings
            )
            VoiceCommand.CheckFlashlight -> {
                val msg = if (PhoneAndDeviceController.isTorchOn) "Фонарик сейчас включён" else "Фонарик выключен"
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }

            // --- 2. Wi-Fi & Internet ---
            is VoiceCommand.SetWifi -> {
                val msg = PhoneAndDeviceController.setWifiOrOpenPanel(context, command.enabled)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.CheckWifiAndInternet -> {
                val msg = PhoneAndDeviceController.checkWifiAndInternet(context)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.OpenWifiSettings -> {
                val ok = PhoneAndDeviceController.openWifiSettingsPanel(context)
                val msg = if (ok) "Открываю настройки Wi-Fi" else "Не удалось открыть настройки Wi-Fi"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }

            // --- 3. Bluetooth ---
            is VoiceCommand.SetBluetooth -> {
                val msg = PhoneAndDeviceController.setBluetoothOrOpenSettings(context, command.enabled)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.CheckBluetooth -> {
                val msg = PhoneAndDeviceController.checkBluetoothAndHeadphones(context)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.OpenBluetoothSettings -> {
                val ok = PhoneAndDeviceController.openBluetoothSettings(context)
                val msg = if (ok) "Открываю настройки Bluetooth" else "Не удалось открыть настройки Bluetooth"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }

            // --- 4. Brightness ---
            is VoiceCommand.AdjustBrightness -> handleBrightnessResult(
                PhoneAndDeviceController.adjustBrightness(context, command.brighter),
                if (command.brighter) "Яркость увеличена" else "Яркость уменьшена",
                spokenText,
                settings
            )
            is VoiceCommand.SetBrightnessExtreme -> handleBrightnessResult(
                PhoneAndDeviceController.setBrightnessExtreme(context, command.max),
                if (command.max) "Установлена максимальная яркость" else "Установлена минимальная яркость",
                spokenText,
                settings
            )
            is VoiceCommand.SetAutoBrightness -> handleBrightnessResult(
                PhoneAndDeviceController.setAutoBrightness(context, command.enabled),
                if (command.enabled) "Автояркость включена" else "Автояркость отключена",
                spokenText,
                settings
            )
            VoiceCommand.CheckBrightness -> {
                val msg = PhoneAndDeviceController.readBrightnessStatus(context)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }

            // --- 5. Volume & Speech ---
            VoiceCommand.VolumeUp, VoiceCommand.MediaVolumeUp -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                recordAndSpeak(spokenText, "Громкость увеличена", true, forceSpeak = false, settings = settings, shortSpeak = "Громче")
            }
            VoiceCommand.VolumeDown, VoiceCommand.MediaVolumeDown -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                recordAndSpeak(spokenText, "Громкость уменьшена", true, forceSpeak = false, settings = settings, shortSpeak = "Тише")
            }
            VoiceCommand.VolumeMax -> {
                PhoneAndDeviceController.setMaxMediaVolume(context)
                recordAndSpeak(spokenText, "Установлена максимальная громкость", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.VolumeMute -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                VoiceEngineStateHolder.recordCommand(spokenText, "Звук переключён", true)
                feedbackManager.vibrateAck()
            }
            VoiceCommand.RingVolumeUp -> {
                PhoneAndDeviceController.adjustRingVolume(context, raise = true)
                recordAndSpeak(spokenText, "Громкость звонка увеличена", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.RingVolumeDown -> {
                PhoneAndDeviceController.adjustRingVolume(context, raise = false)
                recordAndSpeak(spokenText, "Громкость звонка уменьшена", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.CheckVolume -> {
                val msg = PhoneAndDeviceController.readVolumeSummary(context)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SpeakLouder -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_ACCESSIBILITY, AudioManager.ADJUST_RAISE, 0)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_ACCESSIBILITY, AudioManager.ADJUST_RAISE, 0)
                    } catch (_: Exception) {
                    }
                }
                recordAndSpeak(spokenText, "Говорю громче", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SpeakQuieter -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_ACCESSIBILITY, AudioManager.ADJUST_LOWER, 0)
                    } catch (_: Exception) {
                    }
                }
                recordAndSpeak(spokenText, "Говорю тише", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SpeakSlower -> {
                val nextRate = (settings.ttsSpeechRate - 0.15f).coerceAtLeast(0.65f)
                feedbackManager.setSpeechRate(nextRate)
                scope.launch { settingsRepository?.setTtsSpeechRate(nextRate) }
                recordAndSpeak(spokenText, "Хорошо, говорю медленнее", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SpeakFaster -> {
                val nextRate = (settings.ttsSpeechRate + 0.15f).coerceAtMost(1.5f)
                feedbackManager.setSpeechRate(nextRate)
                scope.launch { settingsRepository?.setTtsSpeechRate(nextRate) }
                recordAndSpeak(spokenText, "Хорошо, говорю быстрее", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SpeakSimpler -> {
                scope.launch { settingsRepository?.setSimpleSpeechMode(true) }
                recordAndSpeak(spokenText, "Хорошо, буду отвечать коротко и понятно", true, forceSpeak = true, settings = settings)
            }

            // --- 6 & 7. Phone, Contacts, WhatsApp & Messages ---
            VoiceCommand.OpenWhatsApp -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) { WhatsAppContactCaller.openWhatsApp(context) }
                    when (res) {
                        is WhatsAppActionResult.OpenedApp ->
                            recordAndSpeak(spokenText, "Открываю ${res.packageLabel}", true, forceSpeak = false, settings = settings, shortSpeak = "Открываю Вацап")
                        WhatsAppActionResult.WhatsAppNotInstalled ->
                            recordAndSpeak(spokenText, "WhatsApp не установлен на устройстве", false, forceSpeak = true, settings = settings)
                        is WhatsAppActionResult.Failed ->
                            recordAndSpeak(spokenText, res.reason, false, forceSpeak = true, settings = settings)
                        else -> {}
                    }
                }
            }
            VoiceCommand.CloseWhatsApp -> {
                val acc = VoiceControlAccessibilityService.instance
                val ok = acc?.performGlobalHome() == true
                if (!ok || settings.returnToAppOnClose) bringMainAppToFront()
                recordAndSpeak(spokenText, "Вышел из WhatsApp", true, forceSpeak = false, settings = settings)
            }
            is VoiceCommand.CallContact -> initiateContactCallWithConfirmation(
                contactQuery = command.contactQuery,
                preferWhatsApp = command.preferWhatsApp,
                videoCall = false,
                spokenText = spokenText
            )
            is VoiceCommand.VideoCallContact -> initiateContactCallWithConfirmation(
                contactQuery = command.contactQuery,
                preferWhatsApp = true,
                videoCall = true,
                spokenText = spokenText
            )
            is VoiceCommand.CallPhoneNumber -> {
                pendingAction = PendingConfirmationAction.ConfirmNumberCall(
                    digits = command.digits,
                    label = "номеру ${command.digits}",
                    expiresAtMs = System.currentTimeMillis() + 25_000L
                )
                val prompt = "Набрать номер ${command.digits}? Скажите «Да» для звонка или «Отмена»."
                recordAndSpeak(spokenText, prompt, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.CallLastContact -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) {
                        PhoneAndDeviceController.getLastCallNumberAndName(context)
                    }
                    res.onSuccess { (number, displayName) ->
                        pendingAction = PendingConfirmationAction.ConfirmNumberCall(
                            digits = number,
                            label = displayName,
                            expiresAtMs = System.currentTimeMillis() + 25_000L
                        )
                        val prompt = "Последний контакт: $displayName. Позвонить? Скажите «Да» или «Отмена»."
                        recordAndSpeak(spokenText, prompt, true, forceSpeak = true, settings = settings)
                    }.onFailure { err ->
                        val msg = err.message ?: "Разрешите журнал звонков в Настройке"
                        recordAndSpeak(spokenText, msg, false, forceSpeak = true, settings = settings)
                    }
                }
            }
            VoiceCommand.AnswerCall -> {
                val (ok, msg) = PhoneAndDeviceController.answerIncomingCall(context)
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.RejectCall, VoiceCommand.EndCall -> {
                val (ok, msg) = PhoneAndDeviceController.rejectOrEndCall(context)
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.EnableSpeakerphone -> {
                val ok = PhoneAndDeviceController.enableSpeakerphone(context)
                val msg = if (ok) "Громкая связь включена" else "Не удалось включить громкую связь"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.DisableSpeakerphone -> {
                val ok = PhoneAndDeviceController.disableSpeakerphone(context)
                val msg = if (ok) "Громкая связь выключена" else "Не удалось выключить громкую связь"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.ShowMissedCalls -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) {
                        PhoneAndDeviceController.getMissedCalls(context)
                    }
                    res.onSuccess { list ->
                        val msg = if (list.isEmpty()) {
                            "Пропущенных звонков нет"
                        } else {
                            val details = list.take(3).joinToString(". ") { info ->
                                if (info.timeLabel.isNotBlank()) "${info.caller} в ${info.timeLabel}" else info.caller
                            }
                            "Пропущенных звонков: ${list.size}. $details"
                        }
                        recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
                    }.onFailure {
                        recordAndSpeak(spokenText, "Разрешите доступ к журналу звонков в разделе Настройка", false, forceSpeak = true, settings = settings)
                    }
                }
            }
            is VoiceCommand.PrepareMessage -> startMessageConfirmation(command.recipient, command.messageText, spokenText)
            is VoiceCommand.ReplyToMessage -> {
                val latestMsg = VoiceNotificationListenerService.getRecentMessages(1).firstOrNull()
                val recipient = command.recipient ?: latestMsg?.title?.takeIf { it.isNotBlank() }
                val text = command.messageText
                when {
                    recipient.isNullOrBlank() ->
                        recordAndSpeak(spokenText, "Скажите, кому и что ответить, например: ответь маме я скоро буду", false, forceSpeak = true, settings = settings)
                    text.isNullOrBlank() ->
                        recordAndSpeak(spokenText, "Чтобы ответить $recipient, скажите: напиши $recipient и текст сообщения", false, forceSpeak = true, settings = settings)
                    else -> startMessageConfirmation(recipient, text, spokenText)
                }
            }
            VoiceCommand.ReadMessages -> {
                if (!VoiceNotificationListenerService.isEnabled(context)) {
                    recordAndSpeak(spokenText, "Включите доступ к уведомлениям в Настройке, чтобы читать сообщения", false, forceSpeak = true, settings = settings)
                } else {
                    val msgs = VoiceNotificationListenerService.getRecentMessages(5)
                    val summary = if (msgs.isEmpty()) {
                        "Новых сообщений нет"
                    } else {
                        msgs.take(3).joinToString(". ") { m ->
                            val who = m.title.ifBlank { m.appLabel }
                            "${m.appLabel}, от $who: ${m.text}"
                        }
                    }
                    recordAndSpeak(spokenText, summary, true, forceSpeak = true, settings = settings)
                }
            }

            // --- 8. Universal App Launcher & UI Element Interaction ---
            is VoiceCommand.OpenApp -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) {
                        AppLauncherController.openAppBySpokenName(context, command.appNameQuery)
                    }
                    when (res) {
                        is AppLaunchResult.Launched ->
                            recordAndSpeak(spokenText, "Открываю ${res.appLabel}", true, forceSpeak = false, settings = settings)
                        is AppLaunchResult.NotFound ->
                            recordAndSpeak(spokenText, "Приложение «${res.query}» не найдено на устройстве", false, forceSpeak = true, settings = settings)
                        is AppLaunchResult.Failed ->
                            recordAndSpeak(spokenText, res.reason, false, forceSpeak = true, settings = settings)
                    }
                }
            }
            is VoiceCommand.ClickUiElement -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc == null) {
                    recordAndSpeak(spokenText, "Включите Спецвозможности для нажатия кнопок в приложениях", false, forceSpeak = true, settings = settings)
                } else {
                    scope.launch(Dispatchers.Main) {
                        val clicked = withContext(Dispatchers.IO) { acc.clickElementByText(command.elementQuery) }
                        if (clicked != null) {
                            recordAndSpeak(spokenText, "Нажимаю: $clicked", true, forceSpeak = false, settings = settings)
                        } else {
                            recordAndSpeak(spokenText, "Кнопка «${command.elementQuery}» не найдена на экране", false, forceSpeak = true, settings = settings)
                        }
                    }
                }
            }

            // --- 9. YouTube & Video ---
            is VoiceCommand.OpenYouTubeSearch -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) {
                        YouTubeLauncher.openYouTubeSearch(context, command.query)
                    }
                    handleYouTubeLaunchResult(res, spokenText, settings, "Ищу на Ютубе: ${command.query}")
                    if (res.launched && !res.blockedByMissingPermission && settings.autoOpenFirstVideo) {
                        delay(2400L)
                        withContext(Dispatchers.IO) {
                            VoiceControlAccessibilityService.instance?.clickFirstVideoResult()
                        }
                    }
                }
            }
            VoiceCommand.OpenYouTubeHome ->
                handleYouTubeLaunchResult(YouTubeLauncher.openYouTubeHome(context), spokenText, settings, "Открываю Ютуб")
            VoiceCommand.OpenYouTubeSubscriptions ->
                handleYouTubeLaunchResult(YouTubeLauncher.openYouTubeSubscriptions(context), spokenText, settings, "Открываю подписки на Ютубе")
            VoiceCommand.CloseYouTube -> closeYouTubeAndReturn(spokenText, settings)
            VoiceCommand.PauseVideo -> pauseVideoReliably(spokenText, settings)
            VoiceCommand.ResumeVideo -> resumeVideoReliably(spokenText, settings)
            VoiceCommand.StopVideo -> {
                pauseAndSilenceMedia()
                recordAndSpeak(spokenText, "Воспроизведение остановлено", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.MediaNext -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc != null) acc.clickNextOrPreviousVideo(next = true) { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                else dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                recordAndSpeak(spokenText, "Следующее видео", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.MediaPrevious -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc != null) acc.clickNextOrPreviousVideo(next = false) { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                else dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                recordAndSpeak(spokenText, "Предыдущее видео", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.SeekForward -> {
                val ok = VoiceControlAccessibilityService.instance?.seekYouTube(forward = true) == true
                if (!ok) dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                recordAndSpeak(spokenText, "Перемотка вперёд на 10 секунд", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.SeekBackward -> {
                val ok = VoiceControlAccessibilityService.instance?.seekYouTube(forward = false) == true
                if (!ok) dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
                recordAndSpeak(spokenText, "Перемотка назад на 10 секунд", true, forceSpeak = false, settings = settings)
            }
            VoiceCommand.RestartVideo -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc != null) {
                    acc.restartYouTubeVideo { ok ->
                        val msg = if (ok) "Видео начато сначала" else "Не удалось найти полосу перемотки"
                        recordAndSpeak(spokenText, msg, ok, forceSpeak = !ok, settings = settings)
                    }
                } else {
                    recordAndSpeak(spokenText, "Для команды «Сначала» включите Спецвозможности", false, forceSpeak = true, settings = settings)
                }
            }
            VoiceCommand.SpeedUp -> adjustYouTubeSpeed(true, spokenText, settings)
            VoiceCommand.SpeedDown -> adjustYouTubeSpeed(false, spokenText, settings)
            VoiceCommand.EnterFullscreen -> setFullscreenWithFeedback(true, spokenText, settings)
            VoiceCommand.ExitFullscreen -> setFullscreenWithFeedback(false, spokenText, settings)
            VoiceCommand.OpenFirstResult -> {
                val ok = VoiceControlAccessibilityService.instance?.clickFirstVideoResult() == true
                val msg = if (ok) "Открываю первое видео" else "Не вижу видео на экране"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }

            // --- 10. Screen Reading & Notifications ---
            VoiceCommand.ReadScreen -> readScreenNow(spokenText, settings)
            VoiceCommand.ContinueReadingScreen -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc == null) {
                    recordAndSpeak(spokenText, "Включите Спецвозможности в Настройке", false, forceSpeak = true, settings = settings)
                } else {
                    acc.scrollForward()
                    scope.launch(Dispatchers.Main) {
                        delay(450L)
                        readScreenNow(spokenText, settings)
                    }
                }
            }
            VoiceCommand.StopSpeaking -> {
                feedbackManager.stopSpeaking()
                VoiceEngineStateHolder.recordCommand(spokenText, "Озвучка остановлена", true)
            }
            VoiceCommand.ReadMissedNotifications -> {
                if (!VoiceNotificationListenerService.isEnabled(context)) {
                    recordAndSpeak(spokenText, "Включите доступ к уведомлениям в Настройке", false, forceSpeak = true, settings = settings)
                } else {
                    val items = VoiceNotificationListenerService.getRecentNotifications(5)
                    val msg = if (items.isEmpty()) "Новых уведомлений нет"
                    else items.take(4).joinToString(". ") {
                        val timeStr = VoiceNotificationListenerService.formatTime(it.timestampMs)
                        val titlePart = if (it.title.isNotBlank()) "${it.title}: " else ""
                        "${it.appLabel} в $timeStr. $titlePart${it.text}"
                    }
                    recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
                }
            }
            VoiceCommand.ReadCurrentApp -> {
                val acc = VoiceControlAccessibilityService.instance
                val label = acc?.readCurrentAppLabel().orEmpty()
                val msg = if (label.isBlank()) "Включите Спецвозможности, чтобы определять текущее приложение"
                else "Сейчас открыто приложение: $label"
                recordAndSpeak(spokenText, msg, label.isNotBlank(), forceSpeak = true, settings = settings)
            }
            VoiceCommand.ScrollDown -> {
                val ok = VoiceControlAccessibilityService.instance?.scrollForward() == true
                recordAndSpeak(spokenText, if (ok) "Ниже" else "Включите Спецвозможности для прокрутки", ok, forceSpeak = !ok, settings = settings)
            }
            VoiceCommand.ScrollUp -> {
                val ok = VoiceControlAccessibilityService.instance?.scrollBackward() == true
                recordAndSpeak(spokenText, if (ok) "Выше" else "Включите Спецвозможности для прокрутки", ok, forceSpeak = !ok, settings = settings)
            }
            is VoiceCommand.VisionNotSupportedHint -> {
                AppLauncherController.openCamera(context, videoMode = false)
                val msg = "Открываю камеру. Обратите внимание: офлайн-${command.featureName} требует отдельной модели распознавания и пока не встроено в это приложение."
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }

            // --- 11. Android System Controls ---
            VoiceCommand.LockScreen -> {
                val ok = VoiceControlAccessibilityService.instance?.lockScreen() == true
                val msg = if (ok) "Блокирую экран" else "Включите Спецвозможности для блокировки экрана"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.GoHome -> {
                val acc = VoiceControlAccessibilityService.instance
                if (acc != null && acc.performGlobalHome()) {
                    VoiceEngineStateHolder.markYouTubeOpened(false)
                    recordAndSpeak(spokenText, "Главный экран", true, forceSpeak = false, settings = settings)
                } else {
                    bringMainAppToFront()
                    recordAndSpeak(spokenText, "Возвращаюсь в ГолосГид", true, forceSpeak = false, settings = settings)
                }
            }
            VoiceCommand.GoBack -> {
                val ok = VoiceControlAccessibilityService.instance?.performGlobalBack() == true
                recordAndSpeak(spokenText, if (ok) "Назад" else "Включите Спецвозможности для кнопки Назад", ok, forceSpeak = !ok, settings = settings)
            }
            VoiceCommand.OpenRecents -> {
                val ok = VoiceControlAccessibilityService.instance?.openRecents() == true
                recordAndSpeak(spokenText, if (ok) "Открываю недавние приложения" else "Включите Спецвозможности для списка приложений", ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.TakeScreenshot -> {
                val ok = VoiceControlAccessibilityService.instance?.takeScreenshotAction() == true
                recordAndSpeak(spokenText, if (ok) "Снимок экрана сделан" else "Включите Спецвозможности для скриншота", ok, forceSpeak = true, settings = settings)
            }
            is VoiceCommand.SetAutoRotate -> {
                val (ok, msg) = PhoneAndDeviceController.setAutoRotate(context, command.enabled)
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            is VoiceCommand.SetDoNotDisturb -> {
                val msg = PhoneAndDeviceController.setDoNotDisturb(context, command.enabled)
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.OpenLocationSettings -> {
                PhoneAndDeviceController.openLocationSettings(context)
                recordAndSpeak(spokenText, "Открываю настройки геолокации", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.ReadTime -> recordAndSpeak(spokenText, formatRussianTime(), true, forceSpeak = true, settings = settings)
            VoiceCommand.ReadDate -> recordAndSpeak(spokenText, formatRussianDate(), true, forceSpeak = true, settings = settings)
            VoiceCommand.ReadDayOfWeek -> recordAndSpeak(spokenText, formatRussianDayOfWeek(), true, forceSpeak = true, settings = settings)
            VoiceCommand.ReadBattery -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val msg = if (pct in 0..100) "Осталось $pct процентов заряда" else "Уровень заряда недоступен"
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }

            // --- 12. Alarms, Timers & Reminders ---
            is VoiceCommand.SetAlarm -> {
                val ok = PhoneAndDeviceController.setAlarm(context, command.hour, command.minute, "Будильник ГолосГид")
                val msg = when {
                    !ok -> "Не найдено приложение Часы для будильника"
                    command.hour != null -> "Ставлю будильник на ${command.hour}:${(command.minute ?: 0).toString().padStart(2, '0')}"
                    else -> "Открываю настройку будильника"
                }
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.DisableAlarm -> {
                val ok = PhoneAndDeviceController.dismissOrShowAlarms(context)
                val msg = if (ok) "Открываю или отключаю будильник" else "Не найдено приложение Часы"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            VoiceCommand.SnoozeAlarm -> {
                val ok = PhoneAndDeviceController.snoozeAlarm(context)
                val msg = if (ok) "Будильник отложен" else "Нет активного звонящего будильника"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            is VoiceCommand.SetTimer -> {
                val mins = command.minutes.coerceAtLeast(1)
                val ok = PhoneAndDeviceController.setTimer(context, mins, "Таймер ГолосГид")
                val minWord = declineRussian(mins, "минуту", "минуты", "минут")
                val msg = if (ok) "Таймер на $mins $minWord запущен" else "Запускаю внутренний таймер на $mins $minWord"
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
                if (!ok) {
                    scope.launch(Dispatchers.Main) {
                        delay(mins * 60_000L)
                        feedbackManager.vibrateSuccess()
                        feedbackManager.speak("Таймер на $mins $minWord завершён!")
                    }
                }
            }
            is VoiceCommand.SetReminder -> {
                val msg = ReminderManager.scheduleReminder(
                    context = context,
                    feedbackManager = feedbackManager,
                    scope = scope,
                    title = command.title,
                    hour = command.hour,
                    minute = command.minute,
                    inMinutes = command.inMinutes
                )
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.ReadReminders -> {
                recordAndSpeak(spokenText, ReminderManager.listRemindersSummary(), true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.CancelReminders -> {
                recordAndSpeak(spokenText, ReminderManager.cancelAllReminders(), true, forceSpeak = true, settings = settings)
            }

            // --- 13. Weather & Info ---
            is VoiceCommand.ReadWeather -> {
                val targetCity = command.cityOverride?.takeIf { it.isNotBlank() } ?: settings.weatherCity
                feedbackManager.vibrateAck()
                VoiceEngineStateHolder.recordCommand(spokenText, "Запрашиваю погоду ($targetCity)...", true)
                scope.launch(Dispatchers.Main) {
                    WeatherFetcher.fetchWeatherSummary(targetCity, command.tomorrow, command.askPrecipitation)
                        .onSuccess { recordAndSpeak(spokenText, it, true, forceSpeak = true, settings = settings) }
                        .onFailure {
                            recordAndSpeak(spokenText, "Не удалось узнать погоду. Проверьте интернет или город в Настройке.", false, forceSpeak = true, settings = settings)
                        }
                }
            }
            VoiceCommand.ReadCurrencyRates -> {
                feedbackManager.vibrateAck()
                VoiceEngineStateHolder.recordCommand(spokenText, "Запрашиваю курсы валют...", true)
                scope.launch(Dispatchers.Main) {
                    WeatherFetcher.fetchCbrCurrencyRates()
                        .onSuccess { recordAndSpeak(spokenText, it, true, forceSpeak = true, settings = settings) }
                        .onFailure {
                            recordAndSpeak(spokenText, "Не удалось получить курс валют. Проверьте интернет.", false, forceSpeak = true, settings = settings)
                        }
                }
            }

            // --- 14. Maps & Navigation ---
            VoiceCommand.OpenMaps -> {
                val ok = AppLauncherController.openMaps(context)
                recordAndSpeak(spokenText, if (ok) "Открываю карты" else "Карты не найдены", ok, forceSpeak = true, settings = settings)
            }
            is VoiceCommand.NavigateToPlace -> {
                val ok = AppLauncherController.openMaps(context, command.query)
                val msg = if (ok) "Ищу на карте: ${command.query}. При передвижении обязательно используйте трость." else "Не найдено приложение карт"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }

            // --- 15. Camera ---
            is VoiceCommand.OpenCamera -> {
                val ok = AppLauncherController.openCamera(context, command.videoMode)
                val msg = if (ok) {
                    if (command.videoMode) "Открываю камеру для видеозаписи" else "Открываю камеру"
                } else "Не удалось открыть камеру"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }

            // --- 16 & 17. Emergency SOS & Assistant Controls ---
            VoiceCommand.NeedHelpMenu -> {
                val msg = "Я рядом и готов помочь. Скажите: «Позови на помощь» для звонка близкому, «Экстренный вызов» для 112, «Прочитай экран» или «Что ты умеешь»."
                recordAndSpeak(spokenText, msg, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.CallForHelpSos -> {
                val phone = settings.emergencyContactPhone.trim()
                val name = settings.emergencyContactName.trim()
                if (phone.isBlank() && name.isBlank()) {
                    recordAndSpeak(spokenText, "Экстренный контакт родственника ещё не выбран. Укажите его во вкладке Настройка.", false, forceSpeak = true, settings = settings)
                } else {
                    val displayName = name.ifBlank { phone }
                    pendingAction = PendingConfirmationAction.EmergencyHelpCall(
                        contactName = displayName,
                        contactPhone = phone,
                        expiresAtMs = System.currentTimeMillis() + 25_000L
                    )
                    val prompt = "Экстренный звонок: позвонить $displayName? Скажите «Да» или «Отмена»."
                    recordAndSpeak(spokenText, prompt, true, forceSpeak = true, settings = settings)
                }
            }
            VoiceCommand.CallEmergencyServices -> {
                pendingAction = PendingConfirmationAction.ConfirmNumberCall(
                    digits = "112",
                    label = "экстренную службу 112",
                    expiresAtMs = System.currentTimeMillis() + 25_000L
                )
                val prompt = "Подтвердите экстренный вызов 112. Скажите «Да» для набора или «Отмена»."
                recordAndSpeak(spokenText, prompt, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.AssistantWakeCheck -> {
                recordAndSpeak(spokenText, "Слушаю вас", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.AssistantPingCheck -> {
                recordAndSpeak(spokenText, "Да, я вас слышу и работаю", true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.StopAssistantService -> {
                recordAndSpeak(spokenText, "Выключаю голосового помощника", true, forceSpeak = true, settings = settings)
                scope.launch(Dispatchers.Main) {
                    delay(1400L)
                    VoiceBackgroundService.stop(context)
                }
            }
            VoiceCommand.RepeatLastResponse -> {
                val lastText = feedbackManager.lastSpokenText.ifBlank { "Я ещё ничего не произносил" }
                VoiceEngineStateHolder.recordCommand(spokenText, "Повторяю: $lastText", true)
                feedbackManager.speak(lastText, recordForRepeat = false)
            }
            VoiceCommand.RepeatLastCommand -> {
                val prev = lastExecutedCommand
                if (prev != null) execute(prev.first, prev.second, settings, fromVoice = false)
                else recordAndSpeak(spokenText, "Предыдущей команды ещё не было", false, forceSpeak = true, settings = settings)
            }
            VoiceCommand.ExplainNotUnderstood -> {
                val hint = if (feedbackManager.lastSpokenText.isNotBlank()) {
                    "Скажу проще: ${feedbackManager.lastSpokenText}. Или скажите: Открой ютуб, Позвони, Включи фонарик, Погода."
                } else {
                    "Скажите просто: Открой приложение, Позвони маме, Включи фонарик, Прочитай экран или Погода."
                }
                VoiceEngineStateHolder.recordCommand(spokenText, "Пояснение команд", true)
                feedbackManager.setSpeechRate((settings.ttsSpeechRate - 0.1f).coerceAtLeast(0.7f))
                feedbackManager.speak(hint, recordForRepeat = false)
            }
            VoiceCommand.Help -> {
                val summary = if (settings.simpleSpeechMode) {
                    "Я открываю любые приложения и Ютуб, звоню контактам, включаю фонарик, читаю экран, уведомления, время и погоду."
                } else {
                    "Я открываю любые установленные приложения и YouTube, управляю видео, звоню и читаю сообщения, включаю фонарик, яркость, громкость, будильник и таймер, читаю экран, уведомления, погоду и зову на помощь."
                }
                recordAndSpeak(spokenText, summary, true, forceSpeak = true, settings = settings)
            }
            VoiceCommand.ConfirmAction -> handleConfirmAction(spokenText, settings)
            is VoiceCommand.SelectDisambiguationIndex -> handleDisambiguationChoice(command.index, spokenText, settings)
            VoiceCommand.CancelAction -> {
                pendingAction = null
                PhoneAndDeviceController.stopFindMyPhone(context)
                feedbackManager.stopSpeaking()
                VoiceEngineStateHolder.recordCommand(spokenText, "Действие отменено", true)
                feedbackManager.vibrateClose()
                if (settings.speakConfirmations) feedbackManager.speak("Отменено", recordForRepeat = false)
            }
            VoiceCommand.FindMyPhone -> {
                VoiceEngineStateHolder.recordCommand(spokenText, "Звуковой сигнал поиска телефона (скажите «Отмена» для остановки)", true)
                feedbackManager.vibrateAck()
                PhoneAndDeviceController.startFindMyPhone(context, scope)
            }
            is VoiceCommand.Unknown -> {
                if (!fromVoice || command.shouldPromptRepeat) {
                    val msg = "Не расслышал команду. Повторите, пожалуйста."
                    VoiceEngineStateHolder.recordCommand(spokenText, "Не распознано: «${command.rawText}»", false)
                    feedbackManager.speak(msg, recordForRepeat = false)
                }
            }
        }
    }

    private fun initiateContactCallWithConfirmation(
        contactQuery: String,
        preferWhatsApp: Boolean,
        videoCall: Boolean,
        spokenText: String
    ) {
        scope.launch(Dispatchers.Main) {
            val (waMatches, phoneMatches) = withContext(Dispatchers.IO) {
                val wa = WhatsAppContactCaller.findMatchingContactCandidates(context, contactQuery, limit = 3)
                val ph = PhoneAndDeviceController.findMatchingPhoneContacts(context, contactQuery, limit = 3)
                wa to ph
            }

            val merged = linkedMapOf<String, ResolvedCallTarget>()
            for (c in waMatches) {
                merged[c.displayName.lowercase()] = ResolvedCallTarget(
                    displayName = c.displayName,
                    phoneNumber = c.phoneNumber,
                    waCandidate = c,
                    preferWhatsApp = preferWhatsApp || videoCall || c.voipDataId != null,
                    videoCall = videoCall
                )
            }
            for ((name, phone) in phoneMatches) {
                val key = name.lowercase()
                if (!merged.containsKey(key)) {
                    merged[key] = ResolvedCallTarget(
                        displayName = name,
                        phoneNumber = phone,
                        waCandidate = null,
                        preferWhatsApp = preferWhatsApp || videoCall,
                        videoCall = videoCall
                    )
                }
            }

            val options = merged.values.toList()
            when {
                options.isEmpty() -> {
                    val msg = "Контакт «$contactQuery» не найден в телефонной книге"
                    VoiceEngineStateHolder.recordCommand(spokenText, msg, false)
                    feedbackManager.vibrateAck()
                    feedbackManager.speak(msg)
                }
                options.size == 1 -> {
                    val target = options.first()
                    pendingAction = PendingConfirmationAction.ConfirmContactCall(
                        target = target,
                        expiresAtMs = System.currentTimeMillis() + 25_000L
                    )
                    val callKind = if (target.videoCall) "Видеозвонок" else "Позвонить"
                    val prompt = "Нашёл контакт: ${target.displayName}. $callKind? Скажите «Да» или «Отмена»."
                    VoiceEngineStateHolder.recordCommand(spokenText, "Подтвердите: ${target.displayName}", true)
                    feedbackManager.vibrateAck()
                    feedbackManager.speak(prompt)
                }
                else -> {
                    val top = options.take(3)
                    pendingAction = PendingConfirmationAction.DisambiguateContact(
                        options = top,
                        expiresAtMs = System.currentTimeMillis() + 25_000L
                    )
                    val listText = top.mapIndexed { idx, item -> "${idx + 1} — ${item.displayName}" }.joinToString(", ")
                    val prompt = "Найдено несколько контактов: $listText. Кому звонить: первый или второй?"
                    VoiceEngineStateHolder.recordCommand(spokenText, "Уточнение контакта: $listText", true)
                    feedbackManager.vibrateAck()
                    feedbackManager.speak(prompt)
                }
            }
        }
    }

    private fun handleDisambiguationChoice(index: Int, spokenText: String, settings: VoiceSettings) {
        val pending = pendingAction
        if (pending !is PendingConfirmationAction.DisambiguateContact || index !in pending.options.indices) {
            recordAndSpeak(spokenText, "Такого номера в списке нет", false, forceSpeak = true, settings = settings)
            return
        }
        val chosen = pending.options[index]
        pendingAction = PendingConfirmationAction.ConfirmContactCall(
            target = chosen,
            expiresAtMs = System.currentTimeMillis() + 25_000L
        )
        val prompt = "Выбран контакт: ${chosen.displayName}. Позвонить? Скажите «Да» или «Отмена»."
        recordAndSpeak(spokenText, prompt, true, forceSpeak = true, settings = settings)
    }

    private fun handleConfirmAction(spokenText: String, settings: VoiceSettings) {
        val pending = pendingAction
        pendingAction = null
        if (pending == null || System.currentTimeMillis() > pending.expiresAtMs) {
            recordAndSpeak(spokenText, "Нет активного действия для подтверждения", false, forceSpeak = true, settings = settings)
            return
        }

        when (pending) {
            is PendingConfirmationAction.SendMessage -> {
                scope.launch(Dispatchers.Main) {
                    val res = withContext(Dispatchers.IO) {
                        WhatsAppContactCaller.sendMessageToContact(context, pending.recipientQuery, pending.messageText)
                    }
                    when (res) {
                        is WhatsAppActionResult.CallingContact -> {
                            recordAndSpeak(spokenText, "Отправляю сообщение для ${res.displayName}", true, forceSpeak = true, settings = settings)
                            VoiceControlAccessibilityService.instance?.tapWhatsAppSendButtonIfVisible()
                        }
                        else -> recordAndSpeak(spokenText, "Не удалось отправить сообщение для ${pending.resolvedName}", false, forceSpeak = true, settings = settings)
                    }
                }
            }
            is PendingConfirmationAction.EmergencyHelpCall -> {
                val ok = pending.contactPhone.isNotBlank() && PhoneAndDeviceController.callPhoneNumber(context, pending.contactPhone)
                if (ok) {
                    recordAndSpeak(spokenText, "Звоню ${pending.contactName}", true, forceSpeak = true, settings = settings)
                } else {
                    initiateContactCallWithConfirmation(pending.contactName, false, false, spokenText)
                }
            }
            is PendingConfirmationAction.ConfirmNumberCall -> {
                val ok = PhoneAndDeviceController.callPhoneNumber(context, pending.digits)
                val msg = if (ok) "Звоню: ${pending.label}" else "Не удалось набрать ${pending.label}"
                recordAndSpeak(spokenText, msg, ok, forceSpeak = true, settings = settings)
            }
            is PendingConfirmationAction.ConfirmContactCall -> executeResolvedCall(pending.target, spokenText, settings)
            is PendingConfirmationAction.DisambiguateContact -> {
                pendingAction = pending
                recordAndSpeak(spokenText, "Скажите: первый, второй или отмена", false, forceSpeak = true, settings = settings)
            }
        }
    }

    private fun executeResolvedCall(target: ResolvedCallTarget, spokenText: String, settings: VoiceSettings) {
        scope.launch(Dispatchers.Main) {
            if (target.videoCall && target.waCandidate != null) {
                val res = withContext(Dispatchers.IO) {
                    WhatsAppContactCaller.startWhatsAppVideoCall(context, target.waCandidate)
                }
                if (res is WhatsAppActionResult.CallingContact) {
                    recordAndSpeak(spokenText, "Видеозвонок по WhatsApp: ${res.displayName}", true, forceSpeak = true, settings = settings)
                    VoiceControlAccessibilityService.instance?.tapWhatsAppVideoCallIfVisible()
                    return@launch
                }
            }
            if (target.preferWhatsApp) {
                val res = withContext(Dispatchers.IO) {
                    WhatsAppContactCaller.callContact(context, target.displayName)
                }
                if (res is WhatsAppActionResult.CallingContact) {
                    recordAndSpeak(spokenText, "Звоню по WhatsApp: ${res.displayName}", true, forceSpeak = true, settings = settings)
                    VoiceControlAccessibilityService.instance?.confirmWhatsAppCallIfNeeded()
                    return@launch
                }
            }
            val phone = target.phoneNumber
            if (!phone.isNullOrBlank() && PhoneAndDeviceController.callPhoneNumber(context, phone)) {
                recordAndSpeak(spokenText, "Звоню: ${target.displayName}", true, forceSpeak = true, settings = settings)
            } else {
                recordAndSpeak(spokenText, "Не удалось совершить вызов для ${target.displayName}", false, forceSpeak = true, settings = settings)
            }
        }
    }

    private fun startMessageConfirmation(recipient: String, messageText: String, spokenText: String) {
        scope.launch(Dispatchers.Main) {
            val resolved = withContext(Dispatchers.IO) {
                WhatsAppContactCaller.resolveContact(context, recipient)?.displayName ?: recipient
            }
            pendingAction = PendingConfirmationAction.SendMessage(
                recipientQuery = recipient,
                resolvedName = resolved,
                messageText = messageText,
                expiresAtMs = System.currentTimeMillis() + 25_000L
            )
            val prompt = "Сообщение для $resolved: $messageText. Отправить? Скажите «Да» или «Отмена»."
            VoiceEngineStateHolder.recordCommand(spokenText, "Подтвердите отправку: $resolved", true)
            feedbackManager.vibrateAck()
            feedbackManager.speak(prompt)
        }
    }

    private fun handleTorchResult(res: TorchResult, spokenText: String, settings: VoiceSettings) {
        when (res) {
            is TorchResult.Changed -> {
                val msg = if (res.enabled) "Фонарик включён" else "Фонарик выключен"
                recordAndSpeak(spokenText, msg, true, forceSpeak = false, settings = settings)
            }
            TorchResult.Unavailable ->
                recordAndSpeak(spokenText, "На этом устройстве не найдена вспышка", false, forceSpeak = true, settings = settings)
            is TorchResult.Failed ->
                recordAndSpeak(spokenText, res.message, false, forceSpeak = true, settings = settings)
        }
    }

    private fun handleBrightnessResult(
        res: BrightnessResult,
        successPrefix: String,
        spokenText: String,
        settings: VoiceSettings
    ) {
        when (res) {
            is BrightnessResult.Changed -> {
                val msg = if (res.percent > 0) "$successPrefix (${res.percent}%)" else successPrefix
                recordAndSpeak(spokenText, msg, true, forceSpeak = false, settings = settings)
            }
            BrightnessResult.MissingWriteSettingsPermission ->
                recordAndSpeak(spokenText, "Разрешите изменение системных настроек в разделе Настройка для управления яркостью", false, forceSpeak = true, settings = settings)
            is BrightnessResult.Failed ->
                recordAndSpeak(spokenText, res.message, false, forceSpeak = true, settings = settings)
        }
    }

    private fun setFullscreenWithFeedback(enter: Boolean, spokenText: String, settings: VoiceSettings) {
        val acc = VoiceControlAccessibilityService.instance
        if (acc != null) {
            acc.setFullscreen(enter = enter) { ok ->
                val msg = if (ok) {
                    if (enter) "Полноэкранный режим" else "Обычный режим экрана"
                } else {
                    "Кнопка полного экрана не найдена"
                }
                recordAndSpeak(spokenText, msg, ok, forceSpeak = !ok, settings = settings)
            }
        } else {
            recordAndSpeak(spokenText, "Включите Спецвозможности для полного экрана", false, forceSpeak = true, settings = settings)
        }
    }

    private fun readScreenNow(spokenText: String, settings: VoiceSettings) {
        val acc = VoiceControlAccessibilityService.instance
        if (acc == null) {
            recordAndSpeak(spokenText, "Включите Спецвозможности в Настройке, чтобы читать экран", false, forceSpeak = true, settings = settings)
        } else {
            scope.launch(Dispatchers.Main) {
                val screenText = withContext(Dispatchers.IO) { acc.readScreenText() }
                val msg = if (screenText.isBlank()) "На экране не найден текст" else screenText
                VoiceEngineStateHolder.recordCommand(spokenText, msg.take(120), screenText.isNotBlank())
                feedbackManager.vibrateAck()
                feedbackManager.speak(msg)
            }
        }
    }

    private fun handleYouTubeLaunchResult(
        result: YouTubeLaunchResult,
        spokenText: String,
        settings: VoiceSettings,
        successSpeech: String
    ) {
        if (result.launched) {
            if (result.blockedByMissingPermission) {
                recordAndSpeak(
                    spokenText,
                    "Honor заблокировал запуск из фона. Разрешите «Поверх других окон» в Настройке.",
                    false,
                    forceSpeak = true,
                    settings = settings
                )
            } else {
                VoiceEngineStateHolder.markYouTubeOpened(true)
                VoiceEngineStateHolder.recordCommand(spokenText, "Открыт ${result.targetLabel}", true)
                feedbackManager.vibrateSuccess()
                if (settings.speakConfirmations) feedbackManager.speak(successSpeech)
            }
        } else {
            recordAndSpeak(spokenText, "Не найден YouTube или браузер", false, forceSpeak = true, settings = settings)
        }
    }

    private fun recordAndSpeak(
        spokenText: String,
        message: String,
        succeeded: Boolean,
        forceSpeak: Boolean,
        settings: VoiceSettings,
        shortSpeak: String? = null
    ) {
        VoiceEngineStateHolder.recordCommand(spokenText, message, succeeded)
        if (succeeded) feedbackManager.vibrateAck()
        if (forceSpeak || settings.speakConfirmations) {
            val text = if (shortSpeak != null && settings.simpleSpeechMode) shortSpeak else message
            feedbackManager.speak(text)
        }
    }

    private fun pauseVideoReliably(spokenText: String, settings: VoiceSettings) {
        feedbackManager.vibrateAck()
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        VoiceEngineStateHolder.recordCommand(spokenText, "Видео на паузе", true)

        scope.launch(Dispatchers.Main) {
            delay(220L)
            if (audioManager.isMusicActive) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                delay(220L)
                if (audioManager.isMusicActive) {
                    VoiceControlAccessibilityService.instance?.tapMediaControlButton(wantPause = true)
                }
            }
            if (settings.speakConfirmations) feedbackManager.speak("Пауза")
        }
    }

    private fun resumeVideoReliably(spokenText: String, settings: VoiceSettings) {
        feedbackManager.vibrateAck()
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        VoiceEngineStateHolder.recordCommand(spokenText, "Продолжаю воспроизведение", true)

        scope.launch(Dispatchers.Main) {
            delay(220L)
            if (!audioManager.isMusicActive) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                delay(220L)
                if (!audioManager.isMusicActive) {
                    VoiceControlAccessibilityService.instance?.tapMediaControlButton(wantPause = false)
                }
            }
            if (settings.speakConfirmations) feedbackManager.speak("Продолжаю")
        }
    }

    private fun adjustYouTubeSpeed(faster: Boolean, spokenText: String, settings: VoiceSettings) {
        val acc = VoiceControlAccessibilityService.instance
        if (acc == null) {
            recordAndSpeak(spokenText, "Для скорости видео включите Спецвозможности в Настройке", false, forceSpeak = true, settings = settings)
            return
        }
        feedbackManager.vibrateAck()
        acc.adjustYouTubePlaybackSpeed(faster) { success, message ->
            recordAndSpeak(spokenText, message, success, forceSpeak = !success, settings = settings)
        }
    }

    private fun formatRussianTime(): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return "Сейчас $hour ${declineRussian(hour, "час", "часа", "часов")} $minute ${declineRussian(minute, "минута", "минуты", "минут")}"
    }

    private fun formatRussianDate(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthsGenitive = arrayOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
        return "Сегодня $day ${monthsGenitive[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} года"
    }

    private fun formatRussianDayOfWeek(): String {
        val dayName = SimpleDateFormat("EEEE", Locale("ru", "RU")).format(Calendar.getInstance().time)
        return "Сегодня $dayName"
    }

    private fun declineRussian(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        if (mod100 in 11..19) return many
        return when (n % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    private fun closeYouTubeAndReturn(spokenText: String, settings: VoiceSettings) {
        pauseAndSilenceMedia()
        val wentHome = VoiceControlAccessibilityService.instance?.performGlobalHome() == true
        VoiceEngineStateHolder.markYouTubeOpened(false)
        feedbackManager.vibrateClose()

        if (settings.returnToAppOnClose || !wentHome) {
            scope.launch(Dispatchers.Main) {
                if (wentHome) delay(180L)
                bringMainAppToFront()
            }
        }

        val summary = if (wentHome) "YouTube закрыт" else "Звук остановлен, возврат в ГолосГид"
        recordAndSpeak(spokenText, summary, true, forceSpeak = false, settings = settings, shortSpeak = "Ютуб закрыт")
    }

    @Suppress("DEPRECATION")
    private fun pauseAndSilenceMedia() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusReq)
            scope.launch(Dispatchers.Main) {
                delay(800L)
                audioManager.abandonAudioFocusRequest(focusReq)
            }
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { }
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            scope.launch(Dispatchers.Main) {
                delay(800L)
                audioManager.abandonAudioFocus(listener)
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun bringMainAppToFront() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        YouTubeLauncher.startActivityReliably(context, intent)
    }
}
