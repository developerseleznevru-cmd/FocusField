package ru.seleznev.focusfield.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.seleznev.focusfield.data.db.dao.FocusSessionDao
import ru.seleznev.focusfield.data.db.entity.FocusSampleEntity
import ru.seleznev.focusfield.data.db.entity.FocusSessionEntity
import ru.seleznev.focusfield.data.model.FocusSample
import ru.seleznev.focusfield.data.model.FocusSession

/** Репозиторий сессий концентрации и точек измерения. */
class FocusRepository(private val dao: FocusSessionDao) {

    fun observeSessions(): Flow<List<FocusSession>> =
        dao.observeSessions().map { list -> list.map(FocusSession::fromEntity) }

    /** Создаёт сессию и возвращает её id. */
    suspend fun startSession(taskId: Long, startedAt: Long): Long =
        dao.insertSession(
            FocusSessionEntity(startedAt = startedAt, taskId = taskId)
        )

    /** Завершает сессию, записывая агрегированные метрики. */
    suspend fun finishSession(
        sessionId: Long,
        endedAt: Long,
        avgScore: Int,
        minScore: Int,
        maxScore: Int,
        interruptions: Int,
        blinks: Int
    ) {
        val session = dao.sessionById(sessionId) ?: return
        dao.updateSession(
            session.copy(
                endedAt = endedAt,
                avgScore = avgScore,
                minScore = minScore,
                maxScore = maxScore,
                interruptions = interruptions,
                blinks = blinks
            )
        )
    }

    /** Сохраняет накопленные точки измерения. */
    suspend fun addSamples(sessionId: Long, samples: List<Pair<Long, Int>>) {
        if (samples.isEmpty()) return
        dao.insertSamples(
            samples.map { (timestamp, score) ->
                FocusSampleEntity(sessionId = sessionId, timestamp = timestamp, score = score)
            }
        )
    }

    suspend fun sessionsSince(from: Long): List<FocusSession> =
        dao.sessionsSince(from).map(FocusSession::fromEntity)

    suspend fun samplesSince(from: Long): List<FocusSample> =
        dao.samplesSince(from).map(FocusSample::fromEntity)
}
