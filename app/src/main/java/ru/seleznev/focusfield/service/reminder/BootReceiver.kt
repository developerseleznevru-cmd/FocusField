package ru.seleznev.focusfield.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.FocusFieldApp

/** После перезагрузки устройства переустанавливает будильники напоминаний. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        val app = context.applicationContext as FocusFieldApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val tasks = app.container.taskRepository.tasksWithFutureReminders(
                    System.currentTimeMillis()
                )
                app.container.reminderScheduler.scheduleAll(tasks)
            } finally {
                result.finish()
            }
        }
    }
}
