package ru.seleznev.focusfield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Задача пользователя (управление задачами и напоминаниями).
 *
 * @property priority 0 — низкий, 1 — обычный, 2 — высокий.
 * @property remindAt время напоминания (миллисекунды) или null.
 * @property dueAt срок выполнения или null.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val createdAt: Long,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val isDone: Boolean = false,
    val priority: Int = 1
)
