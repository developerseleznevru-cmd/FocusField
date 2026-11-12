package ru.seleznev.focusfield

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.seleznev.focusfield.data.db.FocusFieldDatabase
import ru.seleznev.focusfield.data.prefs.SecurePrefs
import ru.seleznev.focusfield.data.repo.AuthRepository
import ru.seleznev.focusfield.data.repo.BlockRepository
import ru.seleznev.focusfield.data.repo.FocusRepository
import ru.seleznev.focusfield.data.repo.RemoteSyncRepository
import ru.seleznev.focusfield.data.repo.SettingsRepository
import ru.seleznev.focusfield.data.repo.TaskRepository
import ru.seleznev.focusfield.data.state.BlockingStateStore
import ru.seleznev.focusfield.service.reminder.NotificationHelper
import ru.seleznev.focusfield.service.reminder.ReminderScheduler
import ru.seleznev.focusfield.service.sync.SyncWorker
import ru.seleznev.focusfield.service.wear.WearHealthStore
import ru.seleznev.focusfield.ui.auth.AuthViewModel
import ru.seleznev.focusfield.ui.blocked.BlockedItemsViewModel
import ru.seleznev.focusfield.ui.focus.FocusViewModel
import ru.seleznev.focusfield.ui.settings.SettingsViewModel
import ru.seleznev.focusfield.ui.stats.StatsViewModel
import ru.seleznev.focusfield.ui.tasks.TasksViewModel

/**
 * Вручную собранный DI-контейнер (без внешних библиотек):
 * репозитории, база данных, фабрика ViewModel.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: FocusFieldDatabase by lazy { FocusFieldDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(SecurePrefs.settings(appContext))
    }

    val authRepository: AuthRepository by lazy { AuthRepository(appContext) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val focusRepository: FocusRepository by lazy { FocusRepository(database.focusSessionDao()) }

    val blockRepository: BlockRepository by lazy { BlockRepository(database.blockedItemDao()) }

    val blockingStateStore: BlockingStateStore by lazy { BlockingStateStore.get(appContext) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val wearHealthStore: WearHealthStore by lazy { WearHealthStore() }

    val remoteSyncRepository: RemoteSyncRepository by lazy {
        RemoteSyncRepository(settingsRepository, focusRepository, taskRepository)
    }

    /** Единая фабрика ViewModel для всего приложения. */
    val viewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer { AuthViewModel(authRepository) }
            initializer {
                FocusViewModel(
                    focusRepository = focusRepository,
                    taskRepository = taskRepository,
                    blockingStateStore = blockingStateStore,
                    settingsRepository = settingsRepository,
                    wearHealthStore = wearHealthStore
                )
            }
            initializer {
                TasksViewModel(
                    taskRepository = taskRepository,
                    reminderScheduler = reminderScheduler,
                    settingsRepository = settingsRepository,
                    remoteSyncRepository = remoteSyncRepository
                )
            }
            initializer { StatsViewModel(focusRepository) }
            initializer { BlockedItemsViewModel(blockRepository) }
            initializer { SettingsViewModel(settingsRepository) }
        }
    }
}

class FocusFieldApp : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Применяем сохранённую тему до отрисовки первого экрана
        AppCompatDelegate.setDefaultNightMode(
            themeOf(container.settingsRepository.state.value.theme)
        )
        NotificationHelper.createChannels(this)
        SyncWorker.ensureScheduled(this, container.settingsRepository.state.value.syncEnabled)
    }

    companion object {
        fun themeOf(themeCode: Int): Int = when (themeCode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
