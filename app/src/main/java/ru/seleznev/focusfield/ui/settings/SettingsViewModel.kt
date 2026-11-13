package ru.seleznev.focusfield.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import ru.seleznev.focusfield.data.model.SettingsState
import ru.seleznev.focusfield.data.repo.AuthRepository
import ru.seleznev.focusfield.data.repo.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val state: StateFlow<SettingsState> = settingsRepository.state

    /** Выход из аккаунта: MainActivity вернёт пользователя на экран входа. */
    fun signOut() = authRepository.signOut()

    fun setTheme(themeCode: Int) = update { it.copy(theme = themeCode) }

    fun setBlockEnabled(enabled: Boolean) = update { it.copy(blockEnabled = enabled) }

    fun setBlockOnlyInSession(onlyInSession: Boolean) =
        update { it.copy(blockOnlyInSession = onlyInSession) }

    fun setBlockThreshold(threshold: Int) = update { it.copy(blockThreshold = threshold) }

    fun setGraceMinutes(minutes: Int) = update { it.copy(graceMinutes = minutes) }

    fun setCameraMonitoring(enabled: Boolean) = update { it.copy(cameraMonitoring = enabled) }

    fun setNotificationsEnabled(enabled: Boolean) =
        update { it.copy(notificationsEnabled = enabled) }

    fun setWearEnabled(enabled: Boolean) = update { it.copy(wearEnabled = enabled) }

    fun setSyncEnabled(enabled: Boolean) = update { it.copy(syncEnabled = enabled) }

    fun setApiBaseUrl(url: String) = update { it.copy(apiBaseUrl = url.trim()) }

    fun setScheduleEnabled(enabled: Boolean) = update { it.copy(scheduleEnabled = enabled) }

    fun setScheduleTime(startMinutes: Int, endMinutes: Int) =
        update { it.copy(scheduleStartMinutes = startMinutes, scheduleEndMinutes = endMinutes) }

    private fun update(transform: (SettingsState) -> SettingsState) {
        settingsRepository.update(transform)
    }
}
