package com.devtrack.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.devtrack.domain.model.TicketSummary
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.ReportType
import com.devtrack.viewmodel.ReportsViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Reports screen (P3.5.2).
 * Provides report type selector, period navigation, Markdown preview, and copy/export actions.
 * Includes "By ticket" section (P3.7.2).
 */
@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Text(
            text = I18n.t("nav.reports"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Report Type Selector
        ReportTypeSelector(
            selectedType = uiState.selectedType,
            onSelectType = { viewModel.selectReportType(it) },
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Period Navigation
        PeriodNavigator(
            uiState = uiState,
            onPrevious = { viewModel.previousPeriod() },
            onNext = { viewModel.nextPeriod() },
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Report Preview
        ReportPreview(
            reportOutput = uiState.reportOutput,
            isGenerating = uiState.isGenerating,
            error = uiState.error,
            onCopy = { viewModel.copyToClipboard() },
            onRegenerate = { viewModel.generateReport() },
        )
        Spacer(modifier = Modifier.height(24.dp))

        // "By ticket" section (P3.7.2)
        TicketSummarySection(
            ticketSummaries = uiState.ticketSummaries,
            isLoading = uiState.isLoadingTickets,
            selectedTicket = uiState.selectedTicket,
            showDetail = uiState.showTicketDetail,
            onTicketClick = { viewModel.openTicketDetail(it) },
            onCloseDetail = { viewModel.closeTicketDetail() },
            onRefresh = { viewModel.loadTicketSummaries() },
        )
    }

    // Snackbar
    uiState.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            // Auto-dismiss after showing
            kotlinx.coroutines.delay(2000)
            viewModel.dismissSnackbar()
        }
    }
}

@Composable
private fun ReportTypeSelector(
    selectedType: ReportType,
    onSelectType: (ReportType) -> Unit,
) {
    val types = listOf(
        ReportType.DAY to I18n.t("reports.type.day"),
        ReportType.WEEK to I18n.t("reports.type.week"),
        ReportType.MONTH to I18n.t("reports.type.month"),
        ReportType.STANDUP to I18n.t("reports.type.standup"),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onSelectType(type) },
                label = { Text(label) },
                leadingIcon = if (selectedType == type) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    uiState: com.devtrack.viewmodel.ReportsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val periodLabel = when (uiState.selectedType) {
        ReportType.DAY, ReportType.STANDUP -> {
            uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", Locale.FRENCH))
        }
        ReportType.WEEK -> {
            val start = uiState.selectedWeekStart
            val end = start.plusDays(4)
            val fmt = DateTimeFormatter.ofPattern("dd/MM")
            "${I18n.t("reports.week_of")} ${start.format(fmt)} ${I18n.t("reports.to")} ${end.format(fmt)}"
        }
        ReportType.MONTH -> {
            val ym = uiState.selectedYearMonth
            val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)
                .replaceFirstChar { it.uppercase() }
            "$monthName ${ym.year}"
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = I18n.t("reports.previous"))
        }
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = I18n.t("reports.next"))
        }
    }
}

@Composable
private fun ReportPreview(
    reportOutput: com.devtrack.infrastructure.export.ReportOutput?,
    isGenerating: Boolean,
    error: String?,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reportOutput?.title ?: I18n.t("reports.preview"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (reportOutput != null) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = I18n.t("button.copy"),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = I18n.t("reports.regenerate"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isGenerating -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                reportOutput != null -> {
                    // Markdown preview (rendered as monospace text)
                    SelectionContainer {
                        Text(
                            text = reportOutput.markdownContent,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                else -> {
                    Text(
                        text = I18n.t("reports.empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * "By ticket" section in the Reports screen (P3.7.2).
 * Lists all Jira tickets with total time. Clicking opens detail.
 */
@Composable
private fun TicketSummarySection(
    ticketSummaries: List<TicketSummary>,
    isLoading: Boolean,
    selectedTicket: TicketSummary?,
    showDetail: Boolean,
    onTicketClick: (TicketSummary) -> Unit,
    onCloseDetail: () -> Unit,
    onRefresh: () -> Unit,
) {
    // Section header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = I18n.t("reports.by_ticket"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = I18n.t("reports.refresh_tickets"),
                modifier = Modifier.size(18.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        ticketSummaries.isEmpty() -> {
            Text(
                text = I18n.t("reports.no_tickets"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ticketSummaries.forEach { summary ->
                    TicketSummaryRow(
                        summary = summary,
                        onClick = { onTicketClick(summary) },
                    )
                }
            }
        }
    }

    // Ticket detail dialog (P3.7.2)
    if (showDetail && selectedTicket != null) {
        TicketDetailDialog(
            ticket = selectedTicket,
            onDismiss = onCloseDetail,
        )
    }
}

@Composable
private fun TicketSummaryRow(
    summary: TicketSummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            // Ticket name
            Text(
                text = summary.ticket,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(120.dp),
            )

            // Total time
            Text(
                text = DailyReportGenerator.formatDuration(summary.totalDuration),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp),
            )

            // Sessions count
            Text(
                text = "${summary.sessionCount} ${I18n.t("reports.sessions")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(100.dp),
            )

            // Days worked
            Text(
                text = "${summary.daysWorked.size} ${I18n.t("reports.days_worked")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Detail dialog showing all tasks and sessions for a specific Jira ticket (P3.7.2).
 */
@Composable
private fun TicketDetailDialog(
    ticket: TicketSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = ticket.ticket,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Summary stats
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            text = I18n.t("reports.total_time"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = DailyReportGenerator.formatDuration(ticket.totalDuration),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Column {
                        Text(
                            text = I18n.t("reports.sessions"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = ticket.sessionCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Column {
                        Text(
                            text = I18n.t("reports.days_worked"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = ticket.daysWorked.size.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                HorizontalDivider()

                // Related tasks
                Text(
                    text = I18n.t("reports.related_tasks"),
                    style = MaterialTheme.typography.titleSmall,
                )
                ticket.tasks.forEach { task ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${task.status.name} | ${task.category.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Days worked list
                if (ticket.daysWorked.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = I18n.t("reports.worked_dates"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    Text(
                        text = ticket.daysWorked.sorted().joinToString(", ") { it.format(dateFormat) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(I18n.t("button.close"))
            }
        },
    )
}
