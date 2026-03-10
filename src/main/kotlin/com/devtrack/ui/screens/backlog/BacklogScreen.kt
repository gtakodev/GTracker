package com.devtrack.ui.screens.backlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TaskStatus
import com.devtrack.domain.model.TaskWithTime
import com.devtrack.ui.components.TaskCard
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.screens.TaskDetailDialog
import com.devtrack.viewmodel.BacklogSortOption
import com.devtrack.viewmodel.BacklogViewModel
import java.util.UUID

/**
 * Backlog screen composable (P2.1.2).
 * Shows unplanned tasks with filter chips, sort bar, multi-select, and batch actions.
 */
@Composable
fun BacklogScreen(viewModel: BacklogViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when message is set
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(I18n.t(msg))
            viewModel.dismissSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            BacklogHeader(
                totalCount = uiState.filteredTasks.size,
                isMultiSelectMode = uiState.isMultiSelectMode,
                selectedCount = uiState.selectedTaskIds.size,
                onToggleMultiSelect = { viewModel.toggleMultiSelectMode() },
                onSelectAll = { viewModel.selectAll() },
                onDeselectAll = { viewModel.deselectAll() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick create field
            BacklogQuickCreate(
                text = uiState.quickCreateText,
                onTextChange = { viewModel.updateQuickCreateText(it) },
                onSubmit = { viewModel.quickCreateTask() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter & Sort bar
            FilterSortBar(
                searchQuery = uiState.searchQuery,
                onSearchChange = { viewModel.updateSearchQuery(it) },
                selectedCategories = uiState.selectedCategories,
                onToggleCategory = { viewModel.toggleCategoryFilter(it) },
                selectedStatuses = uiState.selectedStatuses,
                onToggleStatus = { viewModel.toggleStatusFilter(it) },
                sortOption = uiState.sortOption,
                onSortChange = { viewModel.setSortOption(it) },
                onClearFilters = { viewModel.clearFilters() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Batch action bar (when multi-select is active)
            if (uiState.isMultiSelectMode && uiState.selectedTaskIds.isNotEmpty()) {
                BatchActionBar(
                    selectedCount = uiState.selectedTaskIds.size,
                    onPlanToday = { viewModel.batchPlanToday() },
                    onArchive = { viewModel.batchArchive() },
                    onDelete = { viewModel.batchDelete() },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Error display
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

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Task list or empty state
            val tasks = uiState.filteredTasks
            if (tasks.isEmpty()) {
                BacklogEmptyState(hasFilters = uiState.searchQuery.isNotBlank() || uiState.selectedCategories.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.task.id }) { taskWithTime ->
                        BacklogTaskItem(
                            taskWithTime = taskWithTime,
                            isMultiSelectMode = uiState.isMultiSelectMode,
                            isSelected = taskWithTime.task.id in uiState.selectedTaskIds,
                            onToggleSelection = { viewModel.toggleTaskSelection(taskWithTime.task.id) },
                            onPlanToday = { viewModel.planTaskToday(taskWithTime.task.id) },
                            onClick = { viewModel.openTaskDetail(taskWithTime.task) },
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // Snackbar host
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
            onSave = { viewModel.saveTask(it) },
            onDismiss = { viewModel.closeTaskDetail() },
            onDelete = { viewModel.requestDeleteTask() },
            onConfirmDelete = { viewModel.confirmDeleteTask() },
            onCancelDelete = { viewModel.closeTaskDetail() },
            onCreateSubTask = { viewModel.createSubTask(it) },
            onDeleteSubTask = { viewModel.deleteSubTask(it) },
            onToggleSubTaskDone = { viewModel.toggleSubTaskDone(it) },
        )
    }
}

@Composable
private fun BacklogHeader(
    totalCount: Int,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    onToggleMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = I18n.t("nav.backlog"),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = I18n.t("backlog.subtitle", totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMultiSelectMode) {
                Text(
                    text = I18n.t("backlog.selected", selectedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onSelectAll) {
                    Text(I18n.t("backlog.select_all"))
                }
                TextButton(onClick = onDeselectAll) {
                    Text(I18n.t("backlog.deselect_all"))
                }
            }

            FilledTonalIconButton(onClick = onToggleMultiSelect) {
                Icon(
                    imageVector = if (isMultiSelectMode) Icons.Filled.Close else Icons.Filled.Checklist,
                    contentDescription = I18n.t("backlog.multi_select"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BacklogQuickCreate(
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
            Text(I18n.t("backlog.quick_create_placeholder"))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSortBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategories: Set<TaskCategory>,
    onToggleCategory: (TaskCategory) -> Unit,
    selectedStatuses: Set<TaskStatus>,
    onToggleStatus: (TaskStatus) -> Unit,
    sortOption: BacklogSortOption,
    onSortChange: (BacklogSortOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(I18n.t("backlog.search_placeholder")) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = I18n.t("button.close"))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )

        // Category filter chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TaskCategory.entries.forEach { category ->
                val isSelected = category in selectedCategories
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleCategory(category) },
                    label = {
                        Text(
                            text = I18n.t("category.${category.name.lowercase()}"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = categoryColor(category).copy(alpha = 0.2f),
                        selectedLabelColor = categoryColor(category),
                    ),
                )
            }
        }

        // Sort + status filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val statusOptions = listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED, TaskStatus.DONE)
                statusOptions.forEach { status ->
                    val isSelected = status in selectedStatuses
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleStatus(status) },
                        label = {
                            Text(
                                text = I18n.t("status.${status.name.lowercase()}"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            // Sort menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (searchQuery.isNotBlank() || selectedCategories.isNotEmpty()) {
                    TextButton(onClick = onClearFilters) {
                        Text(I18n.t("backlog.clear_filters"))
                    }
                }

                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(I18n.t("backlog.sort"))
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        BacklogSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(I18n.t("backlog.sort.${option.name.lowercase()}")) },
                                onClick = {
                                    onSortChange(option)
                                    showSortMenu = false
                                },
                                leadingIcon = if (sortOption == option) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onPlanToday: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = I18n.t("backlog.batch_actions", selectedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPlanToday) {
                    Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(I18n.t("backlog.plan_today"))
                }
                FilledTonalButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(I18n.t("backlog.archive"))
                }
                FilledTonalButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(I18n.t("button.delete"))
                }
            }
        }
    }
}

@Composable
private fun BacklogTaskItem(
    taskWithTime: TaskWithTime,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onPlanToday: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox for multi-select
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        // Task card with additional "Plan today" action
        Box(modifier = Modifier.weight(1f)) {
            TaskCard(
                taskWithTime = taskWithTime,
                onClick = onClick,
            )
        }

        // Quick "Plan today" button
        if (!isMultiSelectMode) {
            IconButton(onClick = onPlanToday) {
                Icon(
                    Icons.Filled.Today,
                    contentDescription = I18n.t("backlog.plan_today"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BacklogEmptyState(hasFilters: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasFilters) I18n.t("backlog.empty_filtered") else I18n.t("backlog.empty"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
