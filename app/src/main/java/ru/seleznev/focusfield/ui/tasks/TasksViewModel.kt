package ru.seleznev.focusfield.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.Task
import ru.seleznev.focusfield.data.repo.RemoteSyncRepository
import ru.seleznev.focusfield.data.repo.SettingsRepository
import ru.seleznev.focusfield.data.repo.TaskRepository
import ru.seleznev.focusfield.data.repo.SyncOutcome
import ru.seleznev.focusfield.service.reminder.ReminderScheduler

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val remoteSyncRepository: RemoteSyncRepository
) : ViewModel() {

    val tasks: LiveData<List<Task>> = taskRepository.observeTasks().asLiveData()

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    private val _syncing = MutableLiveData(false)
    val syncing: LiveData<Boolean> = _syncing

    fun addTask(
        title: String,
        description: String,
        dueAt: Long?,
        remindAt: Long?,
        priority: Int
    ) {
        viewModelScope.launch {
            val id = taskRepository.add(
                title = title,
                description = description,
                createdAt = System.currentTimeMillis(),
                dueAt = dueAt,
                remindAt = remindAt
            )
            if (remindAt != null && settingsRepository.state.value.notificationsEnabled) {
                reminderScheduler.schedule(id, title, remindAt)
            }
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleDone(task)
            if (task.isDone) {
                // Задача снова стала активной — планируем напоминание, если задано
                task.remindAt?.let { reminderScheduler.schedule(task.id, task.title, it) }
            } else {
                reminderScheduler.cancel(task.id)
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            reminderScheduler.cancel(task.id)
            taskRepository.delete(task)
        }
    }

    /** Загружает задачи из внешнего REST API и синхронизирует сессии. */
    fun importFromApi() {
        viewModelScope.launch {
            _syncing.value = true
            val outcome = remoteSyncRepository.syncNow()
            _message.value = when (outcome) {
                SyncOutcome.Disabled -> "Синхронизация отключена в настройках"
                SyncOutcome.Success -> "Данные синхронизированы"
                is SyncOutcome.Error -> "Ошибка: ${outcome.message}"
            }
            _syncing.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
