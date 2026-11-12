package ru.seleznev.focusfield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.service.block.ScheduleWindow

class ScheduleWindowTest {

    @Test
    fun `within normal interval`() {
        assertTrue(ScheduleWindow.isWithin(minutesOfDay = 12 * 60, startMinutes = 9 * 60, endMinutes = 18 * 60))
        assertFalse(ScheduleWindow.isWithin(minutesOfDay = 7 * 60, startMinutes = 9 * 60, endMinutes = 18 * 60))
    }

    @Test
    fun `interval crossing midnight`() {
        // 22:00 — 07:00
        assertTrue(ScheduleWindow.isWithin(minutesOfDay = 23 * 60, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertTrue(ScheduleWindow.isWithin(minutesOfDay = 3 * 60, startMinutes = 22 * 60, endMinutes = 7 * 60))
        assertFalse(ScheduleWindow.isWithin(minutesOfDay = 12 * 60, startMinutes = 22 * 60, endMinutes = 7 * 60))
    }

    @Test
    fun `boundaries are inclusive`() {
        assertTrue(ScheduleWindow.isWithin(minutesOfDay = 9 * 60, startMinutes = 9 * 60, endMinutes = 18 * 60))
        assertTrue(ScheduleWindow.isWithin(minutesOfDay = 18 * 60, startMinutes = 9 * 60, endMinutes = 18 * 60))
    }
}
