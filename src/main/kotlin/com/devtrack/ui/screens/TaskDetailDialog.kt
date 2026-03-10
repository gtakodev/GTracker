package com.devtrack.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TaskStatus
import com.devtrack.domain.model.SessionWithEvents
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Task detail/edit dialog (P1.4.6).
 * Allows editing title, description, category, status, and date.
 * Shows detected Jira tickets (read-only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(
    task: Task,
    showDeleteConfirmation: Boolean = false,
    subTasks: List<Task> = emptyList(),
    sessions: List<SessionWithEvents> = emptyList(),
    onSave: (Task) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onCreateSubTask: (String) -> Unit = {},
    onDeleteSubTask: (UUID) -> Unit = {},
    onToggleSubTaskDone: (Task) -> Unit = {},
    onStartSubTask: (UUID) -> Unit = {},
    onEditSession: (SessionWithEvents) -> Unit = {},
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description ?: "") }
    var category by remember(task.id) { mutableStateOf(task.category) }
    var status by remember(task.id) { mutableStateOf(task.status) }
    var plannedDateStr by remember(task.id) {
        mutableStateOf(task.plannedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "")
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 560.dp)
                .heightIn(max = 700.dp),
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
                        text = I18n.t("task.detail.title"),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = I18n.t("button.close"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(I18n.t("task.field.title")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(I18n.t("task.field.description")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Category
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = I18n.t("category.${category.name.lowercase()}"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(I18n.t("task.field.category")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            TaskCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(12.dp),
                                                shape = RoundedCornerShape(3.dp),
                                                color = categoryColor(cat),
                                            ) {}
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(I18n.t("category.${cat.name.lowercase()}"))
                                        }
                                    },
                                    onClick = {
                                        category = cat
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Status
                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = I18n.t("status.${status.name.lowercase()}"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(I18n.t("task.field.status")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false },
                        ) {
                            TaskStatus.entries.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(I18n.t("status.${st.name.lowercase()}")) },
                                    onClick = {
                                        status = st
                                        statusExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Planned date field
                OutlinedTextField(
                    value = plannedDateStr,
                    onValueChange = { plannedDateStr = it },
                    label = { Text(I18n.t("task.field.planned_date")) },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Jira tickets (read-only)
                if (task.jiraTickets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = I18n.t("task.field.jira_tickets"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        task.jiraTickets.forEach { ticket ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = ticket,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sub-tasks section (P2.5.3)
                if (task.parentId == null) {
                    SubTaskSection(
                        subTasks = subTasks,
                        onCreateSubTask = onCreateSubTask,
                        onDeleteSubTask = onDeleteSubTask,
                        onToggleSubTaskDone = onToggleSubTaskDone,
                        onStartSubTask = onStartSubTask,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Session list section (P2.7.3)
                if (sessions.isNotEmpty()) {
                    SessionListSection(
                        sessions = sessions,
                        onEditSession = onEditSession,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Delete button
                    if (!showDeleteConfirmation) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(I18n.t("button.delete"))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = I18n.t("task.delete_confirm"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onConfirmDelete) {
                                Text(I18n.t("button.confirm"), color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = onCancelDelete) {
                                Text(I18n.t("button.cancel"))
                            }
                        }
                    }

                    Row {
                        OutlinedButton(onClick = onDismiss) {
                            Text(I18n.t("button.cancel"))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsedDate = try {
                                    if (plannedDateStr.isBlank()) null
                                    else LocalDate.parse(plannedDateStr)
                                } catch (e: Exception) {
                                    task.plannedDate
                                }
                                val updatedTask = task.copy(
                                    title = title,
                                    description = description.ifBlank { null },
                                    category = category,
                                    status = status,
                                    plannedDate = parsedDate,
                                )
                                onSave(updatedTask)
                            },
                            enabled = title.isNotBlank(),
                        ) {
                            Text(I18n.t("button.save"))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sub-task management section within the task detail dialog (P2.5.3).
 * Shows a list of sub-tasks with status toggles, start timer buttons,
 * and an input field to create new sub-tasks.
 */
@Composable
private fun SubTaskSection(
    subTasks: List<Task>,
    onCreateSubTask: (String) -> Unit,
    onDeleteSubTask: (UUID) -> Unit,
    onToggleSubTaskDone: (Task) -> Unit,
    onStartSubTask: (UUID) -> Unit,
) {
    var newSubTaskTitle by remember { mutableStateOf("") }
    val submitSubTask = {
        val title = newSubTaskTitle.trim()
        if (title.isNotEmpty()) {
            onCreateSubTask(title)
            newSubTaskTitle = ""
        }
    }

    Column {
        // Section header with progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = I18n.t("subtask.section_title"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subTasks.isNotEmpty()) {
                val doneCount = subTasks.count { it.status == TaskStatus.DONE }
                Text(
                    text = "$doneCount/${subTasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        if (subTasks.isNotEmpty()) {
            val doneCount = subTasks.count { it.status == TaskStatus.DONE }
            val targetProgress = doneCount.toFloat() / subTasks.size
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "dialogSubtaskProgress",
            )
            val isComplete = doneCount == subTasks.size
            val progressColor by animateColorAsState(
                targetValue = if (isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary,
                animationSpec = tween(durationMillis = 350),
                label = "dialogSubtaskProgressColor",
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Sub-task list
        subTasks.forEach { subTask ->
            SubTaskItem(
                subTask = subTask,
                onToggleDone = { onToggleSubTaskDone(subTask) },
                onDelete = { onDeleteSubTask(subTask.id) },
                onStart = { onStartSubTask(subTask.id) },
            )
        }

        // Add sub-task input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newSubTaskTitle,
                onValueChange = { newSubTaskTitle = it },
                placeholder = { Text(I18n.t("subtask.add_placeholder")) },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            submitSubTask()
                            true
                        } else false
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = submitSubTask,
                enabled = newSubTaskTitle.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = I18n.t("subtask.add"),
                    tint = if (newSubTaskTitle.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A single sub-task item with checkbox, title, and action buttons.
 */
@Composable
private fun SubTaskItem(
    subTask: Task,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
) {
    val isDone = subTask.status == TaskStatus.DONE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isDone,
            onCheckedChange = { onToggleDone() },
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = subTask.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!isDone) {
            IconButton(
                onClick = onStart,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = I18n.t("subtask.start_timer"),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = I18n.t("button.delete"),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Session list section within the task detail dialog (P2.7.3).
 * Shows a compact list of sessions with their duration, source, and edit buttons.
 */
@Composable
private fun SessionListSection(
    sessions: List<SessionWithEvents>,
    onEditSession: (SessionWithEvents) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Column {
        Text(
            text = I18n.t("session.list.title"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        sessions.forEach { swe ->
            val session = swe.session
            val startStr = session.startTime.atZone(zone).format(timeFormatter)
            val endStr = session.endTime?.atZone(zone)?.format(timeFormatter) ?: "..."

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Time range
                Text(
                    text = "$startStr - $endStr",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Duration
                Text(
                    text = formatDuration(swe.effectiveDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Source badge
                val sourceKey = when (session.source.name) {
                    "TIMER" -> "session.list.source.timer"
                    "MANUAL" -> "session.list.source.manual"
                    "POMODORO" -> "session.list.source.pomodoro"
                    else -> "session.list.source.timer"
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = I18n.t(sourceKey),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Edit button
                if (session.endTime != null) {
                    IconButton(
                        onClick = { onEditSession(swe) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = I18n.t("session.list.edit"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
