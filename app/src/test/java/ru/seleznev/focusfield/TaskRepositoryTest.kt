package ru.seleznev.focusfield

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.data.db.dao.TaskDao
import ru.seleznev.focusfield.data.db.entity.TaskEntity
import ru.seleznev.focusfield.data.repo.TaskRepository

class TaskRepositoryTest {

    /** Простая in-memory реализация DAO для юнит-тестов. */
    private class FakeTaskDao : TaskDao {
        private val tasks = mutableListOf<TaskEntity>()
        private var nextId = 1L

        override fun observeAll() = kotlinx.coroutines.flow.flow {
            emit(sorted())
        }

        private fun sorted() = tasks.sortedWith(
            compareBy({ it.isDone }, { -it.priority }, { -it.createdAt })
        )

        override suspend fun byId(id: Long): TaskEntity? = tasks.find { it.id == id }

        override suspend fun tasksWithFutureReminders(now: Long) =
            tasks.filter { !it.isDone && it.remindAt != null && it.remindAt!! > now }

        override suspend fun all(): List<TaskEntity> = tasks.toList()

        override suspend fun insert(task: TaskEntity): Long {
            val entity = task.copy(id = nextId++)
            tasks.add(entity)
            return entity.id
        }

        override suspend fun update(task: TaskEntity) {
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index >= 0) tasks[index] = task
        }

        override suspend fun delete(task: TaskEntity) {
            tasks.removeAll { it.id == task.id }
        }

        override suspend fun setDone(id: Long, done: Boolean) {
            val task = byId(id) ?: return
            update(task.copy(isDone = done))
        }
    }

    @Test
    fun `add task and observe it`() = runTest {
        val repository = TaskRepository(FakeTaskDao())
        repository.add(title = "Написать отчёт", description = "", createdAt = 1000)
        val tasks = repository.observeTasks().first()
        assertEquals(1, tasks.size)
        assertEquals("Написать отчёт", tasks[0].title)
    }

    @Test
    fun `toggle done flips state`() = runTest {
        val repository = TaskRepository(FakeTaskDao())
        val id = repository.add(title = "Задача", description = "", createdAt = 1000)
        var task = repository.task(id)!!
        repository.toggleDone(task)
        task = repository.task(id)!!
        assertTrue(task.isDone)
        repository.toggleDone(task)
        assertFalse(repository.task(id)!!.isDone)
    }

    @Test
    fun `delete removes task`() = runTest {
        val repository = TaskRepository(FakeTaskDao())
        val id = repository.add(title = "Задача", description = "", createdAt = 1000)
        repository.delete(repository.task(id)!!)
        assertEquals(0, repository.observeTasks().first().size)
    }

    @Test
    fun `future reminders excludes done and past`() = runTest {
        val repository = TaskRepository(FakeTaskDao())
        repository.add(title = "Активная", description = "", createdAt = 1000, remindAt = 2000)
        val doneId = repository.add(title = "Выполненная", description = "", createdAt = 1000, remindAt = 2000)
        repository.toggleDone(repository.task(doneId)!!)
        repository.add(title = "Прошедшая", description = "", createdAt = 1000, remindAt = 500)
        val reminders = repository.tasksWithFutureReminders(now = 1500)
        assertEquals(1, reminders.size)
        assertEquals("Активная", reminders[0].title)
    }

    @Test
    fun `upsert remote skips duplicates by title`() = runTest {
        val repository = TaskRepository(FakeTaskDao())
        assertTrue(repository.upsertRemote("Из API", "", null))
        assertFalse(repository.upsertRemote("Из API", "", null))
        assertEquals(1, repository.observeTasks().first().size)
    }
}
