package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voiceSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "golosgid_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val STRICT_COMMANDS_ONLY = booleanPreferencesKey("strict_commands_only")
        val RETURN_TO_APP_ON_CLOSE = booleanPreferencesKey("return_to_app_on_close")
        val SPEAK_CONFIRMATIONS = booleanPreferencesKey("speak_confirmations")
        val SIMPLE_SPEECH_MODE = booleanPreferencesKey("simple_speech_mode")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val AUTO_OPEN_FIRST_VIDEO = booleanPreferencesKey("auto_open_first_video")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val EMERGENCY_CONTACT_NAME = stringPreferencesKey("emergency_contact_name")
        val EMERGENCY_CONTACT_PHONE = stringPreferencesKey("emergency_contact_phone")
    }

    val settingsFlow: Flow<VoiceSettings> = context.voiceSettingsDataStore.data.map { prefs ->
        VoiceSettings(
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWord = prefs[Keys.WAKE_WORD] ?: "слушай",
            strictCommandsOnly = prefs[Keys.STRICT_COMMANDS_ONLY] ?: true,
            returnToAppOnClose = prefs[Keys.RETURN_TO_APP_ON_CLOSE] ?: true,
            speakConfirmations = prefs[Keys.SPEAK_CONFIRMATIONS] ?: true,
            simpleSpeechMode = prefs[Keys.SIMPLE_SPEECH_MODE] ?: false,
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f,
            autoOpenFirstVideo = prefs[Keys.AUTO_OPEN_FIRST_VIDEO] ?: false,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "Москва",
            emergencyContactName = prefs[Keys.EMERGENCY_CONTACT_NAME] ?: "",
            emergencyContactPhone = prefs[Keys.EMERGENCY_CONTACT_PHONE] ?: ""
        )
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setWakeWord(word: String) {
        val clean = word.trim().lowercase().ifBlank { "слушай" }
        context.voiceSettingsDataStore.edit { it[Keys.WAKE_WORD] = clean }
    }

    suspend fun setStrictCommandsOnly(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.STRICT_COMMANDS_ONLY] = enabled }
    }

    suspend fun setReturnToAppOnClose(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.RETURN_TO_APP_ON_CLOSE] = enabled }
    }

    suspend fun setSpeakConfirmations(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.SPEAK_CONFIRMATIONS] = enabled }
    }

    suspend fun setSimpleSpeechMode(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.SIMPLE_SPEECH_MODE] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.voiceSettingsDataStore.edit {
            it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f)
        }
    }

    suspend fun setAutoOpenFirstVideo(enabled: Boolean) {
        context.voiceSettingsDataStore.edit { it[Keys.AUTO_OPEN_FIRST_VIDEO] = enabled }
    }

    suspend fun setWeatherCity(city: String) {
        context.voiceSettingsDataStore.edit {
            it[Keys.WEATHER_CITY] = city.trim().ifBlank { "Москва" }
        }
    }

    suspend fun setEmergencyContact(name: String, phone: String) {
        context.voiceSettingsDataStore.edit {
            it[Keys.EMERGENCY_CONTACT_NAME] = name.trim()
            it[Keys.EMERGENCY_CONTACT_PHONE] = phone.trim()
        }
    }
}
