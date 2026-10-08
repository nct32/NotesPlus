package com.nct32.notesplus.reminders

import com.nct32.notesplus.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure decision logic of [ReminderAlarmScheduler] (JVM-safe, no Android
 * `Context` / `AlarmManager` needed):
 *
 * - past due time → cancel (nothing to fire)
 * - no due time → cancel
 * - completed reminder → cancel
 * - future due time → schedule
 */
class ReminderAlarmSchedulerTest {

    private val now = 1_700_000_000_000L

    private fun reminder(
        dueAt: Long? = null,
        completed: Boolean = false
    ) = Reminder(
        id = "test-reminder",
        title = "Test",
        dueAt = dueAt,
        completed = completed,
        createdAt = now - 1_000L
    )

    @Test
    fun future_due_at_schedules_an_alarm() {
        val decision = alarmDecisionFor(reminder(dueAt = now + 3_600_000L), now)
        assertEquals(AlarmDecision.SCHEDULE, decision)
    }

    @Test
    fun past_due_at_cancels() {
        val decision = alarmDecisionFor(reminder(dueAt = now - 3_600_000L), now)
        assertEquals(AlarmDecision.CANCEL, decision)
    }

    @Test
    fun due_at_exactly_now_cancels() {
        // `dueAt <= now` is treated as "already due": the alarm receiver fires the
        // notification at/after the due time, so no alarm is needed.
        val decision = alarmDecisionFor(reminder(dueAt = now), now)
        assertEquals(AlarmDecision.CANCEL, decision)
    }

    @Test
    fun null_due_at_cancels() {
        val decision = alarmDecisionFor(reminder(dueAt = null), now)
        assertEquals(AlarmDecision.CANCEL, decision)
    }

    @Test
    fun completed_reminder_cancels_even_with_future_due_at() {
        val decision = alarmDecisionFor(
            reminder(dueAt = now + 3_600_000L, completed = true),
            now
        )
        assertEquals(AlarmDecision.CANCEL, decision)
    }

    @Test
    fun completed_reminder_without_due_at_cancels() {
        val decision = alarmDecisionFor(reminder(dueAt = null, completed = true), now)
        assertEquals(AlarmDecision.CANCEL, decision)
    }
}
