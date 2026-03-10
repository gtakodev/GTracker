package com.devtrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.theme.ThemeMode
import com.devtrack.viewmodel.SettingsViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path

/**
 * Settings screen composable (P4.6.2).
 * Sections: Appearance, Timer, Pomodoro, Reports, Data, About.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(I18n.t(msg))
            viewModel.dismissSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Title
                Text(
                    text = I18n.t("settings.title"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Appearance section
                SettingsSection(
                    title = I18n.t("settings.section.appearance"),
                    icon = Icons.Filled.Palette,
                ) {
                    // Theme radio buttons
                    Text(
                        text = I18n.t("settings.theme"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.LIGHT -> I18n.t("settings.theme.light")
                                ThemeMode.DARK -> I18n.t("settings.theme.dark")
                                ThemeMode.SYSTEM -> I18n.t("settings.theme.system")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = uiState.theme == mode,
                                    onClick = { viewModel.setTheme(mode) },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Locale dropdown
                    Text(
                        text = I18n.t("settings.locale"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("fr" to I18n.t("settings.locale.fr"), "en" to I18n.t("settings.locale.en")).forEach { (code, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = uiState.locale == code,
                                    onClick = { viewModel.setLocale(code) },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timer section
                SettingsSection(
                    title = I18n.t("settings.section.timer"),
                    icon = Icons.Filled.Timer,
                ) {
                    Text(
                        text = I18n.t("settings.inactivity_threshold"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = I18n.t("settings.inactivity_threshold.hint", uiState.inactivityThresholdMin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = uiState.inactivityThresholdMin.toFloat(),
                        onValueChange = { viewModel.setInactivityThreshold(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 22, // (120-5)/5 - 1 = 22 steps for 5-min increments
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pomodoro section
                SettingsSection(
                    title = I18n.t("settings.section.pomodoro"),
                    icon = Icons.Filled.AvTimer,
                ) {
                    // Work duration
                    SliderSetting(
                        label = I18n.t("settings.pomodoro.work"),
                        value = uiState.pomodoroWorkMin,
                        valueLabel = I18n.t("settings.pomodoro.minutes", uiState.pomodoroWorkMin),
                        range = 15f..60f,
                        steps = 8, // (60-15)/5 - 1 = 8
                        onValueChange = { viewModel.setPomodoroWorkMin(it) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Break duration
                    SliderSetting(
                        label = I18n.t("settings.pomodoro.break"),
                        value = uiState.pomodoroBreakMin,
                        valueLabel = I18n.t("settings.pomodoro.minutes", uiState.pomodoroBreakMin),
                        range = 1f..15f,
                        steps = 13, // 14 intervals, 13 steps
                        onValueChange = { viewModel.setPomodoroBreakMin(it) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Long break duration
                    SliderSetting(
                        label = I18n.t("settings.pomodoro.long_break"),
                        value = uiState.pomodoroLongBreakMin,
                        valueLabel = I18n.t("settings.pomodoro.minutes", uiState.pomodoroLongBreakMin),
                        range = 5f..30f,
                        steps = 24, // 25 intervals, 24 steps
                        onValueChange = { viewModel.setPomodoroLongBreakMin(it) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sessions before long break
                    SliderSetting(
                        label = I18n.t("settings.pomodoro.sessions_before_long"),
                        value = uiState.pomodoroSessionsBeforeLong,
                        valueLabel = uiState.pomodoroSessionsBeforeLong.toString(),
                        range = 1f..8f,
                        steps = 6, // 7 intervals, 6 steps
                        onValueChange = { viewModel.setPomodoroSessionsBeforeLong(it) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reports section
                SettingsSection(
                    title = I18n.t("settings.section.reports"),
                    icon = Icons.Filled.Assessment,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = uiState.hoursPerDay,
                            onValueChange = { viewModel.setHoursPerDay(it) },
                            label = { Text(I18n.t("settings.reports.hours_per_day")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.halfDayThreshold,
                            onValueChange = { viewModel.setHalfDayThreshold(it) },
                            label = { Text(I18n.t("settings.reports.half_day_threshold")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Data section
                SettingsSection(
                    title = I18n.t("settings.section.data"),
                    icon = Icons.Filled.Storage,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val dialog = FileDialog(null as Frame?, I18n.t("settings.data.export_backup"), FileDialog.SAVE)
                                dialog.file = "devtrack-backup.devtrack-backup"
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    viewModel.exportBackup(Path.of(dir, file))
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(I18n.t("settings.data.export_backup"))
                        }
                        OutlinedButton(
                            onClick = {
                                val dialog = FileDialog(null as Frame?, I18n.t("settings.data.import_backup"), FileDialog.LOAD)
                                dialog.setFilenameFilter { _, name -> name.endsWith(".devtrack-backup") }
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    viewModel.requestImportBackup(Path.of(dir, file))
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(I18n.t("settings.data.import_backup"))
                        }
                        OutlinedButton(
                            onClick = { /* TODO: P4.7 — Export all as Markdown */ },
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(I18n.t("settings.data.export_markdown"))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Database path (read-only)
                    if (uiState.databasePath.isNotBlank()) {
                        ReadOnlyField(
                            label = I18n.t("settings.data.database_path"),
                            value = uiState.databasePath,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // About section
                SettingsSection(
                    title = I18n.t("settings.section.about"),
                    icon = Icons.Filled.Info,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = I18n.t("settings.about.version"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = uiState.appVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Import confirmation dialog
        if (uiState.showImportConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelImport() },
                title = { Text(I18n.t("backup.import.confirm.title")) },
                text = { Text(I18n.t("backup.import.confirm.message")) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmImportBackup() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(I18n.t("button.confirm"))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.cancelImport() }) {
                        Text(I18n.t("button.cancel"))
                    }
                },
            )
        }

        // Error display
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
}

/**
 * A settings section card with a title, icon, and content.
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * A slider-based setting with label and value display.
 */
@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A read-only field displaying a label and its value.
 */
@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
