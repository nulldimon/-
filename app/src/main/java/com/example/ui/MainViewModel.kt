package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.control.TabletController
import com.example.control.YouTubeLauncher
import com.example.data.SettingsRepository
import com.example.data.VoiceSettings
import com.example.service.VoiceBackgroundService
import com.example.service.VoiceControlAccessibilityService
import com.example.service.VoiceNotificationListenerService
import com.example.voice.ListeningStatus
import com.example.voice.ModelInstallState
import com.example.voice.RussianVoiceCommandParser
import com.example.voice.VoiceEngineStateHolder
import com.example.voice.VoiceFeedbackManager
import com.example.voice.VoiceRuntimeState
import com.example.voice.VoskModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceReadiness(
    val hasMicPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val hasContactsPermission: Boolean = false,
    val hasPhonePermissions: Boolean = false,
    val hasBluetoothPermission: Boolean = true,
    val hasNotificationListener: Boolean = false,
    val canWriteSettings: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val isSystemSpeechAvailable: Boolean = false,
    val installedYouTubeLabel: String? = null
)

data class GolosGidUiState(
    val runtime: VoiceRuntimeState = VoiceRuntimeState(),
    val modelState: ModelInstallState = ModelInstallState.NotInstalled,
    val settings: VoiceSettings = VoiceSettings(),
    val readiness: DeviceReadiness = DeviceReadiness()
) {
    val canListenOfflineOrSystem: Boolean
        get() = modelState is ModelInstallState.Ready || readiness.isSystemSpeechAvailable
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val voskModelManager = VoskModelManager(appContext)
    private val feedbackManager = VoiceFeedbackManager.getInstance(appContext)
    private val tabletController = TabletController(
        appContext,
        feedbackManager,
        viewModelScope,
        settingsRepository
    )

    private val _readiness = MutableStateFlow(computeReadiness())

    val uiState: StateFlow<GolosGidUiState> = combine(
        VoiceEngineStateHolder.state,
        voskModelManager.state,
        settingsRepository.settingsFlow,
        _readiness
    ) { runtime, modelState, settings, readiness ->
        GolosGidUiState(
            runtime = runtime.copy(
                isAccessibilityConnected = readiness.isAccessibilityEnabled || runtime.isAccessibilityConnected
            ),
            modelState = modelState,
            settings = settings,
            readiness = readiness
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = GolosGidUiState(readiness = _readiness.value)
    )

    fun refreshDeviceReadiness() {
        voskModelManager.refreshStatus()
        _readiness.value = computeReadiness()
    }

    private fun computeReadiness(): DeviceReadiness {
        val micGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val contactsGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val callPhoneGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val callLogGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val answerGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val accEnabled = VoiceControlAccessibilityService.instance != null ||
            VoiceControlAccessibilityService.isEnabledInSettings(appContext)

        val overlayEnabled = Settings.canDrawOverlays(appContext)
        val notifListenerEnabled = VoiceNotificationListenerService.isEnabled(appContext)
        val writeSettingsEnabled = Settings.System.canWrite(appContext)

        val sysSpeech = SpeechRecognizer.isRecognitionAvailable(appContext)
        val ytClient = YouTubeLauncher.findInstalledYouTubeClient(appContext)?.second

        return DeviceReadiness(
            hasMicPermission = micGranted,
            hasNotificationPermission = notifGranted,
            hasContactsPermission = contactsGranted,
            hasPhonePermissions = callPhoneGranted && callLogGranted && answerGranted,
            hasBluetoothPermission = bluetoothGranted,
            hasNotificationListener = notifListenerEnabled,
            canWriteSettings = writeSettingsEnabled,
            isAccessibilityEnabled = accEnabled,
            canDrawOverlays = overlayEnabled,
            isSystemSpeechAvailable = sysSpeech,
            installedYouTubeLabel = ytClient
        )
    }

    fun toggleListening() {
        refreshDeviceReadiness()
        val currentStatus = uiState.value.runtime.listeningStatus
        when (currentStatus) {
            ListeningStatus.LISTENING, ListeningStatus.INITIALIZING -> {
                VoiceBackgroundService.pause(appContext)
                if (uiState.value.settings.speakConfirmations) {
                    feedbackManager.speak("Прослушивание на паузе")
                }
            }
            ListeningStatus.PAUSED -> {
                VoiceBackgroundService.resume(appContext)
            }
            ListeningStatus.STOPPED, ListeningStatus.ERROR -> {
                VoiceBackgroundService.start(appContext)
            }
        }
    }

    fun stopBackgroundService() {
        VoiceBackgroundService.stop(appContext)
        if (uiState.value.settings.speakConfirmations) {
            feedbackManager.speak("Фоновая служба остановлена")
        }
    }

    fun downloadRussianModel() {
        viewModelScope.launch {
            if (uiState.value.settings.speakConfirmations) {
                feedbackManager.speak("Начинаю загрузку русской голосовой модели")
            }
            val result = voskModelManager.downloadRussianModel()
            if (result.isSuccess) {
                if (uiState.value.settings.speakConfirmations) {
                    feedbackManager.speak("Русская модель установлена и готова к работе без интернета")
                }
                if (uiState.value.readiness.hasMicPermission) {
                    VoiceBackgroundService.reloadModel(appContext)
                }
            } else {
                if (uiState.value.settings.speakConfirmations) {
                    feedbackManager.speak("Не удалось скачать модель. Проверьте интернет или загрузите архив вручную.")
                }
            }
        }
    }

    fun importRussianModel(uri: Uri) {
        viewModelScope.launch {
            val result = voskModelManager.importFromUri(uri)
            if (result.isSuccess) {
                if (uiState.value.settings.speakConfirmations) {
                    feedbackManager.speak("Русская модель успешно импортирована")
                }
                if (uiState.value.readiness.hasMicPermission) {
                    VoiceBackgroundService.reloadModel(appContext)
                }
            }
        }
    }

    fun deleteRussianModel() {
        viewModelScope.launch {
            voskModelManager.removeModel()
            VoiceBackgroundService.stop(appContext)
        }
    }

    fun executeCommandPhrase(phrase: String) {
        val settings = uiState.value.settings
        val parsed = RussianVoiceCommandParser.parse(
            rawText = phrase,
            requireWakeWord = false,
            wakeWord = settings.wakeWord,
            strictCommandsOnly = false,
            hasPendingConfirmation = tabletController.hasPendingConfirmation()
        ) ?: return
        tabletController.execute(parsed, phrase, settings, fromVoice = false)
    }

    fun speakOut(text: String) {
        feedbackManager.setSpeechRate(uiState.value.settings.ttsSpeechRate)
        feedbackManager.speak(text)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWakeWordEnabled(enabled) }
    }

    fun setStrictCommandsOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStrictCommandsOnly(enabled) }
    }

    fun setReturnToAppOnClose(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReturnToAppOnClose(enabled) }
    }

    fun setSpeakConfirmations(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSpeakConfirmations(enabled) }
    }

    fun setSimpleSpeechMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSimpleSpeechMode(enabled) }
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { settingsRepository.setTtsSpeechRate(rate) }
    }

    fun setAutoOpenFirstVideo(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoOpenFirstVideo(enabled) }
    }

    fun setWeatherCity(city: String) {
        viewModelScope.launch {
            settingsRepository.setWeatherCity(city)
            if (uiState.value.settings.speakConfirmations) {
                feedbackManager.speak("Город для погоды сохранён: ${city.trim().ifBlank { "Москва" }}")
            }
        }
    }

    fun setEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            settingsRepository.setEmergencyContact(name, phone)
            if (uiState.value.settings.speakConfirmations) {
                feedbackManager.speak("Экстренный контакт сохранён")
            }
        }
    }
}
