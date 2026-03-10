package com.devtrack.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.components.TimelineBar
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.TimelineSessionEntry
import com.devtrack.viewmodel.TimelineViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timeline screen (P3.6.3).
 * Displays a visual timeline of the day's sessions with:
 * - Day navigation (previous/next, today button)
 * - Timeline bar visualization at the top
 * - Detailed session list below
 * - Export button (plain text to clipboard)
 */
@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Text(
            text = I18n.t("nav.timeline"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Day navigation
        DayNavigator(
            selectedDate = uiState.selectedDate,
            onPrevious = { viewModel.previousDay() },
            onNext = { viewModel.nextDay() },
            onToday = { viewModel.goToToday() },
            onExport = { viewModel.exportTimeline() },
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Timeline bar
        TimelineBar(
            blocks = uiState.timelineBlocks,
            dayStartHour = uiState.dayStartHour,
            dayEndHour = uiState.dayEndHour,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Total time summary
        if (uiState.totalTime.toMinutes() > 0) {
            Text(
                text = "${I18n.t("timeline.total")}: ${DailyReportGenerator.formatDuration(uiState.totalTime)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Loading / Error / Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.sessionEntries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = I18n.t("timeline.no_sessions"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                // Session list header
                Text(
                    text = I18n.t("timeline.session_list"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Session entries
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.sessionEntries.forEach { entry ->
                        SessionEntryRow(entry)
                    }
                }
            }
        }
    }

    // Snackbar handling
    uiState.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000)
            viewModel.dismissSnackbar()
        }
    }
}

@Composable
private fun DayNavigator(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onExport: () -> Unit,
) {
    val dateLabel = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", Locale.FRENCH)
    )
    val isToday = selectedDate == LocalDate.now()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = I18n.t("timeline.previous_day"))
        }

        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = I18n.t("timeline.next_day"))
        }

        if (!isToday) {
            TextButton(onClick = onToday) {
                Text(I18n.t("timeline.today"))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Export button
        OutlinedButton(
            onClick = onExport,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = I18n.t("button.export"),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(I18n.t("button.export"), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A row displaying a single session in the detailed list.
 */
@Composable
private fun SessionEntryRow(entry: TimelineSessionEntry) {
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    val startStr = entry.startTime.format(timeFormat)
    val endStr = entry.endTime.format(timeFormat)
    val durationStr = DailyReportGenerator.formatDuration(entry.effectiveDuration)
    val catColor = categoryColor(entry.task.category)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(catColor, shape = RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Time range
            Text(
                text = "$startStr - $endStr",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(110.dp),
            )

            // Duration
            Text(
                text = durationStr,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(70.dp),
            )

            // Task title
            Text(
                text = entry.task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Jira tickets
            if (entry.task.jiraTickets.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.task.jiraTickets.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
