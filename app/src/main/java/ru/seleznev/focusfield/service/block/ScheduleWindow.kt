package ru.seleznev.focusfield.service.block

/**
 * Окно расписания: проверяет, попадает ли текущее время суток
 * в заданный интервал (учитывает интервалы через полночь).
 * Чистая логика для юнит-тестов.
 */
object ScheduleWindow {

    fun isWithin(minutesOfDay: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val start = normalize(startMinutes)
        val end = normalize(endMinutes)
        val now = normalize(minutesOfDay)
        return if (start <= end) {
            now in start..end
        } else {
            // Интервал через полночь, например 22:00–07:00
            now >= start || now <= end
        }
    }

    private fun normalize(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440
}
