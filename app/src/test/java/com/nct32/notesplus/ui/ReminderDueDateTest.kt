package com.nct32.notesplus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for the pure-Kotlin [formatDueDate] and [isOverdue] helpers (JVM-safe).
 */
class ReminderDueDateTest {

    private val zone = ZoneId.systemDefault()

    private fun at(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun same_day_formats_as_today() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        val due = at(LocalDate.of(2026, 5, 15), LocalTime.of(17, 0))
        val result = formatDueDate(due, now)
        assertTrue("Expected 'Today ...' but was '$result'", result.startsWith("Today"))
    }

    @Test
    fun next_day_formats_as_tomorrow() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        val due = at(LocalDate.of(2026, 5, 16), LocalTime.of(9, 0))
        val result = formatDueDate(due, now)
        assertTrue("Expected 'Tomorrow ...' but was '$result'", result.startsWith("Tomorrow"))
    }

    @Test
    fun other_day_includes_medium_date() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        val due = at(LocalDate.of(2026, 6, 1), LocalTime.of(17, 0))
        val result = formatDueDate(due, now)
        // The medium localized date for 1 June 2026 in the default locale contains "June" or "6".
        assertTrue("Expected a medium date but was '$result'", result.contains("2026") || result.contains("June"))
    }

    @Test
    fun isOverdue_true_when_due_in_past() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        val past = at(LocalDate.of(2026, 5, 15), LocalTime.of(11, 0))
        assertTrue(isOverdue(past, now))
    }

    @Test
    fun isOverdue_false_when_due_in_future() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        val future = at(LocalDate.of(2026, 5, 15), LocalTime.of(13, 0))
        assertFalse(isOverdue(future, now))
    }

    @Test
    fun isOverdue_false_when_due_exactly_now() {
        val now = at(LocalDate.of(2026, 5, 15), LocalTime.of(12, 0))
        assertFalse(isOverdue(now, now))
    }

    @Test
    fun formatDueDate_uses_injected_now_not_wall_clock() {
        // A fixed "now" far in the future should still produce a deterministic "Today" label
        // for a due time on the same calendar day, proving `now` is honored (not wall clock).
        val now = at(LocalDate.of(2030, 1, 1), LocalTime.of(8, 0))
        val due = at(LocalDate.of(2030, 1, 1), LocalTime.of(9, 0))
        assertEquals(true, formatDueDate(due, now).startsWith("Today"))
    }
}
