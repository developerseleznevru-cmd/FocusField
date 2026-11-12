package ru.seleznev.focusfield.data.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.seleznev.focusfield.data.prefs.SecurePrefs

/**
 * Общее состояние блокировки, доступное из ViewModel, Accessibility-сервиса
 * и оверлея: активная сессия, текущий уровень концентрации, «помехи».
 * Дублируется в зашифрованные SharedPreferences, чтобы пережить перезапуск процесса.
 */
class BlockingStateStore private constructor(private val prefs: SharedPreferences) {

    private val _currentScore = MutableStateFlow(prefs.getInt(KEY_SCORE, NO_DATA))
    /** Текущий уровень концентрации 0..100 или [NO_DATA]. */
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()

    private val _sessionActive = MutableStateFlow(prefs.getLong(KEY_SESSION_ID, 0L) != 0L)
    /** Идёт ли сейчас сессия концентрации. */
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    val sessionData: SessionData
        get() = SessionData(
            id = prefs.getLong(KEY_SESSION_ID, 0L),
            startedAt = prefs.getLong(KEY_SESSION_START, 0L),
            interruptions = prefs.getInt(KEY_INTERRUPTIONS, 0)
        )

    data class SessionData(val id: Long, val startedAt: Long, val interruptions: Int)

    /** Публикует новый уровень концентрации (вызывается анализатором биометрии). */
    fun publishScore(score: Int) {
        _currentScore.value = score
        prefs.edit().putInt(KEY_SCORE, score).apply()
    }

    fun startSession(sessionId: Long, startedAt: Long) {
        prefs.edit()
            .putLong(KEY_SESSION_ID, sessionId)
            .putLong(KEY_SESSION_START, startedAt)
            .putInt(KEY_INTERRUPTIONS, 0)
            .apply()
        _sessionActive.value = true
    }

    fun endSession() {
        prefs.edit()
            .putLong(KEY_SESSION_ID, 0L)
            .putLong(KEY_SESSION_START, 0L)
            .putInt(KEY_SCORE, NO_DATA)
            .apply()
        _sessionActive.value = false
        _currentScore.value = NO_DATA
    }

    /** «Помеха»: временное разрешение доступа к заблокированному ресурсу. */
    fun grantOverride(pkg: String, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        prefs.edit()
            .putString(KEY_OVERRIDE_PKG, pkg)
            .putLong(KEY_OVERRIDE_UNTIL, until)
            .apply()
    }

    /** Активно ли временное разрешение для [pkg]. */
    fun isOverrideActive(pkg: String): Boolean {
        val storedPkg = prefs.getString(KEY_OVERRIDE_PKG, null) ?: return false
        val until = prefs.getLong(KEY_OVERRIDE_UNTIL, 0L)
        if (storedPkg != pkg) return false
        if (System.currentTimeMillis() >= until) {
            prefs.edit().remove(KEY_OVERRIDE_PKG).remove(KEY_OVERRIDE_UNTIL).apply()
            return false
        }
        return true
    }

    /** Учитывает «помеху» в счётчике прерываний. */
    fun incrementInterruptions(): Int {
        val next = prefs.getInt(KEY_INTERRUPTIONS, 0) + 1
        prefs.edit().putInt(KEY_INTERRUPTIONS, next).apply()
        return next
    }

    fun interruptions(): Int = prefs.getInt(KEY_INTERRUPTIONS, 0)

    companion object {
        const val NO_DATA = -1

        @Volatile private var instance: BlockingStateStore? = null

        fun get(context: Context): BlockingStateStore =
            instance ?: synchronized(this) {
                instance ?: BlockingStateStore(SecurePrefs.state(context)).also { instance = it }
            }

        private const val KEY_SCORE = "current_score"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_START = "session_started_at"
        private const val KEY_INTERRUPTIONS = "interruptions"
        private const val KEY_OVERRIDE_PKG = "override_pkg"
        private const val KEY_OVERRIDE_UNTIL = "override_until"
    }
}
