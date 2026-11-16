package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    private data class PendingUtterance(
        val text: String,
        val flush: Boolean,
        val recordForRepeat: Boolean,
        val expiresAtMs: Long
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady = false
    @Volatile
    private var isInitializing = false
    @Volatile
    private var lastInitAttemptMs = 0L
    @Volatile
    private var speechRate = 1.0f
    @Volatile
    var lastSpokenText: String = ""
        private set

    @Volatile
    private var activeUtteranceId: String? = null
    private var activeUtteranceText: String? = null
    private var activeUtteranceFlush: Boolean = true
    private var activeUtteranceRecord: Boolean = true
    private var activeUtteranceAllowRetry: Boolean = false
    private var activeUtteranceStarted: Boolean = false
    private var speakWatchdogJob: Job? = null
    private var pendingUtterance: PendingUtterance? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsAvailable = MutableStateFlow(false)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable.asStateFlow()

    companion object {
        private const val PENDING_TIMEOUT_MS = 6_000L
        private const val REINIT_COOLDOWN_MS = 2_500L
        private const val START_TIMEOUT_MS = 1_400L

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
        runOnMain {
            initTtsLocked(force = true)
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun initTtsLocked(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val initTimedOut = isInitializing && (now - lastInitAttemptMs) > 10_000L
        if (!force && !initTimedOut && (isInitializing || (now - lastInitAttemptMs) < REINIT_COOLDOWN_MS)) {
            return
        }

        shutdownCurrentTtsLocked()
        isReady = false
        isInitializing = true
        lastInitAttemptMs = now
        _ttsAvailable.value = false

        try {
            var createdTts: TextToSpeech? = null
            createdTts = TextToSpeech(appContext) { status ->
                runOnMain {
                    val current = createdTts
                    if (current == null || tts !== current) {
                        try {
                            current?.shutdown()
                        } catch (_: Exception) {
                        }
                        return@runOnMain
                    }
                    isInitializing = false

                    if (status == TextToSpeech.SUCCESS) {
                        val ruLocale = Locale("ru", "RU")
                        val res = try {
                            current.setLanguage(ruLocale)
                        } catch (_: Exception) {
                            TextToSpeech.ERROR
                        }
                        val supported = res != TextToSpeech.LANG_MISSING_DATA &&
                            res != TextToSpeech.LANG_NOT_SUPPORTED &&
                            res != TextToSpeech.ERROR
                        if (!supported) {
                            try {
                                current.setLanguage(Locale("ru"))
                            } catch (_: Exception) {
                            }
                        }
                        try {
                            current.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                        } catch (_: Exception) {
                        }
                        attachProgressListener(current)
                        isReady = true
                        _ttsAvailable.value = true

                        val pending = pendingUtterance
                        pendingUtterance = null
                        if (pending != null && System.currentTimeMillis() <= pending.expiresAtMs) {
                            speakInternal(
                                text = pending.text,
                                flush = pending.flush,
                                recordForRepeat = pending.recordForRepeat,
                                allowReinitRetry = false
                            )
                        }
                    } else {
                        shutdownCurrentTtsLocked()
                        pendingUtterance = null
                        isReady = false
                        _ttsAvailable.value = false
                        _isSpeaking.value = false
                    }
                }
            }
            tts = createdTts
        } catch (_: Exception) {
            isInitializing = false
            tts = null
            isReady = false
            pendingUtterance = null
            _ttsAvailable.value = false
            _isSpeaking.value = false
        }
    }

    private fun shutdownCurrentTtsLocked() {
        val old = tts
        tts = null
        isReady = false
        if (old != null) {
            try {
                old.stop()
            } catch (_: Exception) {
            }
            try {
                old.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    private fun attachProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnMain {
                    if (tts !== engine) return@runOnMain
                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                        activeUtteranceStarted = true
                        _isSpeaking.value = true
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnMain {
                    if (tts !== engine) return@runOnMain
                    if (utteranceId == null || utteranceId == activeUtteranceId || activeUtteranceId == null) {
                        clearActiveUtteranceLocked()
                    }
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                runOnMain {
                    if (tts !== engine) return@runOnMain
                    if (!interrupted && (utteranceId == null || utteranceId == activeUtteranceId)) {
                        clearActiveUtteranceLocked()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleEngineUtteranceError(engine, utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleEngineUtteranceError(engine, utteranceId)
            }
        })
    }

    private fun handleEngineUtteranceError(engine: TextToSpeech, utteranceId: String?) {
        runOnMain {
            if (tts !== engine) return@runOnMain
            if (utteranceId != null && utteranceId != activeUtteranceId) {
                return@runOnMain
            }

            val retryPending = if (activeUtteranceAllowRetry && !activeUtteranceText.isNullOrBlank()) {
                PendingUtterance(
                    text = activeUtteranceText!!,
                    flush = activeUtteranceFlush,
                    recordForRepeat = activeUtteranceRecord,
                    expiresAtMs = System.currentTimeMillis() + PENDING_TIMEOUT_MS
                )
            } else {
                null
            }

            clearActiveUtteranceLocked()
            recoverFromDeadTtsLocked(retryPending)
        }
    }

    private fun clearActiveUtteranceLocked() {
        activeUtteranceId = null
        activeUtteranceText = null
        activeUtteranceAllowRetry = false
        activeUtteranceStarted = false
        speakWatchdogJob?.cancel()
        speakWatchdogJob = null
        _isSpeaking.value = false
    }

    private fun recoverFromDeadTtsLocked(pending: PendingUtterance?) {
        if (pending != null) {
            pendingUtterance = pending
        }
        isReady = false
        _ttsAvailable.value = false
        initTtsLocked(force = !isInitializing)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.6f, 1.6f)
        runOnMain {
            try {
                tts?.setSpeechRate(speechRate)
            } catch (_: Exception) {
            }
        }
    }

    fun speak(text: String, flush: Boolean = true, recordForRepeat: Boolean = true) {
        runOnMain {
            speakInternal(
                text = text,
                flush = flush,
                recordForRepeat = recordForRepeat,
                allowReinitRetry = true
            )
        }
    }

    private fun speakInternal(
        text: String,
        flush: Boolean,
        recordForRepeat: Boolean,
        allowReinitRetry: Boolean
    ) {
        val safeText = text.trim().take(3500)
        if (safeText.isBlank()) {
            pendingUtterance = null
            clearActiveUtteranceLocked()
            return
        }
        if (recordForRepeat) {
            lastSpokenText = safeText
        }

        val engine = tts
        if (engine == null || !isReady) {
            clearActiveUtteranceLocked()
            if (allowReinitRetry) {
                pendingUtterance = PendingUtterance(
                    text = safeText,
                    flush = flush,
                    recordForRepeat = recordForRepeat,
                    expiresAtMs = System.currentTimeMillis() + PENDING_TIMEOUT_MS
                )
                initTtsLocked(force = (engine == null && !isInitializing))
            }
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        activeUtteranceText = safeText
        activeUtteranceFlush = flush
        activeUtteranceRecord = recordForRepeat
        activeUtteranceAllowRetry = allowReinitRetry
        activeUtteranceStarted = false

        try {
            engine.setSpeechRate(speechRate)
        } catch (_: Exception) {
        }
        _isSpeaking.value = true

        // Adaptive watchdog: handles dropped onDone callbacks AND detects a dead/killed TTS binder
        // after long background sessions (so TTS automatically rebinds instead of staying silent).
        speakWatchdogJob?.cancel()
        val maxWaitMs = max(2000L, (safeText.length * 105L)).coerceAtMost(30_000L)
        speakWatchdogJob = scope.launch {
            var elapsed = 0L
            var idleChecks = 0
            while (elapsed < maxWaitMs && activeUtteranceId == utteranceId) {
                val step = if (elapsed < 1000L) 250L else 350L
                delay(step)
                elapsed += step
                if (activeUtteranceId != utteranceId) break

                val engineSpeaking = try {
                    tts?.isSpeaking == true
                } catch (_: Exception) {
                    false
                }
                if (engineSpeaking) {
                    activeUtteranceStarted = true
                }
                if (!activeUtteranceStarted && !engineSpeaking && elapsed >= START_TIMEOUT_MS) {
                    // Remote TTS service accepted call or died silently without starting playback
                    break
                }
                if (!engineSpeaking && (activeUtteranceStarted || elapsed >= 1000L)) {
                    idleChecks++
                    if (idleChecks >= 2) break
                } else {
                    idleChecks = 0
                }
            }

            if (activeUtteranceId == utteranceId) {
                val started = activeUtteranceStarted
                val retry = if (!started && activeUtteranceAllowRetry) {
                    PendingUtterance(
                        text = safeText,
                        flush = flush,
                        recordForRepeat = recordForRepeat,
                        expiresAtMs = System.currentTimeMillis() + PENDING_TIMEOUT_MS
                    )
                } else {
                    null
                }
                clearActiveUtteranceLocked()
                if (retry != null) {
                    recoverFromDeadTtsLocked(retry)
                }
            }
        }

        val result = try {
            engine.speak(safeText, queueMode, null, utteranceId)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }

        if (result != TextToSpeech.SUCCESS) {
            if (activeUtteranceId == utteranceId) {
                clearActiveUtteranceLocked()
            }
            if (allowReinitRetry) {
                recoverFromDeadTtsLocked(
                    PendingUtterance(
                        text = safeText,
                        flush = flush,
                        recordForRepeat = recordForRepeat,
                        expiresAtMs = System.currentTimeMillis() + PENDING_TIMEOUT_MS
                    )
                )
            }
        }
    }

    fun stopSpeaking() {
        runOnMain {
            pendingUtterance = null
            clearActiveUtteranceLocked()
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
        }
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
