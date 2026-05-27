package com.devtrack.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.TaskStatus
import com.devtrack.ui.components.DragDropLazyColumn
import com.devtrack.ui.components.TaskCard
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.TodayViewModel
import java.util.UUID

/**
 * Worklist screen composable.
 * Shows open tasks by default, with search/filter access to terminal tasks.
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
            TodayHeader(
                taskCount = uiState.taskCount,
                doneCount = uiState.doneCount,
                totalTime = formatDuration(uiState.totalTimeToday),
                onExport = { viewModel.showExportPreview() },
                onAddManualSession = { viewModel.openManualSessionEditor() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            QuickCreateField(
                text = uiState.quickCreateText,
                onTextChange = { viewModel.updateQuickCreateText(it) },
                onSubmit = { viewModel.quickCreateTask() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            WorklistFilters(
                searchQuery = uiState.searchQuery,
                showTerminalTasks = uiState.showTerminalTasks,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onShowTerminalTasksChange = { viewModel.setShowTerminalTasks(it) },
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
                            imageVector = Icons.Filled.Checklist,
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

            // Separate the currently active timer from the open task lifecycle.
            val activeTaskId = activeSession?.task?.id
            val activeTasks = tasks.filter { it.task.id == activeTaskId }
            val todoTasks = tasks.filter {
                it.task.id != activeTaskId && (it.task.status == TaskStatus.TODO || it.task.status == TaskStatus.DOING)
            }
            val terminalTasks = tasks.filter { it.task.status == TaskStatus.DONE || it.task.status == TaskStatus.ARCHIVED }

            // Combined draggable list: active + todo (P4.1.2)
            // Mutable snapshot for drag-and-drop reordering
            val draggableTasks = (activeTasks + todoTasks + terminalTasks).sortedBy { it.task.displayOrder }
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
                text = I18n.t("today.subtitle"),
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
private fun WorklistFilters(
    searchQuery: String,
    showTerminalTasks: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onShowTerminalTasksChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(I18n.t("today.search_placeholder")) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = I18n.t("button.close"),
                        )
                    }
                }
            },
            singleLine = true,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = I18n.t("today.show_terminal"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = showTerminalTasks,
                onCheckedChange = onShowTerminalTasksChange,
            )
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
