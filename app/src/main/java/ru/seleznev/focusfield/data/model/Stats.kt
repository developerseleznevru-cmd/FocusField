package ru.seleznev.focusfield.data.model

import ru.seleznev.focusfield.util.DayMath

/** Диапазон концентрации для статистики и блокировки. */
enum class FocusBand {
    HIGH,
    MEDIUM,
    LOW,
    NO_DATA;

    companion object {
        fun fromScore(score: Int): FocusBand = when {
            score < 0 -> NO_DATA
            score >= HIGH_THRESHOLD -> HIGH
            score >= MEDIUM_THRESHOLD -> MEDIUM
            else -> LOW
        }

        const val HIGH_THRESHOLD = 75
        const val MEDIUM_THRESHOLD = 50
    }
}

/** Агрегированная статистика за день. */
data class DayAverage(
    /** Начало дня (00:00). */
    val dayStart: Long,
    /** Средняя концентрация за день или -1, если данных нет. */
    val average: Int,
    val sessions: Int
)

/** Итоговый отчёт по периоду. */
data class FocusSummary(
    val sessions: Int,
    val totalMinutes: Long,
    val avgScore: Int,
    val interruptions: Int
)

/**
 * Чистая агрегация статистики (используется StatsViewModel, покрывается юнит-тестами).
 */
object StatsAggregator {

    /** Средняя концентрация по каждому из последних [days] дней. */
    fun dailyAverages(
        sessions: List<FocusSession>,
        days: Int,
        now: Long,
        zone: java.util.TimeZone
    ): List<DayAverage> {
        val todayStart = DayMath.startOfDay(now, zone)
        return (days - 1 downTo 0).map { offset ->
            val dayStart = todayStart - offset * MILLIS_PER_DAY
            val dayEnd = dayStart + MILLIS_PER_DAY
            val inDay = sessions.filter {
                it.startedAt in dayStart until dayEnd && it.endedAt > 0 && it.avgScore >= 0
            }
            val avg = inDay.map { it.avgScore }
                .average()
                .takeUnless { it.isNaN() }
                ?.toInt()
                ?: -1
            DayAverage(dayStart = dayStart, average = avg, sessions = inDay.size)
        }
    }

    /** Распределение точек измерения по диапазонам концентрации. */
    fun distribution(samples: List<FocusSample>): Map<FocusBand, Int> {
        val result = FocusBand.entries.associateWith { 0 }.toMutableMap()
        for (sample in samples) {
            result[FocusBand.fromScore(sample.score)] = result.getValue(FocusBand.fromScore(sample.score)) + 1
        }
        return result
    }

    /** Сводка по сессиям за период. */
    fun summary(sessions: List<FocusSession>): FocusSummary {
        val ended = sessions.filter { it.endedAt > 0 }
        val scores = ended.map { it.avgScore }.filter { it >= 0 }
        return FocusSummary(
            sessions = ended.size,
            totalMinutes = ended.sumOf { (it.endedAt - it.startedAt) / 60_000 },
            avgScore = if (scores.isEmpty()) -1 else scores.average().toInt(),
            interruptions = ended.sumOf { it.interruptions }
        )
    }

    const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
}
