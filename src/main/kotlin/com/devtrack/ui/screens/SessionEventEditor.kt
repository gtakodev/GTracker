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
import com.devtrack.domain.model.EventType
import com.devtrack.domain.model.SessionEvent
import com.devtrack.domain.model.SessionWithEvents
import com.devtrack.domain.model.WorkSession
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Mutable event holder for editing in the SessionEventEditor.
 */
data class EditableEvent(
    val id: UUID = UUID.randomUUID(),
    val type: EventType,
    val timeStr: String,
    val originalTimestamp: Instant? = null,
)

/**
 * Dialog for editing the events of an existing session (P2.7.2).
 * Shows the list of events with editable timestamps and types.
 * Allows adding/removing events and validates the event sequence on save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEventEditor(
    sessionWithEvents: SessionWithEvents,
    onSave: (sessionId: UUID, events: List<EditableEvent>) -> Unit,
    onDismiss: () -> Unit,
) {
    val session = sessionWithEvents.session
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    var editableEvents by remember(session.id) {
        mutableStateOf(
            sessionWithEvents.events.map { event ->
                EditableEvent(
                    id = event.id,
                    type = event.type,
                    timeStr = event.timestamp.atZone(zone).format(timeFormatter),
                    originalTimestamp = event.timestamp,
                )
            }
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                    Column {
                        Text(
                            text = I18n.t("session.edit_events.title"),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = session.date.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = I18n.t("button.close"))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Effective duration
                Text(
                    text = "${I18n.t("session.list.title")}: ${formatDuration(sessionWithEvents.effectiveDuration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Event list
                editableEvents.forEachIndexed { index, event ->
                    EventRow(
                        event = event,
                        onTypeChanged = { newType ->
                            editableEvents = editableEvents.toMutableList().apply {
                                this[index] = event.copy(type = newType)
                            }
                        },
                        onTimeChanged = { newTime ->
                            editableEvents = editableEvents.toMutableList().apply {
                                this[index] = event.copy(timeStr = newTime)
                            }
                        },
                        onDelete = {
                            editableEvents = editableEvents.toMutableList().apply {
                                removeAt(index)
                            }
                        },
                        canDelete = editableEvents.size > 2,
                    )
                    if (index < editableEvents.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add event button
                OutlinedButton(
                    onClick = {
                        val lastTime = editableEvents.lastOrNull()?.timeStr ?: "09:00"
                        // Suggest the next logical event type
                        val lastType = editableEvents.lastOrNull()?.type
                        val suggestedType = when (lastType) {
                            EventType.START, EventType.RESUME -> EventType.PAUSE
                            EventType.PAUSE -> EventType.RESUME
                            else -> EventType.PAUSE
                        }
                        editableEvents = editableEvents.toMutableList().apply {
                            // Insert before the last event (END) if END exists
                            val insertIndex = if (isNotEmpty() && last().type == EventType.END) {
                                size - 1
                            } else {
                                size
                            }
                            add(insertIndex, EditableEvent(type = suggestedType, timeStr = lastTime))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(I18n.t("session.edit_events.add_event"))
                }

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
                            // Validate all times parse correctly
                            val allValid = editableEvents.all { parseTimeStr(it.timeStr) != null }
                            if (!allValid) {
                                validationError = I18n.t("session.manual.validation.invalid_time")
                                return@Button
                            }
                            validationError = null
                            onSave(session.id, editableEvents)
                        },
                        enabled = editableEvents.size >= 2,
                    ) {
                        Text(I18n.t("button.save"))
                    }
                }
            }
        }
    }
}

/**
 * A single event row with type dropdown and time input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventRow(
    event: EditableEvent,
    onTypeChanged: (EventType) -> Unit,
    onTimeChanged: (String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    var typeExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Event type dropdown
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = event.type.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(I18n.t("session.edit_events.event_type"), style = MaterialTheme.typography.labelSmall) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                EventType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = {
                            onTypeChanged(type)
                            typeExpanded = false
                        },
                    )
                }
            }
        }

        // Time input
        OutlinedTextField(
            value = event.timeStr,
            onValueChange = onTimeChanged,
            label = { Text(I18n.t("session.edit_events.event_time"), style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text(I18n.t("session.manual.time_format")) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )

        // Delete button
        IconButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = I18n.t("button.delete"),
                modifier = Modifier.size(18.dp),
                tint = if (canDelete) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

/**
 * Parse a time string in "HH:MM" format to [LocalTime]. Returns null on failure.
 */
private fun parseTimeStr(timeStr: String): LocalTime? {
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
