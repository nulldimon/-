package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.max

class VoiceFeedbackManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady = false
    @Volatile
    private var speechRate = 1.0f
    @Volatile
    var lastSpokenText: String = ""
        private set
    @Volatile
    private var activeUtteranceId: String? = null
    private var speakWatchdogJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsAvailable = MutableStateFlow(false)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: VoiceFeedbackManager? = null

        fun getInstance(context: Context): VoiceFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceFeedbackManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ruLocale = Locale("ru", "RU")
                    val res = tts?.setLanguage(ruLocale)
                    val supported = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!supported) {
                        tts?.setLanguage(Locale("ru"))
                    }
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId != null && utteranceId == activeUtteranceId) {
                                _isSpeaking.value = true
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == null || utteranceId == activeUtteranceId || activeUtteranceId == null) {
                                activeUtteranceId = null
                                speakWatchdogJob?.cancel()
                                _isSpeaking.value = false
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (!interrupted && (utteranceId == null || utteranceId == activeUtteranceId)) {
                                activeUtteranceId = null
                                speakWatchdogJob?.cancel()
                                _isSpeaking.value = false
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == null || utteranceId == activeUtteranceId || activeUtteranceId == null) {
                                activeUtteranceId = null
                                speakWatchdogJob?.cancel()
                                _isSpeaking.value = false
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId == null || utteranceId == activeUtteranceId || activeUtteranceId == null) {
                                activeUtteranceId = null
                                speakWatchdogJob?.cancel()
                                _isSpeaking.value = false
                            }
                        }
                    })
                    isReady = true
                    _ttsAvailable.value = true
                } else {
                    isReady = false
                    activeUtteranceId = null
                    _ttsAvailable.value = false
                    _isSpeaking.value = false
                }
            }
        } catch (_: Exception) {
            isReady = false
            activeUtteranceId = null
            _ttsAvailable.value = false
            _isSpeaking.value = false
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.6f, 1.6f)
        try {
            tts?.setSpeechRate(speechRate)
        } catch (_: Exception) {
        }
    }

    fun speak(text: String, flush: Boolean = true, recordForRepeat: Boolean = true) {
        if (tts == null) {
            initTts()
        }
        if (!isReady || text.isBlank()) {
            activeUtteranceId = null
            speakWatchdogJob?.cancel()
            _isSpeaking.value = false
            return
        }
        if (recordForRepeat) {
            lastSpokenText = text
        }
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        try {
            tts?.setSpeechRate(speechRate)
        } catch (_: Exception) {
        }
        _isSpeaking.value = true

        // Adaptive watchdog: polls tts.isSpeaking after a short grace period so Honor/Huawei
        // never stays stuck if onDone is dropped, while allowing long responses (up to 30s).
        speakWatchdogJob?.cancel()
        val maxWaitMs = max(2000L, (text.length * 105L)).coerceAtMost(30_000L)
        speakWatchdogJob = scope.launch {
            delay(650L)
            var elapsed = 650L
            var idleChecks = 0
            while (elapsed < maxWaitMs && activeUtteranceId == utteranceId) {
                val engineSpeaking = try {
                    tts?.isSpeaking == true
                } catch (_: Exception) {
                    false
                }
                if (!engineSpeaking && elapsed >= 900L) {
                    idleChecks++
                    if (idleChecks >= 2) break
                } else {
                    idleChecks = 0
                }
                delay(350L)
                elapsed += 350L
            }
            if (activeUtteranceId == utteranceId) {
                activeUtteranceId = null
                _isSpeaking.value = false
            }
        }

        val result = try {
            tts?.speak(text, queueMode, null, utteranceId) ?: TextToSpeech.ERROR
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            if (activeUtteranceId == utteranceId) {
                activeUtteranceId = null
                speakWatchdogJob?.cancel()
                _isSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        activeUtteranceId = null
        speakWatchdogJob?.cancel()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun vibrateAck() {
        vibratePattern(longArrayOf(0, 45))
    }

    fun vibrateSuccess() {
        vibratePattern(longArrayOf(0, 45, 60, 70))
    }

    fun vibrateClose() {
        vibratePattern(longArrayOf(0, 110))
    }

    @Suppress("DEPRECATION")
    private fun vibratePattern(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
