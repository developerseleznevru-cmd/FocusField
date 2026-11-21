package ru.seleznev.focusfield.ui.account

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.BestDay
import ru.seleznev.focusfield.data.model.StatsAggregator
import ru.seleznev.focusfield.data.repo.AuthRepository
import ru.seleznev.focusfield.data.repo.FocusRepository
import ru.seleznev.focusfield.util.DayMath
import java.util.TimeZone

/** Компактная сводка статистики для экрана «Аккаунт». */
data class AccountStats(
    val sessions: Int,
    val totalMinutes: Long,
    val streakDays: Int,
    val bestDay: BestDay?
)

/**
 * Экран «Аккаунт» (вкладка нижнего меню): данные текущего аккаунта,
 * статус режима, компактная статистика, смена пароля и удаление аккаунта.
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val focusRepository: FocusRepository
) : ViewModel() {

    /** Email текущего аккаунта (только отображение). */
    val email: String get() = authRepository.currentEmail()

    /** Сохранённое ФИО профиля; пусто, если не заполнено. */
    val fullName: String get() = authRepository.profileFullName()

    /** Фото профиля; null — показывается заглушка. */
    val avatar: Bitmap? get() = authRepository.avatarBitmap()

    /** true — работает Firebase, иначе локальный режим. */
    val isFirebaseAvailable: Boolean get() = authRepository.isFirebaseAvailable

    private val _stats = MutableLiveData<AccountStats?>(null)
    val stats: LiveData<AccountStats?> = _stats

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    init {
        loadStats()
    }

    /** Пересчёт сводки (все сессии за всё время). */
    fun loadStats() {
        viewModelScope.launch {
            val sessions = focusRepository.sessionsSince(0)
            val summary = StatsAggregator.summary(sessions)
            val now = System.currentTimeMillis()
            _stats.value = AccountStats(
                sessions = summary.sessions,
                totalMinutes = summary.totalMinutes,
                streakDays = StatsAggregator.streakDays(sessions, now, TimeZone.getDefault()),
                bestDay = StatsAggregator.bestDay(sessions, TimeZone.getDefault())
            )
        }
    }

    /** Выход: MainActivity наблюдает состояние аутентификации и уводит на экран входа. */
    fun signOut() = authRepository.signOut()

    /** Смена пароля текущего аккаунта; результат — в [message]. */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            authRepository.changePassword(currentPassword, newPassword)
                .onSuccess { _message.value = PASSWORD_CHANGED }
                .onFailure { error -> _message.value = error.message ?: ERROR_UNKNOWN }
        }
    }

    /**
     * Удаление аккаунта: при успехе состояние становится SignedOut,
     * MainActivity сама уводит на экран входа.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.deleteAccount()
                .onFailure { error -> _message.value = error.message ?: ERROR_UNKNOWN }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Форматирование длительности: «45 мин» или «2 ч 15 мин». */
    fun formatMinutes(minutes: Long): String = when {
        minutes <= 0 -> "0 мин"
        minutes < 60 -> "$minutes мин"
        else -> "${minutes / 60} ч ${minutes % 60} мин"
    }

    /** Серия дней подряд для отображения: «3 дн». */
    fun formatStreak(days: Int): String =
        if (days <= 0) "0 дн" else "$days дн"

    /** Дата лучшего дня «dd.MM»; null — «—». */
    fun formatBestDay(day: BestDay?): String =
        day?.let { DayMath.formatDay(it.dayStart) } ?: DASH

    private companion object {
        const val PASSWORD_CHANGED = "Пароль изменён"
        const val ERROR_UNKNOWN = "Не удалось выполнить операцию"
        const val DASH = "—"
    }
}
