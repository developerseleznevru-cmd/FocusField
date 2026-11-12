package ru.seleznev.focusfield.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * REST API для синхронизации данных (требование ТЗ: обмен данными через REST).
 * Базовый URL задаётся в настройках приложения.
 */
interface SyncApi {

    /** Отправляет сессии концентрации за последние 7 дней. */
    @POST("sessions")
    suspend fun pushSessions(@Body payload: SessionsPayload): Response<Unit>

    /** Получает задачи из внешнего API. */
    @GET("tasks")
    suspend fun fetchTasks(): Response<List<TaskDto>>
}

data class SessionsPayload(val sessions: List<SessionDto>)

data class SessionDto(
    val id: Long?,
    val startedAt: Long,
    val endedAt: Long?,
    val avgScore: Int?,
    val interruptions: Int?
)

data class TaskDto(
    val id: Long?,
    val title: String,
    val description: String?,
    val dueAt: Long?,
    val remindAt: Long?,
    val isDone: Boolean?,
    val priority: Int?
)
