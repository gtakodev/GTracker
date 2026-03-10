package com.devtrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.devtrack.domain.model.Task
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.i18n.I18n
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Dialog for creating a manual session (P2.7.1).
 * Allows the user to select a task, date, start time, end time, and optional notes.
 * The session is created with source = MANUAL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSessionEditor(
    tasks: List<Task>,
    onCreateSession: (taskId: java.util.UUID, date: LocalDate, startTime: LocalTime, endTime: LocalTime, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var taskExpanded by remember { mutableStateOf(false) }
    var dateStr by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var startTimeStr by remember { mutableStateOf("") }
    var endTimeStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTasks = remember(searchQuery, tasks) {
        if (searchQuery.isBlank()) tasks
        else tasks.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 520.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = I18n.t("session.manual.title"),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = I18n.t("button.close"))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Task selector dropdown
                ExposedDropdownMenuBox(
                    expanded = taskExpanded,
                    onExpandedChange = { taskExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedTask?.title ?: "",
                        onValueChange = { searchQuery = it },
                        readOnly = false,
                        label = { Text(I18n.t("session.manual.task")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = taskExpanded,
                        onDismissRequest = { taskExpanded = false },
                    ) {
                        filteredTasks.take(20).forEach { task ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(12.dp),
                                            shape = RoundedCornerShape(3.dp),
                                            color = categoryColor(task.category),
                                        ) {}
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(task.title, maxLines = 1)
                                    }
                                },
                                onClick = {
                                    selectedTask = task
                                    searchQuery = ""
                                    taskExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date field
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text(I18n.t("session.manual.date")) },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Start/End time fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = startTimeStr,
                        onValueChange = { startTimeStr = it },
                        label = { Text(I18n.t("session.manual.start_time")) },
                        placeholder = { Text(I18n.t("session.manual.time_format")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = endTimeStr,
                        onValueChange = { endTimeStr = it },
                        label = { Text(I18n.t("session.manual.end_time")) },
                        placeholder = { Text(I18n.t("session.manual.time_format")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(I18n.t("session.manual.notes")) },
                    placeholder = { Text(I18n.t("session.manual.notes_placeholder")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    maxLines = 3,
                )

                // Validation error
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(I18n.t("button.cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Validate
                            val task = selectedTask
                            if (task == null) {
                                validationError = I18n.t("session.manual.validation.task_required")
                                return@Button
                            }

                            val startTime = parseTime(startTimeStr)
                            val endTime = parseTime(endTimeStr)
                            if (startTime == null || endTime == null) {
                                validationError = I18n.t("session.manual.validation.invalid_time")
                                return@Button
                            }

                            if (!startTime.isBefore(endTime)) {
                                validationError = I18n.t("session.manual.validation.start_before_end")
                                return@Button
                            }

                            val date = try {
                                LocalDate.parse(dateStr)
                            } catch (e: Exception) {
                                validationError = I18n.t("session.manual.validation.invalid_time")
                                return@Button
                            }

                            validationError = null
                            onCreateSession(
                                task.id,
                                date,
                                startTime,
                                endTime,
                                notes.ifBlank { null },
                            )
                        },
                        enabled = selectedTask != null && startTimeStr.isNotBlank() && endTimeStr.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(I18n.t("button.create"))
                    }
                }
            }
        }
    }
}

/**
 * Parse a time string in "HH:MM" or "H:MM" format to [LocalTime].
 * Returns null if parsing fails.
 */
private fun parseTime(timeStr: String): LocalTime? {
    return try {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null
        LocalTime.of(hours, minutes)
    } catch (e: Exception) {
        null
    }
}
