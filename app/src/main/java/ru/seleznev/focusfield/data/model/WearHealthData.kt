package ru.seleznev.focusfield.data.model

/** Данные о здоровье с Wear OS устройства (пульс, активность). */
data class WearHealthData(
    val heartRate: Int,
    val steps: Int,
    val timestamp: Long
)
