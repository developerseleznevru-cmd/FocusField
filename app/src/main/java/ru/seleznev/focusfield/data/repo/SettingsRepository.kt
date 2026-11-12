package ru.seleznev.focusfield.data.repo

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.seleznev.focusfield.data.model.SettingsState

/**
 * Репозиторий настроек: хранит состояние в зашифрованных SharedPreferences
 * и публикует его как StateFlow для подписки из UI и сервисов.
 */
class SettingsRepository(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun load(): SettingsState = SettingsState(
        theme = prefs.getInt(KEY_THEME, 0),
        blockEnabled = prefs.getBoolean(KEY_BLOCK_ENABLED, true),
        blockOnlyInSession = prefs.getBoolean(KEY_BLOCK_ONLY_IN_SESSION, true),
        blockThreshold = prefs.getInt(KEY_THRESHOLD, 55),
        graceMinutes = prefs.getInt(KEY_GRACE, 5),
        cameraMonitoring = prefs.getBoolean(KEY_CAMERA, true),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        wearEnabled = prefs.getBoolean(KEY_WEAR, true),
        syncEnabled = prefs.getBoolean(KEY_SYNC, false),
        apiBaseUrl = prefs.getString(KEY_API_URL, "").orEmpty(),
        scheduleEnabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
        scheduleStartMinutes = prefs.getInt(KEY_SCHEDULE_START, 9 * 60),
        scheduleEndMinutes = prefs.getInt(KEY_SCHEDULE_END, 18 * 60)
    )

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putInt(KEY_THEME, next.theme)
            .putBoolean(KEY_BLOCK_ENABLED, next.blockEnabled)
            .putBoolean(KEY_BLOCK_ONLY_IN_SESSION, next.blockOnlyInSession)
            .putInt(KEY_THRESHOLD, next.blockThreshold)
            .putInt(KEY_GRACE, next.graceMinutes)
            .putBoolean(KEY_CAMERA, next.cameraMonitoring)
            .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
            .putBoolean(KEY_WEAR, next.wearEnabled)
            .putBoolean(KEY_SYNC, next.syncEnabled)
            .putString(KEY_API_URL, next.apiBaseUrl)
            .putBoolean(KEY_SCHEDULE_ENABLED, next.scheduleEnabled)
            .putInt(KEY_SCHEDULE_START, next.scheduleStartMinutes)
            .putInt(KEY_SCHEDULE_END, next.scheduleEndMinutes)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_BLOCK_ENABLED = "block_enabled"
        const val KEY_BLOCK_ONLY_IN_SESSION = "block_only_in_session"
        const val KEY_THRESHOLD = "block_threshold"
        const val KEY_GRACE = "grace_minutes"
        const val KEY_CAMERA = "camera_monitoring"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_WEAR = "wear_enabled"
        const val KEY_SYNC = "sync_enabled"
        const val KEY_API_URL = "api_base_url"
        const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        const val KEY_SCHEDULE_START = "schedule_start_minutes"
        const val KEY_SCHEDULE_END = "schedule_end_minutes"
    }
}
