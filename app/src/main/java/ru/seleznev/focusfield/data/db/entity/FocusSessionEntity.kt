package ru.seleznev.focusfield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сессия концентрации: период непрерывного мониторинга внимания.
 * Создаётся при запуске сессии в FocusViewModel и обновляется при завершении.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** 0, пока сессия активна. */
    val endedAt: Long = 0,
    /** Средний уровень концентрации 0..100; -1 — нет биометрических данных. */
    val avgScore: Int = -1,
    val minScore: Int = -1,
    val maxScore: Int = -1,
    /** Количество «помех»: показов оверлея блокировки за сессию. */
    val interruptions: Int = 0,
    /** Связанная задача или 0. */
    val taskId: Long = 0,
    /** Количество зафиксированных морганий за сессию. */
    val blinks: Int = 0
)
