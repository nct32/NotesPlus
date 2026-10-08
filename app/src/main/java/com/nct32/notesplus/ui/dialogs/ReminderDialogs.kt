package com.nct32.notesplus.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nct32.notesplus.data.Reminder
import com.nct32.notesplus.ui.formatDueDate
import com.nct32.notesplus.ui.theme.NotesTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Prompts the user to create a new reminder **or** edit an existing one.
 *
 * The user enters a title and may optionally pick a due date and time using the Material 3
 * [DatePicker] and [TimePicker] dialogs. The "No date" button clears any picked date/time.
 *
 * When [reminder] is `null` the dialog is in "create" mode: it starts empty, is titled "New
 * reminder", uses an [Icons.Rounded.Add] icon, and the confirm button reads "Add". When
 * [reminder] is provided the dialog is in "edit" mode: it is pre-filled with the existing
 * title and due date/time, is titled "Edit reminder", uses an [Icons.Rounded.Edit] icon, and
 * the confirm button reads "Save".
 *
 * [onSave] is invoked with the trimmed title and the combined due time (epoch millis) when the
 * user confirms. It is NOT invoked if the title is blank (the dialog stays open). The dialog
 * dismisses itself on confirm or cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, dueAt: Long?) -> Unit,
    reminder: Reminder? = null
) {
    val isEditing = reminder != null
    var title by remember(reminder) { mutableStateOf(reminder?.title ?: "") }
    var dueAt by remember(reminder) { mutableStateOf(reminder?.dueAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isEditing) Icons.Rounded.Edit else Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(if (isEditing) "Edit reminder" else "New reminder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Call the dentist") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = dueAt?.let { formatDueDate(it) } ?: "No date"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = dueAt?.let { formatDueDate(it) } ?: "No time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(trimmed, dueAt)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueAt
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueAt = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val initialTime = dueAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
        } ?: java.time.LocalTime.now(zone)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = false
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select time") },
            confirmButton = {
                TextButton(onClick = {
                    dueAt = combineDueAt(dueAt, timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/**
 * Combines an existing [dueAt] (or today, if none) with the picked [hour] and [minute] into a
 * single epoch-millis due time in the device's default time zone.
 */
private fun combineDueAt(
    dueAt: Long?,
    hour: Int,
    minute: Int
): Long {
    val zone = ZoneId.systemDefault()
    val baseDate = dueAt?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    } ?: java.time.LocalDate.now(zone)
    return baseDate.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

/**
 * Confirms deletion of a single reminder.
 *
 * [onConfirm] is invoked when the user confirms the deletion.
 */
@Composable
fun DeleteReminderDialog(
    reminderTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete reminder?") },
        text = {
            Text(
                text = "\"${reminderTitle.ifBlank { "Untitled" }}\" will be permanently deleted.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReminderDialogCreatePreview() {
    NotesTheme {
        ReminderDialog(onDismiss = {}, onSave = { _, _ -> })
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReminderDialogEditPreview() {
    NotesTheme {
        ReminderDialog(
            onDismiss = {},
            onSave = { _, _ -> },
            reminder = Reminder(
                id = "preview",
                title = "Call the dentist",
                dueAt = System.currentTimeMillis() + 3_600_000L,
                completed = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun DeleteReminderDialogPreview() {
    NotesTheme {
        DeleteReminderDialog(
            reminderTitle = "Call the dentist",
            onDismiss = {},
            onConfirm = {}
        )
    }
}
