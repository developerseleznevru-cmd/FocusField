package ru.seleznev.focusfield.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Срабатывает по будильнику напоминания и показывает уведомление. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (taskId <= 0) return
        NotificationHelper.showReminder(context, taskId, title)
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
    }
}
