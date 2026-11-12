package ru.seleznev.focusfield

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.seleznev.focusfield.data.model.FocusBand
import ru.seleznev.focusfield.data.model.FocusSample
import ru.seleznev.focusfield.data.model.FocusSession
import ru.seleznev.focusfield.data.model.StatsAggregator
import ru.seleznev.focusfield.util.DayMath
import java.util.TimeZone

class StatsAggregatorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `distribution counts bands`() {
        val samples = listOf(
            FocusSample(1, 0, 80),
            FocusSample(1, 1, 60),
            FocusSample(1, 2, 30),
            FocusSample(1, 3, -1)
        )
        val distribution = StatsAggregator.distribution(samples)
        assertEquals(1, distribution[FocusBand.HIGH])
        assertEquals(1, distribution[FocusBand.MEDIUM])
        assertEquals(1, distribution[FocusBand.LOW])
        assertEquals(1, distribution[FocusBand.NO_DATA])
    }

    @Test
    fun `summary aggregates ended sessions`() {
        val sessions = listOf(
            FocusSession(
                id = 1, startedAt = 0, endedAt = 10 * 60_000L, avgScore = 80,
                minScore = 60, maxScore = 95, interruptions = 1, taskId = 0, blinks = 3
            ),
            FocusSession(
                id = 2, startedAt = 0, endedAt = 20 * 60_000L, avgScore = 60,
                minScore = 40, maxScore = 90, interruptions = 0, taskId = 0, blinks = 0
            ),
            // Активная сессия не считается
            FocusSession(
                id = 3, startedAt = 0, endedAt = 0, avgScore = -1,
                minScore = -1, maxScore = -1, interruptions = 0, taskId = 0, blinks = 0
            )
        )
        val summary = StatsAggregator.summary(sessions)
        assertEquals(2, summary.sessions)
        assertEquals(30L, summary.totalMinutes)
        assertEquals(70, summary.avgScore)
        assertEquals(1, summary.interruptions)
    }

    @Test
    fun `daily averages covers gap days with -1`() {
        val now = 1_700_000_000_000L
        val todayStart = DayMath.startOfDay(now, utc)
        val yesterday = FocusSession(
            id = 1, startedAt = todayStart - DayMath.MILLIS_PER_DAY + 3_600_000L,
            endedAt = todayStart - DayMath.MILLIS_PER_DAY + 3_600_000L + 15 * 60_000L,
            avgScore = 70, minScore = 50, maxScore = 90, interruptions = 0, taskId = 0, blinks = 0
        )
        val result = StatsAggregator.dailyAverages(listOf(yesterday), days = 3, now = now, zone = utc)
        assertEquals(3, result.size)
        // самый старый день первым
        assertEquals(todayStart - 2 * DayMath.MILLIS_PER_DAY, result[0].dayStart)
        assertEquals(-1, result[0].average)
        assertEquals(70, result[1].average)
        assertEquals(1, result[1].sessions)
        assertEquals(-1, result[2].average)
    }
}
