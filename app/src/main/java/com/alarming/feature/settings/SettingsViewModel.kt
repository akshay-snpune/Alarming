package com.alarming.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarming.audio.ToneLibrary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppSettingsStore.get(application)
    private val tones = ToneLibrary(application)

    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val toneDeletionError = MutableStateFlow<String?>(null)

    fun setDarkTheme(enabled: Boolean) { viewModelScope.launch { store.setDarkTheme(enabled) } }
    fun setColorTheme(theme: AppColorTheme) { viewModelScope.launch { store.setColorTheme(theme) } }
    fun setKeepScreenOn(enabled: Boolean) { viewModelScope.launch { store.setKeepScreenOn(enabled) } }
    fun setDefaultVibration(enabled: Boolean) { viewModelScope.launch { store.setDefaultVibration(enabled) } }
    fun setDefaultTone(slot: DefaultToneSlot, toneId: String) { viewModelScope.launch { store.setDefaultTone(slot, toneId) } }

    fun deleteTone(toneId: String) {
        viewModelScope.launch {
            toneDeletionError.value = null
            try {
                tones.delete(toneId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                toneDeletionError.value = failure.message ?: "Could not delete this tone."
            }
        }
    }
}
