package com.nct32.notesplus.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nct32.notesplus.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-schedules all reminder alarms after a device reboot (`BOOT_COMPLETED`) or an app
 * update (`MY_PACKAGE_REPLACED`), so due-time notifications survive a restart.
 *
 * AlarmManager alarms do not survive a reboot, so this is the mechanism that restores them:
 * every reminder in the database is re-scheduled (past/completed/dateless ones are simply
 * cancelled by the scheduler's decision logic).
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // Fresh process: make sure the DB and the scheduler are initialized (idempotent).
        AppDatabase.init(appContext)
        ReminderAlarmScheduler.init(appContext)
        CoroutineScope(Dispatchers.IO).launch {
            ReminderAlarmScheduler.rescheduleAll()
        }
    }
}
