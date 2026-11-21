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

    @Test
    fun `streak counts consecutive days ending today`() {
        val now = 1_700_000_000_000L
        val todayStart = DayMath.startOfDay(now, utc)
        fun session(dayOffset: Long, id: Long) = FocusSession(
            id = id, startedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY + 3_600_000L,
            endedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY + 3_900_000L,
            avgScore = 70, minScore = 50, maxScore = 90, interruptions = 0, taskId = 0, blinks = 0
        )
        // Сегодня + вчера + позавчера, дальше — пропуск.
        val sessions = listOf(session(0, 1), session(1, 2), session(2, 3), session(4, 4))
        assertEquals(3, StatsAggregator.streakDays(sessions, now, utc))
    }

    @Test
    fun `streak counts from yesterday when today has no sessions`() {
        val now = 1_700_000_000_000L
        val todayStart = DayMath.startOfDay(now, utc)
        fun session(dayOffset: Long, id: Long) = FocusSession(
            id = id, startedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY + 3_600_000L,
            endedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY + 3_900_000L,
            avgScore = 70, minScore = 50, maxScore = 90, interruptions = 0, taskId = 0, blinks = 0
        )
        // Серия живёт, пока сегодня ещё нет сессий: считаем от вчера.
        assertEquals(2, StatsAggregator.streakDays(listOf(session(1, 1), session(2, 2)), now, utc))
        // Нет вчерашней и сегодняшней — серии нет.
        assertEquals(0, StatsAggregator.streakDays(listOf(session(3, 1)), now, utc))
        // Нет данных вообще.
        assertEquals(0, StatsAggregator.streakDays(emptyList(), now, utc))
    }

    @Test
    fun `best day picks day with max focus minutes`() {
        val todayStart = DayMath.startOfDay(1_700_000_000_000L, utc)
        fun session(dayOffset: Long, id: Long, minutes: Long) = FocusSession(
            id = id, startedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY,
            endedAt = todayStart - dayOffset * DayMath.MILLIS_PER_DAY + minutes * 60_000,
            avgScore = 70, minScore = 50, maxScore = 90, interruptions = 0, taskId = 0, blinks = 0
        )
        val sessions = listOf(
            session(0, 1, 25),
            session(1, 2, 40),
            session(1, 3, 20), // вчерашний суммарно 60 — лучший
            // Активная сессия не учитывается
            FocusSession(
                id = 4, startedAt = todayStart, endedAt = 0, avgScore = -1,
                minScore = -1, maxScore = -1, interruptions = 0, taskId = 0, blinks = 0
            )
        )
        val best = StatsAggregator.bestDay(sessions, utc)
        assertEquals(todayStart - DayMath.MILLIS_PER_DAY, best?.dayStart)
        assertEquals(60L, best?.totalMinutes)
        assertEquals(null, StatsAggregator.bestDay(emptyList(), utc))
    }
}
