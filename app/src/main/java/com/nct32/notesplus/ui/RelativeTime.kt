package com.nct32.notesplus.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Returns a short, human-readable relative time string for [epochMillis] relative to [now]
 * (defaults to the current time).
 *
 * Examples: "just now", "5m ago", "3h ago", "2d ago", or a short date (e.g. "Jan 5") for anything
 * older than a week.
 */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMillis
    return when {
        diff < 60_000L -> "just now"
        diff < 60L * 60_000L -> "${diff / 60_000L}m ago"
        diff < 24L * 60L * 60_000L -> "${diff / (60L * 60_000L)}h ago"
        diff < 7L * 24L * 60L * 60_000L -> "${diff / (24L * 60L * 60_000L)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}
