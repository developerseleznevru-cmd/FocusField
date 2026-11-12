package ru.seleznev.focusfield.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Точка измерения уровня концентрации внутри сессии. */
@Entity(
    tableName = "focus_samples",
    indices = [Index("sessionId")]
)
data class FocusSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    /** 0..100, либо -1 если биометрия недоступна. */
    val score: Int
)
