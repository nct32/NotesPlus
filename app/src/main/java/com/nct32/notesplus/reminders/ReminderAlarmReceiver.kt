package com.nct32.notesplus.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nct32.notesplus.data.ReminderRepository
import com.nct32.notesplus.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the alarm broadcast when a reminder is due and posts the notification.
 *
 * The process may be fresh (e.g. the device rebooted and the alarm fired right after), so
 * [AppDatabase] and [ReminderAlarmScheduler] are re-initialized first — both are idempotent
 * no-ops when already initialized.
 *
 * The notification is posted only if the reminder still exists, is **not** completed, has a
 * due time, and is due (`dueAt <= now + [TOLERANCE_MS]`, a small tolerance for clock/alarm
 * jitter).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        AppDatabase.init(appContext)
        ReminderAlarmScheduler.init(appContext)

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val reminder = ReminderRepository.instance.getById(reminderId) ?: return@launch
            val dueAt = reminder.dueAt ?: return@launch
            if (reminder.completed) return@launch
            if (dueAt > System.currentTimeMillis() + TOLERANCE_MS) return@launch
            ReminderNotifications.postReminderDueNotification(appContext, reminder)
        }
    }

    companion object {
        /** Intent extra carrying the id of the reminder that is due. */
        const val EXTRA_REMINDER_ID = "com.nct32.notesplus.extra.REMINDER_ID"

        /**
         * "Is it due yet?" tolerance: the notification is posted when
         * `dueAt <= now + TOLERANCE_MS`. Covers minor clock/alarm jitter.
         */
        const val TOLERANCE_MS = 60_000L
    }
}
