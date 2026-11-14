package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener as AndroidRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.control.TabletController
import com.example.data.SettingsRepository
import com.example.data.VoiceSettings
import com.example.voice.ListeningStatus
import com.example.voice.RussianVoiceCommandParser
import com.example.voice.SpeechEngineKind
import com.example.voice.VoiceCommand
import com.example.voice.VoiceEngineStateHolder
import com.example.voice.VoiceFeedbackManager
import com.example.voice.VoskModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.math.max

class VoiceBackgroundService : Service() {

    companion object {
        const val ACTION_START_LISTENING = "com.example.action.START_LISTENING"
        const val ACTION_PAUSE_LISTENING = "com.example.action.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.example.action.RESUME_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_RELOAD_MODEL = "com.example.action.RELOAD_MODEL"

        private const val CHANNEL_ID = "golosgid_voice_bg"
        private const val NOTIFICATION_ID = 4201
        private const val SAMPLE_RATE = 16000
        private const val READ_CHUNK_SAMPLES = 1600 // 100ms chunks at 16kHz

        fun start(context: Context) {
            val intent = Intent(context, VoiceBackgroundService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, VoiceBackgroundService::class.java).apply {
                action = ACTION_PAUSE_LISTENING
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, VoiceBackgroundService::class.java).apply {
                action = ACTION_RESUME_LISTENING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun reloadModel(context: Context) {
            val intent = Intent(context, VoiceBackgroundService::class.java).apply {
                action = ACTION_RELOAD_MODEL
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var voskModelManager: VoskModelManager
    private lateinit var feedbackManager: VoiceFeedbackManager
    private lateinit var tabletController: TabletController

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSettings = VoiceSettings()
    @Volatile
    private var isPausedByUser = false
    @Volatile
    private var pauseCaptureForTts = false
    @Volatile
    private var needResetRecognizer = false

    // Direct AudioRecord + Vosk Recognizer capture loop (prevents SpeechService ANRs & silent deaths)
    @Volatile
    private var voskModel: Model? = null
    private var voskLoopJob: Job? = null
    @Volatile
    private var closingLoopJob: Job? = null
    @Volatile
    private var captureGeneration: Long = 0L
    private var activeModelDir: File? = null

    // Optional system SpeechRecognizer fallback (if non-Google recognizer exists on device)
    private var systemRecognizer: SpeechRecognizer? = null
    private var isSystemListening = false

    // Debounce & echo guard
    private var lastExecutedNormText: String = ""
    private var lastExecutedCommandClass: String = ""
    private var lastExecutedTimestampMs: Long = 0L
    private var lastPartialPreview: String = ""
    private var urgentPartialJob: Job? = null
    private var lastUrgentPartialCandidate: String = ""

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        voskModelManager = VoskModelManager(applicationContext)
        feedbackManager = VoiceFeedbackManager.getInstance(applicationContext)
        tabletController = TabletController(
            applicationContext,
            feedbackManager,
            serviceScope,
            settingsRepository
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GolosGid:VoiceListening").apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()

        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                feedbackManager.setSpeechRate(settings.ttsSpeechRate)
            }
        }

        // Pause recognition while TTS speaks, then reset recognizer buffer and resume cleanly
        serviceScope.launch {
            feedbackManager.isSpeaking.collectLatest { speaking ->
                if (speaking) {
                    pauseCaptureForTts = true
                    needResetRecognizer = true
                    urgentPartialJob?.cancel()
                } else {
                    delay(200L)
                    needResetRecognizer = true
                    pauseCaptureForTts = false
                    if (!isPausedByUser && systemRecognizer != null && !isSystemListening) {
                        startSystemListeningCycle()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundNotification(isPaused = isPausedByUser)

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopEngines(releaseModel = true)
                releaseWakeLock()
                VoiceEngineStateHolder.updateStatus(ListeningStatus.STOPPED)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_LISTENING -> {
                isPausedByUser = true
                stopEngines(releaseModel = false)
                releaseWakeLock()
                VoiceEngineStateHolder.updateStatus(ListeningStatus.PAUSED)
                updateNotification(isPaused = true)
                return START_STICKY
            }

            ACTION_RESUME_LISTENING -> {
                isPausedByUser = false
                acquireWakeLock()
                updateNotification(isPaused = false)
                startAppropriateEngine(announceReady = false)
                return START_STICKY
            }

            ACTION_RELOAD_MODEL -> {
                stopEngines(releaseModel = true)
                isPausedByUser = false
                acquireWakeLock()
                startAppropriateEngine(announceReady = true)
                return START_STICKY
            }

            else -> {
                isPausedByUser = false
                acquireWakeLock()
                startAppropriateEngine(announceReady = voskLoopJob?.isActive != true)
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEngines(releaseModel = true)
        releaseWakeLock()
        feedbackManager.stopSpeaking()
        serviceScope.cancel()
        VoiceEngineStateHolder.updateStatus(ListeningStatus.STOPPED)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun startAppropriateEngine(announceReady: Boolean = true) {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            VoiceEngineStateHolder.updateStatus(
                ListeningStatus.ERROR,
                error = "Нет разрешения на микрофон. Выдайте доступ в настройках."
            )
            return
        }

        val readyModelDir = voskModelManager.getReadyModelDir()
        if (readyModelDir != null) {
            activeModelDir = readyModelDir
            startVoskCaptureLoop(readyModelDir, announceReady = announceReady)
        } else if (SpeechRecognizer.isRecognitionAvailable(this)) {
            startSystemSpeechFallback()
        } else {
            VoiceEngineStateHolder.updateStatus(
                ListeningStatus.ERROR,
                SpeechEngineKind.NONE,
                "На планшете нет Google-сервисов речи. Скачайте офлайн-модель русского языка (Vosk) во вкладке «Настройка»."
            )
        }
    }

    private fun startVoskCaptureLoop(modelDir: File, announceReady: Boolean) {
        if (voskLoopJob?.isActive == true) {
            VoiceEngineStateHolder.updateStatus(
                if (isPausedByUser) ListeningStatus.PAUSED else ListeningStatus.LISTENING,
                SpeechEngineKind.VOSK_OFFLINE
            )
            updateNotification(isPaused = isPausedByUser)
            return
        }

        val myGen = ++captureGeneration
        val jobToWait = closingLoopJob ?: voskLoopJob
        jobToWait?.cancel()
        closingLoopJob = null
        VoiceEngineStateHolder.updateStatus(ListeningStatus.INITIALIZING, SpeechEngineKind.VOSK_OFFLINE)

        voskLoopJob = serviceScope.launch(Dispatchers.IO) {
            var recognizer: Recognizer? = null
            var recorder: AudioRecord? = null
            var announced = false

            try {
                try {
                    jobToWait?.join()
                } catch (_: Exception) {
                }
                if (!isActive || captureGeneration != myGen) return@launch

                val loadedModel = voskModel ?: Model(modelDir.absolutePath).also { voskModel = it }
                if (!isActive || captureGeneration != myGen) return@launch

                recognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
                val audioBuffer = ShortArray(READ_CHUNK_SAMPLES)
                var zeroReadStreak = 0
                var lastPartialCheckMs = 0L
                var lastSentPartial = ""

                while (isActive && captureGeneration == myGen && !isPausedByUser) {
                    if (recorder == null) {
                        recorder = createInitializedAudioRecord()
                        if (recorder == null) {
                            mainHandler.post {
                                if (captureGeneration == myGen && !isPausedByUser) {
                                    VoiceEngineStateHolder.updateStatus(
                                        ListeningStatus.ERROR,
                                        SpeechEngineKind.VOSK_OFFLINE,
                                        "Микрофон занят другим приложением — восстанавливаю прослушивание..."
                                    )
                                }
                            }
                            delay(900L)
                            continue
                        }

                        if (!announced) {
                            announced = true
                            mainHandler.post {
                                if (captureGeneration == myGen) {
                                    VoiceEngineStateHolder.clearError()
                                    VoiceEngineStateHolder.updateStatus(
                                        if (isPausedByUser) ListeningStatus.PAUSED else ListeningStatus.LISTENING,
                                        SpeechEngineKind.VOSK_OFFLINE
                                    )
                                    updateNotification(isPaused = isPausedByUser)
                                    if (announceReady && !isPausedByUser) {
                                        feedbackManager.vibrateAck()
                                        if (currentSettings.speakConfirmations) {
                                            feedbackManager.speak("Слушаю вас")
                                        }
                                    }
                                }
                            }
                        } else {
                            mainHandler.post {
                                if (captureGeneration == myGen && !isPausedByUser) {
                                    VoiceEngineStateHolder.clearError()
                                    VoiceEngineStateHolder.updateStatus(
                                        ListeningStatus.LISTENING,
                                        SpeechEngineKind.VOSK_OFFLINE
                                    )
                                }
                            }
                        }
                    }

                    val activeRecorder = recorder!!
                    if (activeRecorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        releaseRecorderQuietly(activeRecorder)
                        recorder = null
                        delay(450L)
                        continue
                    }

                    val nread = try {
                        activeRecorder.read(audioBuffer, 0, audioBuffer.size)
                    } catch (_: Exception) {
                        -1
                    }

                    if (nread < 0) {
                        // AudioRecord preempted by Honor/Huawei or another app — release and recover smoothly
                        releaseRecorderQuietly(activeRecorder)
                        recorder = null
                        zeroReadStreak = 0
                        needResetRecognizer = true
                        delay(500L)
                        continue
                    }

                    if (nread == 0) {
                        zeroReadStreak++
                        delay(30L)
                        if (zeroReadStreak >= 75) {
                            releaseRecorderQuietly(activeRecorder)
                            recorder = null
                            zeroReadStreak = 0
                            needResetRecognizer = true
                            delay(350L)
                        }
                        continue
                    }

                    zeroReadStreak = 0

                    if (pauseCaptureForTts) {
                        needResetRecognizer = true
                        lastSentPartial = ""
                        continue
                    }

                    if (needResetRecognizer) {
                        recognizer.reset()
                        needResetRecognizer = false
                        lastSentPartial = ""
                    }

                    val hasFinal = recognizer.acceptWaveForm(audioBuffer, nread)
                    if (hasFinal) {
                        val finalJson = recognizer.result
                        lastSentPartial = ""
                        val text = extractJsonField(finalJson, "text")
                        if (text.isNotBlank()) {
                            mainHandler.post {
                                if (captureGeneration == myGen) {
                                    handleVoskFinal(text)
                                }
                            }
                        }
                    } else {
                        val nowMs = System.currentTimeMillis()
                        if ((nowMs - lastPartialCheckMs) >= 110L) {
                            lastPartialCheckMs = nowMs
                            val partial = extractJsonField(recognizer.partialResult, "partial")
                            if (partial != lastSentPartial) {
                                lastSentPartial = partial
                                mainHandler.post {
                                    if (captureGeneration == myGen) {
                                        handleVoskPartial(partial)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (captureGeneration == myGen && !isPausedByUser) {
                        VoiceEngineStateHolder.updateStatus(
                            ListeningStatus.ERROR,
                            SpeechEngineKind.VOSK_OFFLINE,
                            "Перезапуск распознавания: ${e.message ?: "сбой аудио"}"
                        )
                        scheduleCaptureRetry(myGen)
                    }
                }
            } finally {
                releaseRecorderQuietly(recorder)
                try {
                    recognizer?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedAudioRecord(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(READ_CHUNK_SAMPLES * 2)
        val bufferBytes = max(minBuf * 2, SAMPLE_RATE) // ~0.5s buffer at 16kHz 16-bit

        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            var candidate: AudioRecord? = null
            try {
                candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    candidate.startRecording()
                    if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        return candidate
                    }
                }
                releaseRecorderQuietly(candidate)
            } catch (_: SecurityException) {
                releaseRecorderQuietly(candidate)
                return null
            } catch (_: Exception) {
                releaseRecorderQuietly(candidate)
            }
        }
        return null
    }

    private fun releaseRecorderQuietly(recorder: AudioRecord?) {
        if (recorder == null) return
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder.release()
        } catch (_: Exception) {
        }
    }

    private fun scheduleCaptureRetry(expectedGen: Long) {
        if (isPausedByUser) return
        val dir = activeModelDir ?: voskModelManager.getReadyModelDir() ?: return
        serviceScope.launch {
            delay(1400L)
            if (!isPausedByUser && captureGeneration == expectedGen && voskLoopJob?.isActive != true) {
                startVoskCaptureLoop(dir, announceReady = false)
            }
        }
    }

    private fun handleVoskPartial(partial: String) {
        if (isPausedByUser || feedbackManager.isSpeaking.value) return
        if (partial.isBlank()) {
            urgentPartialJob?.cancel()
            lastUrgentPartialCandidate = ""
            if (lastPartialPreview.isNotEmpty()) {
                lastPartialPreview = ""
                VoiceEngineStateHolder.updatePartialTranscript("")
            }
            return
        }

        // Keep UI clean from random room/TV speech and suppress redundant emissions
        val preview = if (RussianVoiceCommandParser.isLikelyCommandPrefix(partial)) partial else ""
        if (preview != lastPartialPreview) {
            lastPartialPreview = preview
            VoiceEngineStateHolder.updatePartialTranscript(preview)
        }

        val norm = RussianVoiceCommandParser.normalize(partial)
        if (RussianVoiceCommandParser.isUrgentPartialCommand(norm) && !currentSettings.wakeWordEnabled) {
            if (norm != lastUrgentPartialCandidate) {
                lastUrgentPartialCandidate = norm
                urgentPartialJob?.cancel()
                urgentPartialJob = serviceScope.launch {
                    delay(420L)
                    if (!isPausedByUser && !feedbackManager.isSpeaking.value) {
                        handleRecognizedPhrase(partial, fromUrgentPartial = true)
                    }
                }
            }
        } else {
            urgentPartialJob?.cancel()
            lastUrgentPartialCandidate = ""
        }
    }

    private fun handleVoskFinal(text: String) {
        urgentPartialJob?.cancel()
        lastUrgentPartialCandidate = ""
        if (lastPartialPreview.isNotEmpty()) {
            lastPartialPreview = ""
            VoiceEngineStateHolder.updatePartialTranscript("")
        }
        if (text.isNotBlank()) {
            handleRecognizedPhrase(text, fromUrgentPartial = false)
        }
    }

    private fun startSystemSpeechFallback() {
        mainHandler.post {
            try {
                if (systemRecognizer == null) {
                    systemRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(systemRecognitionListener)
                    }
                }
                VoiceEngineStateHolder.updateStatus(ListeningStatus.LISTENING, SpeechEngineKind.SYSTEM_RECOGNIZER)
                updateNotification(isPaused = false)
                startSystemListeningCycle()
            } catch (_: Exception) {
                VoiceEngineStateHolder.updateStatus(
                    ListeningStatus.ERROR,
                    SpeechEngineKind.NONE,
                    "Системное распознавание недоступно. Скачайте офлайн-модель Vosk."
                )
            }
        }
    }

    private fun startSystemListeningCycle() {
        if (isPausedByUser || feedbackManager.isSpeaking.value) return
        val recognizer = systemRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isSystemListening = true
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isSystemListening = false
        }
    }

    private val systemRecognitionListener = object : AndroidRecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isSystemListening = false
        }

        override fun onError(error: Int) {
            isSystemListening = false
            if (!isPausedByUser) {
                mainHandler.postDelayed({ startSystemListeningCycle() }, 450L)
            }
        }

        override fun onResults(results: Bundle?) {
            isSystemListening = false
            VoiceEngineStateHolder.updatePartialTranscript("")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                handleRecognizedPhrase(text, fromUrgentPartial = false)
            }
            if (!isPausedByUser) {
                mainHandler.postDelayed({ startSystemListeningCycle() }, 350L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            if (partial.isNotBlank() && RussianVoiceCommandParser.isLikelyCommandPrefix(partial)) {
                VoiceEngineStateHolder.updatePartialTranscript(partial)
            } else {
                VoiceEngineStateHolder.updatePartialTranscript("")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleRecognizedPhrase(rawText: String, fromUrgentPartial: Boolean) {
        if (feedbackManager.isSpeaking.value || isPausedByUser) return

        val norm = RussianVoiceCommandParser.normalize(rawText)
        val now = System.currentTimeMillis()
        if (norm == lastExecutedNormText && (now - lastExecutedTimestampMs) < 2600L) {
            return
        }

        val parsed = RussianVoiceCommandParser.parse(
            rawText = rawText,
            requireWakeWord = currentSettings.wakeWordEnabled,
            wakeWord = currentSettings.wakeWord,
            strictCommandsOnly = currentSettings.strictCommandsOnly,
            hasPendingConfirmation = tabletController.hasPendingConfirmation()
        )

        // Silently ignore surrounding speech / TV audio unless user explicitly addressed a command
        if (parsed == null || (parsed is VoiceCommand.Unknown && !parsed.shouldPromptRepeat)) {
            if (lastPartialPreview.isNotEmpty()) {
                lastPartialPreview = ""
                VoiceEngineStateHolder.updatePartialTranscript("")
            }
            return
        }

        val cmdKey = parsed.toString()
        val dedupeWindow = if (fromUrgentPartial) 1800L else 2600L
        if (cmdKey == lastExecutedCommandClass && (now - lastExecutedTimestampMs) < dedupeWindow) {
            return
        }

        lastExecutedNormText = norm
        lastExecutedCommandClass = cmdKey
        lastExecutedTimestampMs = now
        tabletController.execute(parsed, rawText, currentSettings, fromVoice = true)
    }

    private fun stopEngines(releaseModel: Boolean) {
        captureGeneration++
        urgentPartialJob?.cancel()
        lastUrgentPartialCandidate = ""
        lastPartialPreview = ""
        val prevLoop = voskLoopJob
        voskLoopJob = null
        closingLoopJob = prevLoop
        prevLoop?.cancel()

        if (releaseModel) {
            val modelToClose = voskModel
            voskModel = null
            if (modelToClose != null) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        prevLoop?.join()
                    } catch (_: Exception) {
                    }
                    try {
                        modelToClose.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        stopSystemRecognizer()
    }

    private fun stopSystemRecognizer() {
        isSystemListening = false
        mainHandler.post {
            try {
                systemRecognizer?.cancel()
                systemRecognizer?.destroy()
            } catch (_: Exception) {
            }
            systemRecognizer = null
        }
    }

    private fun extractJsonField(json: String?, field: String): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString(field, "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun ensureForegroundNotification(isPaused: Boolean) {
        val notification = buildNotification(isPaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(isPaused: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceBackgroundService::class.java).apply {
                action = if (isPaused) ACTION_RESUME_LISTENING else ACTION_PAUSE_LISTENING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(
            if (isPaused) R.string.notification_title_paused else R.string.notification_title_listening
        )
        val text = getString(
            if (isPaused) R.string.notification_text_paused else R.string.notification_text_listening
        )
        val toggleLabel = getString(
            if (isPaused) R.string.action_resume else R.string.action_pause
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, toggleLabel, toggleIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }
}
