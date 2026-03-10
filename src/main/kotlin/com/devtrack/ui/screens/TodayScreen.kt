package com.devtrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.TaskStatus
import com.devtrack.domain.model.TaskWithTime
import com.devtrack.ui.components.DragDropLazyColumn
import com.devtrack.ui.components.TaskCard
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.TodayViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Today screen composable (P1.4.4).
 * Shows header with date/count/total, quick-create, and task sections.
 */
@Composable
fun TodayScreen(viewModel: TodayViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Stable map of expanded subtask sections — survives loadTasks() recomposition
    val expandedTaskIds = remember { mutableStateMapOf<UUID, Boolean>() }

    // Show snackbar when snackbarMessage is set
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(I18n.t(msg))
            viewModel.dismissSnackbar()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Ctrl+E shortcut for export
                if (event.key == Key.E &&
                    event.isCtrlPressed &&
                    event.type == KeyEventType.KeyDown
                ) {
                    viewModel.showExportPreview()
                    true
                } else false
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            TodayHeader(
                taskCount = uiState.taskCount,
                doneCount = uiState.doneCount,
                totalTime = formatDuration(uiState.totalTimeToday),
                onExport = { viewModel.showExportPreview() },
                onAddManualSession = { viewModel.openManualSessionEditor() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick create field
            QuickCreateField(
                text = uiState.quickCreateText,
                onTextChange = { viewModel.updateQuickCreateText(it) },
                onSubmit = { viewModel.quickCreateTask() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Error snackbar
            if (uiState.error != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text(I18n.t("button.close"))
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text(uiState.error ?: "")
                }
            }

            val tasks = uiState.tasks
            if (tasks.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = I18n.t("today.empty"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            // Separate tasks by status
            val activeTasks = tasks.filter { it.task.status == TaskStatus.IN_PROGRESS }
            val todoTasks = tasks.filter { it.task.status == TaskStatus.TODO || it.task.status == TaskStatus.PAUSED }
            val doneTasks = tasks.filter { it.task.status == TaskStatus.DONE }

            // Combined draggable list: active + todo (P4.1.2)
            // Mutable snapshot for drag-and-drop reordering
            val draggableTasks = (activeTasks + todoTasks).sortedBy { it.task.displayOrder }
            var orderedTasks by remember(draggableTasks) { mutableStateOf(draggableTasks) }

            DragDropLazyColumn(
                items = orderedTasks,
                key = { it.task.id },
                onMove = { from, to ->
                    orderedTasks = orderedTasks.toMutableList().apply {
                        add(to, removeAt(from))
                    }
                },
                onDragEnd = {
                    viewModel.reorderTasks(orderedTasks.map { it.task.id })
                },
                modifier = Modifier.fillMaxSize(),
                footerContent = {
                    Column {
                        // "Done" section (collapsible)
                        if (doneTasks.isNotEmpty()) {
                            DoneSection(
                                doneTasks = doneTasks,
                                activeSession = activeSession,
                                viewModel = viewModel,
                                expandedTaskIds = expandedTaskIds,
                            )
                        }

                        // Backlog peek section (P2.2.3)
                        if (uiState.backlogPeek.isNotEmpty()) {
                            BacklogPeekSection(
                                backlogTasks = uiState.backlogPeek,
                                onPlanToday = { viewModel.planTaskToday(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                },
            ) { taskWithTime, _, _ ->
                val isThisActive = activeSession?.task?.id == taskWithTime.task.id
                TaskCard(
                    taskWithTime = taskWithTime,
                    isActive = isThisActive,
                    isPaused = isThisActive && (activeSession?.isPaused == true),
                    activeDuration = if (isThisActive) activeSession?.effectiveDuration else null,
                    onPlay = { viewModel.startTask(taskWithTime.task.id) },
                    onPause = { viewModel.pauseSession() },
                    onResume = { viewModel.resumeSession() },
                    onStop = { viewModel.stopSession() },
                    onMarkDone = { viewModel.markDone(taskWithTime.task.id) },
                    onToggleSubTaskDone = { viewModel.toggleSubTaskDone(it) },
                    onDeleteSubTask = { viewModel.deleteSubTask(it) },
                    subTasksExpanded = expandedTaskIds[taskWithTime.task.id] == true,
                    onToggleSubTasksExpanded = { expandedTaskIds[taskWithTime.task.id] = !(expandedTaskIds[taskWithTime.task.id] ?: false) },
                    onClick = { viewModel.openTaskDetail(taskWithTime.task) },
                )
            }
        }

        // Snackbar host at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Task detail dialog
    if (uiState.showTaskDetail && uiState.selectedTask != null) {
        TaskDetailDialog(
            task = uiState.selectedTask!!,
            showDeleteConfirmation = uiState.showDeleteConfirmation,
            subTasks = uiState.subTasks,
            sessions = uiState.sessionListForTask,
            onSave = { viewModel.saveTask(it) },
            onDismiss = { viewModel.closeTaskDetail() },
            onDelete = { viewModel.requestDeleteTask() },
            onConfirmDelete = { viewModel.confirmDeleteTask() },
            onCancelDelete = { viewModel.closeTaskDetail() },
            onCreateSubTask = { viewModel.createSubTask(it) },
            onDeleteSubTask = { viewModel.deleteSubTask(it) },
            onToggleSubTaskDone = { viewModel.toggleSubTaskDone(it) },
            onStartSubTask = { viewModel.startSubTask(it) },
            onEditSession = { viewModel.openSessionEventEditor(it) },
        )
    }

    // Manual session editor dialog (P2.7.1)
    if (uiState.showManualSessionEditor) {
        ManualSessionEditor(
            tasks = uiState.allTasks,
            onCreateSession = { taskId, date, startTime, endTime, notes ->
                viewModel.createManualSession(taskId, date, startTime, endTime, notes)
            },
            onDismiss = { viewModel.closeManualSessionEditor() },
        )
    }

    // Session event editor dialog (P2.7.2)
    if (uiState.showSessionEventEditor && uiState.editingSession != null) {
        SessionEventEditor(
            sessionWithEvents = uiState.editingSession!!,
            onSave = { sessionId, events -> viewModel.saveSessionEvents(sessionId, events) },
            onDismiss = { viewModel.closeSessionEventEditor() },
        )
    }

    // Export preview dialog
    if (uiState.showExportPreview && uiState.exportMarkdown != null) {
        ExportPreviewDialog(
            markdown = uiState.exportMarkdown ?: "",
            onCopy = { viewModel.copyExportToClipboard() },
            onDismiss = { viewModel.closeExportPreview() },
        )
    }
}

@Composable
private fun TodayHeader(taskCount: Int, doneCount: Int, totalTime: String, onExport: () -> Unit, onAddManualSession: () -> Unit) {
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = I18n.t("nav.today"),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = today.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Task count
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = I18n.t("today.tasks_count", taskCount, doneCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            // Total time
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Add manual session button (P2.7)
            FilledTonalIconButton(onClick = onAddManualSession) {
                Icon(
                    Icons.Filled.MoreTime,
                    contentDescription = I18n.t("session.manual.add_button"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Export button
            FilledTonalIconButton(onClick = onExport) {
                Icon(
                    Icons.Filled.FileUpload,
                    contentDescription = "${I18n.t("button.export")} (Ctrl+E)",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun QuickCreateField(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                    onSubmit()
                    true
                } else false
            },
        placeholder = {
            Text(I18n.t("today.quick_create_placeholder"))
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onSubmit) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = I18n.t("button.create"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun DoneSection(
    doneTasks: List<com.devtrack.domain.model.TaskWithTime>,
    activeSession: com.devtrack.domain.model.ActiveSessionState?,
    viewModel: TodayViewModel,
    expandedTaskIds: MutableMap<UUID, Boolean>,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) I18n.t("sidebar.collapse") else I18n.t("sidebar.expand"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = I18n.t("today.section.done"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = doneTasks.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        if (expanded) {
            doneTasks.forEach { taskWithTime ->
                TaskCard(
                    taskWithTime = taskWithTime,
                    onToggleSubTaskDone = { viewModel.toggleSubTaskDone(it) },
                    onDeleteSubTask = { viewModel.deleteSubTask(it) },
                    subTasksExpanded = expandedTaskIds[taskWithTime.task.id] == true,
                    onToggleSubTasksExpanded = { expandedTaskIds[taskWithTime.task.id] = !(expandedTaskIds[taskWithTime.task.id] ?: false) },
                    onClick = { viewModel.openTaskDetail(taskWithTime.task) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Backlog peek section for the Today screen (P2.2.3).
 * Shows the first 5 backlog tasks with a "Plan for today" button.
 */
@Composable
private fun BacklogPeekSection(
    backlogTasks: List<TaskWithTime>,
    onPlanToday: (java.util.UUID) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = I18n.t("today.backlog_peek"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        backlogTasks.forEach { taskWithTime ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TaskCard(
                        taskWithTime = taskWithTime,
                        onClick = {},
                    )
                }
                IconButton(onClick = { onPlanToday(taskWithTime.task.id) }) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = I18n.t("backlog.plan_today"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
