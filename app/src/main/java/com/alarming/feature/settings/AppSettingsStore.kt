package com.alarming.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class AppSettings(
    val darkTheme: Boolean = false,
    val keepScreenOn: Boolean = true,
    val defaultStageTone: String = "Soft bell",
    val defaultStartTone: String = "Focus tone",
    val defaultCompletionTone: String = "Completion chime",
    val defaultReminderTone: String = "Soft bell",
    val defaultVibration: Boolean = false,
)

enum class DefaultToneSlot { STAGE, START, COMPLETION, REMINDER }

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class AppSettingsStore private constructor(context: Context) {
    private val store = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)

    suspend fun setDarkTheme(enabled: Boolean) = store.edit { it[DARK_THEME] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = store.edit { it[KEEP_SCREEN_ON] = enabled }
    suspend fun setDefaultVibration(enabled: Boolean) = store.edit { it[DEFAULT_VIBRATION] = enabled }
    suspend fun setDefaultTone(slot: DefaultToneSlot, toneId: String) = store.edit { it[keyFor(slot)] = toneId }
    val hiddenTones: Flow<Set<String>> = store.data.map { it[HIDDEN_TONES] ?: emptySet() }
    suspend fun hideTone(id: String) = store.edit { it[HIDDEN_TONES] = (it[HIDDEN_TONES] ?: emptySet()) + id }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            darkTheme = prefs[DARK_THEME] ?: defaults.darkTheme,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            defaultStageTone = prefs[STAGE_TONE] ?: defaults.defaultStageTone,
            defaultStartTone = prefs[START_TONE] ?: defaults.defaultStartTone,
            defaultCompletionTone = prefs[COMPLETION_TONE] ?: defaults.defaultCompletionTone,
            defaultReminderTone = prefs[REMINDER_TONE] ?: defaults.defaultReminderTone,
            defaultVibration = prefs[DEFAULT_VIBRATION] ?: defaults.defaultVibration,
        )
    }

    companion object {
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val DEFAULT_VIBRATION = booleanPreferencesKey("default_vibration")
        private val STAGE_TONE = stringPreferencesKey("default_stage_tone")
        private val START_TONE = stringPreferencesKey("default_start_tone")
        private val COMPLETION_TONE = stringPreferencesKey("default_completion_tone")
        private val REMINDER_TONE = stringPreferencesKey("default_reminder_tone")
        private val HIDDEN_TONES = stringSetPreferencesKey("hidden_bundled_tones")

        private fun keyFor(slot: DefaultToneSlot) = when (slot) {
            DefaultToneSlot.STAGE -> STAGE_TONE
            DefaultToneSlot.START -> START_TONE
            DefaultToneSlot.COMPLETION -> COMPLETION_TONE
            DefaultToneSlot.REMINDER -> REMINDER_TONE
        }

        @Volatile private var instance: AppSettingsStore? = null
        fun get(context: Context): AppSettingsStore = instance ?: synchronized(this) {
            instance ?: AppSettingsStore(context.applicationContext).also { instance = it }
        }
    }
}

fun AppSettings.tone(slot: DefaultToneSlot): String = when (slot) {
    DefaultToneSlot.STAGE -> defaultStageTone
    DefaultToneSlot.START -> defaultStartTone
    DefaultToneSlot.COMPLETION -> defaultCompletionTone
    DefaultToneSlot.REMINDER -> defaultReminderTone
}
