package com.nct32.notesplus.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nct32.notesplus.data.Reminder
import com.nct32.notesplus.data.ReminderRepository

/** What to do with a reminder's alarm: set a new one, or cancel any existing one. */
enum class AlarmDecision { SCHEDULE, CANCEL }

/**
 * Pure decision function for [ReminderAlarmScheduler.schedule] (JVM-testable, no Android
 * dependencies):
 *
 * - `completed` reminder → [AlarmDecision.CANCEL]
 * - no due time (`dueAt == null`) → [AlarmDecision.CANCEL]
 * - due time already reached or passed (`dueAt <= now`) → [AlarmDecision.CANCEL]
 * - due time in the future → [AlarmDecision.SCHEDULE]
 */
fun alarmDecisionFor(reminder: Reminder, now: Long): AlarmDecision {
    if (reminder.completed) return AlarmDecision.CANCEL
    val dueAt = reminder.dueAt ?: return AlarmDecision.CANCEL
    return if (dueAt > now) AlarmDecision.SCHEDULE else AlarmDecision.CANCEL
}

/**
 * Schedules and cancels [AlarmManager] alarms for reminder due times.
 *
 * Process-wide singleton, initialized in `MainActivity.onCreate` (and defensively in the
 * boot / alarm receivers) — the same pattern as `AppDatabase.init` / `AppSettings.init`.
 * Before [init] (e.g. in plain JVM unit tests) every operation is a safe no-op, which is what
 * keeps [com.nct32.notesplus.ui.RemindersViewModel] unit-testable without an Android context.
 *
 * Exact vs. inexact: [schedule] uses [AlarmManager.setExactAndAllowWhileIdle] when exact
 * alarms are allowed (API < 31 always; API 31+ only when the user has granted the
 * `SCHEDULE_EXACT_ALARM` permission). On API 31+ without the permission it falls back to
 * [AlarmManager.setAndAllowWhileIdle] (inexact) so reminders still fire — possibly a little
 * late — without the special permission.
 */
object ReminderAlarmScheduler {

    private var context: Context? = null

    /** Binds the scheduler to the application context. Idempotent. */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Schedules (or cancels) the alarm for [reminder] according to [alarmDecisionFor]:
     * future due time → alarm at `dueAt`; no due time / completed / already due → cancel.
     */
    fun schedule(reminder: Reminder, now: Long = System.currentTimeMillis()) {
        val ctx = context ?: return
        when (alarmDecisionFor(reminder, now)) {
            AlarmDecision.SCHEDULE -> setAlarm(ctx, reminder)
            AlarmDecision.CANCEL -> cancel(reminder.id)
        }
    }

    /** Cancels the pending alarm for the reminder with [id] (no-op if none is pending). */
    fun cancel(id: String) {
        val ctx = context ?: return
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(alarmPendingIntent(ctx, id))
    }

    /**
     * Re-schedules the alarm for every reminder in the database. Called on
     * `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` (see [ReminderBootReceiver]) so due-time
     * alarms survive a device reboot or an app update.
     */
    suspend fun rescheduleAll() {
        val ctx = context ?: return
        ReminderRepository.instance.snapshot().forEach { schedule(it) }
    }

    private fun setAlarm(ctx: Context, reminder: Reminder) {
        val dueAt = reminder.dueAt ?: return
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent(ctx, reminder.id)
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            // No exact-alarm permission (API 31+): fall back to an inexact alarm so the
            // reminder still fires without the special permission.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
        }
    }

    /**
     * The [PendingIntent] delivered to [ReminderAlarmReceiver] when the alarm fires.
     *
     * The stable `requestCode` (derived from the reminder id) plus `FLAG_UPDATE_CURRENT`
     * guarantee that [schedule] and [cancel] for the same reminder always operate on the
     * *same* pending intent.
     */
    internal fun alarmPendingIntent(ctx: Context, reminderId: String): PendingIntent {
        val intent = Intent(ctx, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_DUE
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            ctx,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ACTION_REMINDER_DUE = "com.nct32.notesplus.action.REMINDER_DUE"
}
