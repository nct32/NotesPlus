package com.nct32.notesplus.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nct32.notesplus.MainActivity
import com.nct32.notesplus.R
import com.nct32.notesplus.data.Reminder
import com.nct32.notesplus.ui.formatDueDate

/**
 * Notification plumbing for due-time reminders: the "Reminders" channel and the notification
 * itself.
 *
 * [init] must be called once with an Android context (done in `MainActivity.onCreate` and
 * defensively in [ReminderAlarmReceiver]); it is idempotent — re-creating a channel with the
 * same id is a no-op/update on the platform side.
 */
object ReminderNotifications {

    /** Id of the "Reminders" notification channel. */
    const val CHANNEL_ID = "reminders"

    /**
     * Creates the "Reminders" channel with [NotificationManager.IMPORTANCE_HIGH] so the
     * notification makes a sound and shows in the status bar. Idempotent.
     */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a reminder is due"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Posts the due-time notification for [reminder]:
     *
     * - **title** = the reminder's title
     * - **text**  = the reminder's due time, formatted like the reminder list
     *   (e.g. "Today 5:00 PM", "Tomorrow 9:00 AM", "Jan 5, 2026, 5:00 PM")
     *
     * Tapping the notification opens the app; it auto-cancels when dismissed. On Android 13+
     * this is a silent no-op unless `POST_NOTIFICATIONS` is granted (no crash).
     */
    fun postReminderDueNotification(context: Context, reminder: Reminder) {
        init(context)
        val dueAt = reminder.dueAt ?: return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(reminder.title)
            .setContentText(formatDueDate(dueAt))
            .setWhen(dueAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Stable per-reminder id so a re-fired alarm replaces (not duplicates) the notification.
        notificationManager.notify(reminder.id.hashCode(), notification)
    }
}
