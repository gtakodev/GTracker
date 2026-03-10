package com.devtrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TemplateTask
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.TemplatesViewModel

/**
 * Templates screen composable (P4.2.2).
 * Lists templates with category, default duration, edit/delete/instantiate actions.
 */
@Composable
fun TemplatesScreen(viewModel: TemplatesViewModel) {
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
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = I18n.t("templates.title"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { viewModel.openCreateDialog() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(I18n.t("templates.add"))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.templates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = I18n.t("templates.empty"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.templates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onEdit = { viewModel.openEditDialog(template) },
                            onDelete = { viewModel.requestDelete(template) },
                            onInstantiate = { viewModel.instantiateForToday(template) },
                        )
                    }
                }
            }
        }

        // Error
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text(I18n.t("button.ok"))
                    }
                },
            ) {
                Text(error)
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Edit/Create dialog
    if (uiState.showEditDialog) {
        TemplateEditDialog(
            isEditing = uiState.editingTemplate != null,
            title = uiState.editTitle,
            category = uiState.editCategory,
            durationMin = uiState.editDurationMin,
            onTitleChange = { viewModel.updateEditTitle(it) },
            onCategoryChange = { viewModel.updateEditCategory(it) },
            onDurationChange = { viewModel.updateEditDuration(it) },
            onSave = { viewModel.saveTemplate() },
            onDismiss = { viewModel.closeEditDialog() },
        )
    }

    // Delete confirmation
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(I18n.t("templates.delete_confirm_title")) },
            text = {
                Text(
                    I18n.t("templates.delete_confirm_message",
                        uiState.deletingTemplate?.title ?: "")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(I18n.t("button.delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(I18n.t("button.cancel"))
                }
            },
        )
    }
}

/**
 * Card displaying a single template with actions.
 */
@Composable
private fun TemplateCard(
    template: TemplateTask,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInstantiate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category color indicator
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.small,
                color = categoryColor(template.category),
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            // Title + category + duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = template.category.labelFr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    template.defaultDurationMin?.let { mins ->
                        Text(
                            text = "${mins} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Actions
            FilledTonalButton(
                onClick = onInstantiate,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(I18n.t("templates.instantiate_today"))
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = I18n.t("button.edit"))
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = I18n.t("button.delete"),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Dialog for creating or editing a template.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditDialog(
    isEditing: Boolean,
    title: String,
    category: TaskCategory,
    durationMin: String,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (TaskCategory) -> Unit,
    onDurationChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) I18n.t("templates.edit_title")
                else I18n.t("templates.create_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(I18n.t("templates.field_title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = category.labelFr,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(I18n.t("templates.field_category")) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        TaskCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.labelFr) },
                                onClick = {
                                    onCategoryChange(cat)
                                    categoryExpanded = false
                                },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.size(12.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = categoryColor(cat),
                                    ) {}
                                },
                            )
                        }
                    }
                }

                // Duration
                OutlinedTextField(
                    value = durationMin,
                    onValueChange = onDurationChange,
                    label = { Text(I18n.t("templates.field_duration")) },
                    singleLine = true,
                    placeholder = { Text(I18n.t("templates.field_duration_placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = title.isNotBlank(),
            ) {
                Text(I18n.t("button.save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(I18n.t("button.cancel"))
            }
        },
    )
}
