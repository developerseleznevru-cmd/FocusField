package ru.seleznev.focusfield.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Утилиты для работы с календарными днями.
 * Использует java.util.Calendar — работает на всех уровнях API без desugaring.
 */
object DayMath {

    /** Начало дня (00:00) в [zone] для момента времени [millis]. */
    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Сдвигает момент времени на [days] дней (24 часа). */
    fun addDays(millis: Long, days: Long): Long = millis + days * MILLIS_PER_DAY

    /** Минуты от полуночи для момента [millis] в зоне [zone]. */
    fun minutesOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Момент «сегодня в [minutes] минут от полуночи». */
    fun todayAt(minutes: Int, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = startOfDay(nowMillis, zone)
        cal.add(Calendar.MINUTE, minutes)
        return cal.timeInMillis
    }

    /** Дата момента [millis] в формате «dd.MM» (локальная зона). */
    fun formatDay(millis: Long): String =
        SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis))

    const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
}
