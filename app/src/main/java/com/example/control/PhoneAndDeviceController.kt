package com.example.control

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class TorchResult {
    data class Changed(val enabled: Boolean) : TorchResult()
    data object Unavailable : TorchResult()
    data class Failed(val message: String) : TorchResult()
}

sealed class BrightnessResult {
    data class Changed(val percent: Int, val autoMode: Boolean = false) : BrightnessResult()
    data object MissingWriteSettingsPermission : BrightnessResult()
    data class Failed(val message: String) : BrightnessResult()
}

data class MissedCallInfo(
    val caller: String,
    val timeLabel: String
)

object PhoneAndDeviceController {

    private var findPhonePlayer: MediaPlayer? = null
    private var findPhoneJob: Job? = null

    @Volatile
    private var torchCallbackRegistered = false

    @Volatile
    var isTorchOn: Boolean = false
        private set

    fun ensureTorchStateTracking(context: Context) {
        if (torchCallbackRegistered) return
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    isTorchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
            torchCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    fun setFlashlight(context: Context, enabled: Boolean): TorchResult {
        ensureTorchStateTracking(context)
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var targetId: String? = null
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetId = id
                    break
                } else if (hasFlash && targetId == null) {
                    targetId = id
                }
            }
            if (targetId == null) {
                TorchResult.Unavailable
            } else {
                cm.setTorchMode(targetId, enabled)
                isTorchOn = enabled
                TorchResult.Changed(enabled)
            }
        } catch (e: Exception) {
            TorchResult.Failed("Не удалось переключить фонарик: ${e.message ?: "камера занята"}")
        }
    }

    fun toggleFlashlight(context: Context): TorchResult {
        ensureTorchStateTracking(context)
        return setFlashlight(context, !isTorchOn)
    }

    // --- Wi-Fi & Internet ---
    @Suppress("DEPRECATION")
    fun checkWifiAndInternet(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = try {
            wm?.isWifiEnabled == true
        } catch (_: Exception) {
            false
        }
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val viaWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val viaCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        return when {
            viaWifi && hasInternet -> "Wi-Fi включён, интернет работает"
            wifiEnabled && !hasInternet -> "Wi-Fi включён, но нет подключения к интернету"
            viaCellular && hasInternet -> "Wi-Fi выключен, интернет работает через мобильную сеть"
            wifiEnabled -> "Wi-Fi включён"
            else -> "Wi-Fi выключен, интернет не подключён"
        }
    }

    @Suppress("DEPRECATION")
    fun setWifiOrOpenPanel(context: Context, enabled: Boolean): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && wm != null) {
            try {
                if (wm.setWifiEnabled(enabled)) {
                    return if (enabled) "Wi-Fi включён" else "Wi-Fi выключен"
                }
            } catch (_: Exception) {
            }
        }
        openWifiSettingsPanel(context)
        val actionWord = if (enabled) "включить" else "выключить"
        return "На Android нельзя $actionWord Wi-Fi без подтверждения. Открываю панель настроек Wi-Fi."
    }

    fun openWifiSettingsPanel(context: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (YouTubeLauncher.startActivityReliably(context, intent)) return true
        val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return YouTubeLauncher.startActivityReliably(context, fallback)
    }

    // --- Bluetooth ---
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun checkBluetoothAndHeadphones(context: Context): String {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val enabled = try {
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            null
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val btAudioConnected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        return when {
            btAudioConnected -> "Bluetooth включён, беспроводные наушники или колонка подключены"
            enabled == true -> "Bluetooth включён, аудиоустройства не подключены"
            enabled == false -> "Bluetooth выключен"
            else -> "Для проверки Bluetooth разрешите доступ к устройствам рядом в Настройке"
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun setBluetoothOrOpenSettings(context: Context, enabled: Boolean): String {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (hasPerm && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && adapter != null) {
            try {
                val ok = if (enabled) adapter.enable() else adapter.disable()
                if (ok) return if (enabled) "Bluetooth включён" else "Bluetooth выключен"
            } catch (_: Exception) {
            }
        }
        openBluetoothSettings(context)
        return "Открываю настройки Bluetooth для переключения и подключения наушников"
    }

    fun openBluetoothSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return YouTubeLauncher.startActivityReliably(context, intent)
    }

    // --- Brightness ---
    fun readBrightnessStatus(context: Context): String {
        return try {
            val resolver = context.contentResolver
            val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            val valRaw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(1, 255)
            val pct = ((valRaw / 255f) * 100f).toInt().coerceIn(1, 100)
            val modeStr = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                "включена автояркость"
            } else {
                "ручной режим"
            }
            "Яркость экрана $pct процентов, $modeStr"
        } catch (_: Exception) {
            "Не удалось прочитать уровень яркости"
        }
    }

    fun adjustBrightness(context: Context, brighter: Boolean): BrightnessResult {
        if (!Settings.System.canWrite(context)) {
            return BrightnessResult.MissingWriteSettingsPermission
        }
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                .coerceIn(10, 255)
            val delta = if (brighter) 60 else -60
            val next = (current + delta).coerceIn(15, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
            val pct = ((next / 255f) * 100f).toInt().coerceIn(5, 100)
            BrightnessResult.Changed(pct)
        } catch (e: Exception) {
            BrightnessResult.Failed("Не удалось изменить яркость: ${e.message}")
        }
    }

    fun setBrightnessExtreme(context: Context, max: Boolean): BrightnessResult {
        if (!Settings.System.canWrite(context)) {
            return BrightnessResult.MissingWriteSettingsPermission
        }
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val target = if (max) 255 else 15
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            BrightnessResult.Changed(if (max) 100 else 6)
        } catch (e: Exception) {
            BrightnessResult.Failed("Не удалось установить яркость: ${e.message}")
        }
    }

    fun setAutoBrightness(context: Context, enabled: Boolean): BrightnessResult {
        if (!Settings.System.canWrite(context)) {
            return BrightnessResult.MissingWriteSettingsPermission
        }
        return try {
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            BrightnessResult.Changed(percent = 0, autoMode = enabled)
        } catch (e: Exception) {
            BrightnessResult.Failed("Не удалось переключить автояркость: ${e.message}")
        }
    }

    // --- System Toggles (Rotate, DND, Location) ---
    fun setAutoRotate(context: Context, enabled: Boolean): Pair<Boolean, String> {
        if (!Settings.System.canWrite(context)) {
            return false to "Разрешите изменение системных настроек в разделе Настройка для автоповорота"
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            true to if (enabled) "Автоповорот экрана включён" else "Автоповорот экрана отключён"
        } catch (_: Exception) {
            false to "Не удалось изменить автоповорот экрана"
        }
    }

    fun setDoNotDisturb(context: Context, enabled: Boolean): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            val filter = if (enabled) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(filter)
            return if (enabled) "Режим «Не беспокоить» включён" else "Режим «Не беспокоить» отключён"
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        YouTubeLauncher.startActivityReliably(context, intent)
        return "Для управления режимом Не беспокоить выдайте доступ приложению ГолосГид. Открываю настройки."
    }

    fun openLocationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return YouTubeLauncher.startActivityReliably(context, intent)
    }

    // --- Phone Calls & Audio ---
    @SuppressLint("MissingPermission")
    fun answerIncomingCall(context: Context): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false to "Ответ на звонок голосом поддерживается с Android 8"
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return false to "Разрешите управление звонками в разделе Настройка"
        }
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            tm.acceptRingingCall()
            true to "Отвечаю на звонок"
        } catch (_: Exception) {
            false to "Нет входящего звонка или система запретила ответ"
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectOrEndCall(context: Context): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false to "Сброс звонка голосом поддерживается с Android 9"
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return false to "Разрешите управление звонками в разделе Настройка"
        }
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            val ended = tm.endCall()
            if (ended) true to "Звонок завершён" else false to "Активный или входящий звонок не найден"
        } catch (_: Exception) {
            false to "Не удалось завершить звонок"
        }
    }

    fun enableSpeakerphone(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) am.setCommunicationDevice(speaker)
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disableSpeakerphone(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun adjustRingVolume(context: Context, raise: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_RING, dir, AudioManager.FLAG_SHOW_UI)
    }

    fun setMaxMediaVolume(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
    }

    fun readVolumeSummary(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val musicCur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val ringCur = am.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val musicPct = ((musicCur.toFloat() / musicMax) * 100f).toInt()
        val ringPct = ((ringCur.toFloat() / ringMax) * 100f).toInt()
        return "Громкость мультимедиа $musicPct процентов, громкость звонка $ringPct процентов"
    }

    @SuppressLint("MissingPermission")
    fun getMissedCalls(context: Context, limit: Int = 5): Result<List<MissedCallInfo>> {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return Result.failure(SecurityException("Нет разрешения на журнал звонков"))
        }
        return try {
            val list = mutableListOf<MissedCallInfo>()
            val projection = arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE
            )
            val selection = "${CallLog.Calls.TYPE} = ?"
            val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            val timeFormat = SimpleDateFormat("HH:mm", Locale("ru", "RU"))

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                while (cursor.moveToNext() && list.size < limit) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val label = name.ifBlank { num.ifBlank { "Неизвестный номер" } }
                    val timeStr = if (dateMs > 0L) timeFormat.format(Date(dateMs)) else ""
                    list.add(MissedCallInfo(label, timeStr))
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastCallNumberAndName(context: Context): Result<Pair<String, String>> {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return Result.failure(SecurityException("Нет разрешения на журнал звонков"))
        }
        return try {
            val projection = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER)
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    if (num.isNotBlank()) {
                        return Result.success(num to name.ifBlank { num })
                    }
                }
            }
            Result.failure(IllegalStateException("Журнал звонков пуст"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun callPhoneNumber(context: Context, rawPhone: String): Boolean {
        val cleaned = rawPhone.filter { it.isDigit() || it == '+' }
        if (cleaned.isBlank()) return false

        val canCallDirect = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (canCallDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$cleaned")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return YouTubeLauncher.startActivityReliably(context, intent)
    }

    fun findMatchingPhoneContacts(context: Context, spokenQuery: String, limit: Int = 3): List<Pair<String, String>> {
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return emptyList()

        val candidatesByName = linkedMapOf<String, ContactMatchCandidate>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                if (name.isNotBlank() && num.isNotBlank()) {
                    val key = name.lowercase()
                    if (!candidatesByName.containsKey(key)) {
                        candidatesByName[key] = ContactMatchCandidate(name, null, null, num)
                    }
                }
            }
        }
        return WhatsAppContactCaller.rankCandidates(spokenQuery, candidatesByName.values.toList())
            .take(limit)
            .mapNotNull { (c, _) ->
                val phone = c.phoneNumber ?: return@mapNotNull null
                c.displayName to phone
            }
    }

    // --- Alarms & Timers ---
    fun setAlarm(context: Context, hour: Int?, minute: Int?, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            if (hour != null) {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, (minute ?: 0).coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            } else {
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return YouTubeLauncher.startActivityReliably(context, intent)
    }

    fun dismissOrShowAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val dismiss = Intent(AlarmClock.ACTION_DISMISS_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (YouTubeLauncher.startActivityReliably(context, dismiss)) return true
        }
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return YouTubeLauncher.startActivityReliably(context, show)
    }

    fun snoozeAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (YouTubeLauncher.startActivityReliably(context, intent)) return true
        }
        return false
    }

    fun setTimer(context: Context, minutes: Int, label: String): Boolean {
        val seconds = (minutes.coerceIn(1, 720)) * 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return YouTubeLauncher.startActivityReliably(context, intent)
    }

    @Suppress("DEPRECATION")
    fun startFindMyPhone(context: Context, scope: CoroutineScope) {
        stopFindMyPhone(context)
        findPhoneJob = scope.launch(Dispatchers.IO) {
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, (maxAlarm * 0.85f).toInt().coerceAtLeast(1), 0)

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, alarmUri)
                    isLooping = true
                    prepare()
                    start()
                }
                findPhonePlayer = player

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 250, 400), 0))
                    } else {
                        vibrator.vibrate(longArrayOf(0, 400, 250, 400), 0)
                    }
                }

                delay(14_000L)
                stopFindMyPhone(context)
            } catch (_: Exception) {
                stopFindMyPhone(context)
            }
        }
    }

    fun stopFindMyPhone(context: Context? = null) {
        findPhoneJob?.cancel()
        findPhoneJob = null
        try {
            findPhonePlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            findPhonePlayer?.release()
        } catch (_: Exception) {
        }
        findPhonePlayer = null
        if (context != null) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.cancel()
            } catch (_: Exception) {
            }
        }
    }
}
