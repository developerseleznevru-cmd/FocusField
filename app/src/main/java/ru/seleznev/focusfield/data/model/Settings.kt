package ru.seleznev.focusfield.data.model

/**
 * Состояние настроек приложения.
 * Хранится в зашифрованных SharedPreferences (см. SecurePrefs).
 */
data class SettingsState(
    /** Тема: 0 — системная, 1 — светлая, 2 — тёмная. */
    val theme: Int = 0,
    /** Включена ли блокировка отвлекающего контента. */
    val blockEnabled: Boolean = true,
    /** Блокировать только во время сессии концентрации (иначе — всегда при низкой концентрации). */
    val blockOnlyInSession: Boolean = true,
    /** Порог концентрации: ниже него доступ к заблокированным ресурсам ограничивается. */
    val blockThreshold: Int = 55,
    /** Длительность «помехи» — временного разрешения доступа, минут. */
    val graceMinutes: Int = 5,
    /** Мониторинг через камеру включён. */
    val cameraMonitoring: Boolean = true,
    /** Уведомления о задачах включены. */
    val notificationsEnabled: Boolean = true,
    /** Интеграция с Wear OS включена. */
    val wearEnabled: Boolean = true,
    /** Синхронизация с REST API включена. */
    val syncEnabled: Boolean = false,
    /** Базовый URL REST API. */
    val apiBaseUrl: String = "",
    /** Режим расписания: блокировка/мониторинг активны только в заданный интервал. */
    val scheduleEnabled: Boolean = false,
    /** Начало интервала расписания, минут от полуночи. */
    val scheduleStartMinutes: Int = 9 * 60,
    /** Конец интервала расписания, минут от полуночи. */
    val scheduleEndMinutes: Int = 18 * 60
)
