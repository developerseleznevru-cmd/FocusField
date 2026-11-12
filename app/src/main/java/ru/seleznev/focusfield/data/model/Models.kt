package ru.seleznev.focusfield.data.model

import ru.seleznev.focusfield.data.db.entity.BlockedItemEntity
import ru.seleznev.focusfield.data.db.entity.FocusSampleEntity
import ru.seleznev.focusfield.data.db.entity.FocusSessionEntity
import ru.seleznev.focusfield.data.db.entity.TaskEntity

/** Задача пользователя. */
data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val createdAt: Long,
    val dueAt: Long?,
    val remindAt: Long?,
    val isDone: Boolean,
    val priority: Int
) {
    fun toEntity() = TaskEntity(
        id = id, title = title, description = description, createdAt = createdAt,
        dueAt = dueAt, remindAt = remindAt, isDone = isDone, priority = priority
    )

    companion object {
        fun fromEntity(e: TaskEntity) = Task(
            id = e.id, title = e.title, description = e.description, createdAt = e.createdAt,
            dueAt = e.dueAt, remindAt = e.remindAt, isDone = e.isDone, priority = e.priority
        )
    }
}

/** Элемент списка блокировки. */
data class BlockedItem(
    val id: Long,
    val type: String,
    val packageName: String?,
    val domain: String?,
    val label: String,
    val enabled: Boolean,
    val addedAt: Long
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_SITE = "SITE"

        fun fromEntity(e: BlockedItemEntity) = BlockedItem(
            id = e.id, type = e.type, packageName = e.packageName, domain = e.domain,
            label = e.label, enabled = e.enabled, addedAt = e.addedAt
        )
    }
}

/** Сессия концентрации. */
data class FocusSession(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val avgScore: Int,
    val minScore: Int,
    val maxScore: Int,
    val interruptions: Int,
    val taskId: Long,
    val blinks: Int
) {
    companion object {
        fun fromEntity(e: FocusSessionEntity) = FocusSession(
            id = e.id, startedAt = e.startedAt, endedAt = e.endedAt, avgScore = e.avgScore,
            minScore = e.minScore, maxScore = e.maxScore, interruptions = e.interruptions,
            taskId = e.taskId, blinks = e.blinks
        )
    }
}

/** Точка измерения концентрации. */
data class FocusSample(
    val sessionId: Long,
    val timestamp: Long,
    val score: Int
) {
    companion object {
        fun fromEntity(e: FocusSampleEntity) = FocusSample(
            sessionId = e.sessionId, timestamp = e.timestamp, score = e.score
        )
    }
}
