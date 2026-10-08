package com.nct32.notesplus.ui

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Formats a reminder's due time [epochMillis] into a short, human-readable string relative to
 * [now] (defaults to the current time):
 *
 * - Same calendar day: "Today 5:00 PM"
 * - Next calendar day: "Tomorrow 9:00 AM"
 * - Otherwise: "Jan 5, 2026, 5:00 PM"
 *
 * The time is formatted in the user's locale (12-hour where the locale uses it).
 */
fun formatDueDate(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val dueDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    val timeText = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    return when (ChronoUnit.DAYS.between(nowDate, dueDate)) {
        0L -> "Today $timeText"
        1L -> "Tomorrow $timeText"
        else -> dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + ", $timeText"
    }
}

/**
 * Returns `true` if the reminder's due time [epochMillis] is in the past relative to [now]
 * (defaults to the current time).
 */
fun isOverdue(epochMillis: Long, now: Long = System.currentTimeMillis()): Boolean =
    epochMillis < now
