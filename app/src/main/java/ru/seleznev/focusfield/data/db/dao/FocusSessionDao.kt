package ru.seleznev.focusfield.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.seleznev.focusfield.data.db.entity.FocusSampleEntity
import ru.seleznev.focusfield.data.db.entity.FocusSessionEntity

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt ASC")
    suspend fun sessionsSince(from: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun sessionById(id: Long): FocusSessionEntity?

    @Insert
    suspend fun insertSamples(samples: List<FocusSampleEntity>)

    @Query(
        "SELECT samples.* FROM focus_samples AS samples " +
            "INNER JOIN focus_sessions AS sessions ON samples.sessionId = sessions.id " +
            "WHERE sessions.startedAt >= :from ORDER BY samples.timestamp ASC"
    )
    suspend fun samplesSince(from: Long): List<FocusSampleEntity>

    @Query("SELECT * FROM focus_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun samplesForSession(sessionId: Long): List<FocusSampleEntity>
}
