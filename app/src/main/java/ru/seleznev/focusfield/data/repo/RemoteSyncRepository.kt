package ru.seleznev.focusfield.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.seleznev.focusfield.data.model.Task
import ru.seleznev.focusfield.data.remote.SessionDto
import ru.seleznev.focusfield.data.remote.SessionsPayload
import ru.seleznev.focusfield.data.remote.SyncApi
import ru.seleznev.focusfield.data.remote.TaskDto
import ru.seleznev.focusfield.util.DayMath
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Итог синхронизации. */
sealed interface SyncOutcome {
    data object Disabled : SyncOutcome
    data object Success : SyncOutcome
    data class Error(val message: String) : SyncOutcome
}

/**
 * Синхронизация с внешним REST API: отправка сессий концентрации
 * и загрузка задач из внешнего сервиса.
 */
class RemoteSyncRepository(
    private val settingsRepository: SettingsRepository,
    private val focusRepository: FocusRepository,
    private val taskRepository: TaskRepository
) {

    suspend fun syncNow(): SyncOutcome = withContext(Dispatchers.IO) {
        val settings = settingsRepository.state.value
        if (!settings.syncEnabled || settings.apiBaseUrl.isBlank()) return@withContext SyncOutcome.Disabled
        val baseUrl = normalizeBaseUrl(settings.apiBaseUrl)
        if (baseUrl == null) {
            return@withContext SyncOutcome.Error("Некорректный базовый URL API")
        }
        try {
            val api = buildApi(baseUrl)
            val weekAgo = System.currentTimeMillis() - 7 * DayMath.MILLIS_PER_DAY
            val sessions = focusRepository.sessionsSince(weekAgo).map { session ->
                SessionDto(
                    id = session.id,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt.takeIf { it > 0 },
                    avgScore = session.avgScore.takeIf { it >= 0 },
                    interruptions = session.interruptions
                )
            }
            val pushResponse = api.pushSessions(SessionsPayload(sessions))
            if (!pushResponse.isSuccessful) {
                return@withContext SyncOutcome.Error("Сервер ответил кодом ${pushResponse.code()}")
            }

            val tasksResponse = api.fetchTasks()
            if (tasksResponse.isSuccessful) {
                tasksResponse.body().orEmpty().forEach { dto ->
                    taskRepository.upsertRemote(
                        title = dto.title,
                        description = dto.description.orEmpty(),
                        dueAt = dto.dueAt
                    )
                }
            }
            SyncOutcome.Success
        } catch (e: IOException) {
            SyncOutcome.Error("Сеть недоступна: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            SyncOutcome.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun fetchRemoteTasksPreview(): List<Task> {
        val settings = settingsRepository.state.value
        val baseUrl = normalizeBaseUrl(settings.apiBaseUrl) ?: return emptyList()
        val response = buildApi(baseUrl).fetchTasks()
        return response.body().orEmpty().map { dto ->
            Task(
                id = dto.id ?: 0,
                title = dto.title,
                description = dto.description.orEmpty(),
                createdAt = System.currentTimeMillis(),
                dueAt = dto.dueAt,
                remindAt = dto.remindAt,
                isDone = dto.isDone ?: false,
                priority = dto.priority ?: 1
            )
        }
    }

    private fun buildApi(baseUrl: String): SyncApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncApi::class.java)

    private fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
