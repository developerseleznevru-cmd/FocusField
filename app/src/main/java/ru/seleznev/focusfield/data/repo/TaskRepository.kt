package ru.seleznev.focusfield.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.seleznev.focusfield.data.db.dao.TaskDao
import ru.seleznev.focusfield.data.db.entity.TaskEntity
import ru.seleznev.focusfield.data.model.Task

/** Репозиторий задач: CRUD и выборки для планировщика напоминаний. */
class TaskRepository(private val dao: TaskDao) {

    fun observeTasks(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map(Task::fromEntity) }

    suspend fun task(id: Long): Task? = dao.byId(id)?.let(Task::fromEntity)

    suspend fun tasksWithFutureReminders(now: Long): List<Task> =
        dao.tasksWithFutureReminders(now).map(Task::fromEntity)

    suspend fun allTasks(): List<Task> = dao.all().map(Task::fromEntity)

    suspend fun add(
        title: String,
        description: String,
        createdAt: Long,
        dueAt: Long? = null,
        remindAt: Long? = null,
        priority: Int = 1
    ): Long = dao.insert(
        TaskEntity(
            title = title,
            description = description,
            createdAt = createdAt,
            dueAt = dueAt,
            remindAt = remindAt,
            priority = priority
        )
    )

    suspend fun toggleDone(task: Task) = dao.setDone(task.id, !task.isDone)

    suspend fun delete(task: Task) = dao.delete(task.toEntity())

    /** Обновляет напоминание задачи (после выбора нового времени). */
    suspend fun updateReminder(task: Task, remindAt: Long?) =
        dao.update(task.copy(remindAt = remindAt).toEntity())

    /** Сохраняет задачу, полученную из внешнего API (если ещё нет такой же по названию). */
    suspend fun upsertRemote(title: String, description: String, dueAt: Long?): Boolean {
        val exists = dao.all().any { it.title.equals(title, ignoreCase = true) }
        if (exists) return false
        add(title = title, description = description, createdAt = System.currentTimeMillis(), dueAt = dueAt)
        return true
    }
}
