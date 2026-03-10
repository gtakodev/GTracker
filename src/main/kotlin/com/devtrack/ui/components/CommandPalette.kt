package com.devtrack.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devtrack.domain.service.PaletteSuggestion
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.CommandPaletteViewModel
import com.devtrack.viewmodel.PaletteMode

/**
 * Command Palette composable (P2.3.1).
 *
 * Modal dialog centered on screen, with auto-focus input, real-time suggestions,
 * keyboard navigation (Up/Down/Enter/Escape), and two modes (command and search/create).
 */
@Composable
fun CommandPalette(viewModel: CommandPaletteViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isVisible) return

    Dialog(
        onDismissRequest = { viewModel.close() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        CommandPaletteContent(
            input = uiState.input,
            suggestions = uiState.suggestions,
            selectedIndex = uiState.selectedIndex,
            mode = uiState.mode,
            isExecuting = uiState.isExecuting,
            onInputChange = { viewModel.updateInput(it) },
            onMoveUp = { viewModel.moveSelectionUp() },
            onMoveDown = { viewModel.moveSelectionDown() },
            onExecuteSelected = { viewModel.executeSelected() },
            onExecuteSuggestion = { viewModel.executeSuggestion(it) },
            onClose = { viewModel.close() },
        )
    }
}

@Composable
private fun CommandPaletteContent(
    input: String,
    suggestions: List<PaletteSuggestion>,
    selectedIndex: Int,
    mode: PaletteMode,
    isExecuting: Boolean,
    onInputChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onExecuteSelected: () -> Unit,
    onExecuteSuggestion: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Auto-focus the input field when the palette opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Scroll to keep selected item visible
    LaunchedEffect(selectedIndex) {
        if (suggestions.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, suggestions.lastIndex))
        }
    }

    val placeholder = when (mode) {
        PaletteMode.COMMAND -> I18n.t("command_palette.placeholder")
        PaletteMode.CREATE -> I18n.t("command_palette.placeholder_create")
    }

    Surface(
        modifier = Modifier
            .width(560.dp)
            .heightIn(max = 440.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // Input field
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (mode == PaletteMode.CREATE) Icons.Filled.Add else Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.Escape -> { onClose(); true }
                            Key.DirectionUp -> { onMoveUp(); true }
                            Key.DirectionDown -> { onMoveDown(); true }
                            Key.Enter -> { onExecuteSelected(); true }
                            else -> false
                        }
                    },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            )

            // Mode indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (mode) {
                        PaletteMode.COMMAND -> I18n.t("command_palette.mode.command")
                        PaletteMode.CREATE -> I18n.t("command_palette.mode.search")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(
                    text = I18n.t("command_palette.hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Suggestion list
            if (suggestions.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    itemsIndexed(
                        items = suggestions,
                        key = { index, suggestion -> "${index}_${suggestion.label}" },
                    ) { index, suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            isSelected = index == selectedIndex,
                            onClick = { onExecuteSuggestion(index) },
                        )
                    }
                }
            } else if (input.isNotBlank()) {
                // No results
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = I18n.t("command_palette.no_results"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: PaletteSuggestion,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    val isCommand = suggestion.label.startsWith("/")
    val isCreateAction = suggestion.command is com.devtrack.domain.service.PaletteCommand.CreateTask

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        val icon = when {
            isCommand -> Icons.Filled.Terminal
            isCreateAction -> Icons.Filled.Add
            suggestion.task != null -> Icons.Filled.Task
            else -> Icons.Filled.Search
        }
        val iconTint = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (suggestion.description.isNotBlank()) {
                Text(
                    text = I18n.t(suggestion.description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Task category badge
        if (suggestion.task != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = I18n.t("category.${suggestion.task.category.name.lowercase()}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Keyboard hint for selected item
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = I18n.t("command_palette.key.enter"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}
