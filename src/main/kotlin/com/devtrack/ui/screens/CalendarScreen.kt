package com.devtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.WorkSession
import com.devtrack.ui.components.categoryColor
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.CalendarDayData
import com.devtrack.viewmodel.CalendarUiState
import com.devtrack.viewmodel.CalendarViewModel
import com.devtrack.viewmodel.CalendarViewMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar screen (P4.5.4).
 * Supports month grid view and week view, with a side panel for selected day details.
 */
@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: title, view toggle, navigation
        CalendarHeader(
            uiState = uiState,
            onPreviousPeriod = { viewModel.previousPeriod() },
            onNextPeriod = { viewModel.nextPeriod() },
            onGoToToday = { viewModel.goToToday() },
            onToggleView = { viewModel.toggleViewMode() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            // Content: calendar grid + side panel
            Row(modifier = Modifier.fillMaxSize()) {
                // Calendar grid (month or week)
                Box(modifier = Modifier.weight(2f).fillMaxHeight()) {
                    when (uiState.viewMode) {
                        CalendarViewMode.MONTH -> MonthGrid(
                            days = uiState.monthDays,
                            selectedDate = uiState.selectedDate,
                            maxDayHours = uiState.maxDayHours,
                            onDayClick = { viewModel.selectDay(it) },
                        )
                        CalendarViewMode.WEEK -> WeekView(
                            days = uiState.weekDays,
                            selectedDate = uiState.selectedDate,
                            onDayClick = { viewModel.selectDay(it) },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Side panel: selected day details
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp,
                ) {
                    DayDetailPanel(
                        selectedDate = uiState.selectedDate,
                        tasks = uiState.selectedDayTasks,
                        sessions = uiState.selectedDaySessions,
                        totalDuration = uiState.selectedDayDuration,
                    )
                }
            }
        }
    }
}

// -- Header --

@Composable
private fun CalendarHeader(
    uiState: CalendarUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onGoToToday: () -> Unit,
    onToggleView: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = I18n.t("calendar.title"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Navigation: previous / period label / next
        IconButton(onClick = onPreviousPeriod) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = I18n.t("calendar.previous"))
        }

        val periodLabel = if (uiState.viewMode == CalendarViewMode.MONTH) {
            val month = uiState.displayedMonth
            val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "${monthName.replaceFirstChar { it.uppercase() }} ${month.year}"
        } else {
            val start = uiState.displayedWeekStart
            val end = start.plusDays(4)
            val fmt = DateTimeFormatter.ofPattern("d MMM")
            "${start.format(fmt)} - ${end.format(fmt)} ${end.year}"
        }

        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 180.dp),
            textAlign = TextAlign.Center,
        )

        IconButton(onClick = onNextPeriod) {
            Icon(Icons.Filled.ChevronRight, contentDescription = I18n.t("calendar.next"))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Today button
        OutlinedButton(onClick = onGoToToday) {
            Text(I18n.t("calendar.today"))
        }

        Spacer(modifier = Modifier.weight(1f))

        // View mode toggle
        SegmentedViewToggle(
            currentMode = uiState.viewMode,
            onToggle = onToggleView,
        )
    }
}

@Composable
private fun SegmentedViewToggle(
    currentMode: CalendarViewMode,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        val isMonth = currentMode == CalendarViewMode.MONTH
        Surface(
            modifier = Modifier
                .clickable { if (!isMonth) onToggle() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (isMonth) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        ) {
            Text(
                text = I18n.t("calendar.view.month"),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMonth) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Surface(
            modifier = Modifier
                .clickable { if (isMonth) onToggle() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (!isMonth) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        ) {
            Text(
                text = I18n.t("calendar.view.week"),
                style = MaterialTheme.typography.labelMedium,
                color = if (!isMonth) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// -- Month Grid (P4.5.2) --

@Composable
private fun MonthGrid(
    days: List<CalendarDayData>,
    selectedDate: LocalDate,
    maxDayHours: Double,
    onDayClick: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Day-of-week headers (Mon-Sun)
        Row(modifier = Modifier.fillMaxWidth()) {
            val weekDays = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            )
            weekDays.forEach { dow ->
                Box(
                    modifier = Modifier.weight(1f).padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid of days (7 columns)
        val weeks = days.chunked(7)
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { dayData ->
                    MonthDayCell(
                        dayData = dayData,
                        isSelected = dayData.date == selectedDate,
                        maxDayHours = maxDayHours,
                        onClick = { onDayClick(dayData.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad if last week is short
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    dayData: CalendarDayData,
    isSelected: Boolean,
    maxDayHours: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = dayData.totalDuration.toMinutes() / 60.0
    // Heatmap intensity: 0.0 (no work) to 1.0 (max work)
    val intensity = if (maxDayHours > 0) (hours / maxDayHours).coerceIn(0.0, 1.0) else 0.0
    val heatmapColor = if (intensity > 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = (0.1 + intensity * 0.5).toFloat())
    } else {
        Color.Transparent
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        dayData.isToday -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }
    val borderWidth = if (isSelected || dayData.isToday) 2.dp else 0.dp

    val textAlpha = if (dayData.isCurrentMonth) 1f else 0.4f

    Surface(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        color = heatmapColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Day number
            Text(
                text = dayData.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (dayData.isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
            )

            // Task count indicator
            if (dayData.taskCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${dayData.taskCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                )
            }

            // Duration indicator (if any)
            if (dayData.totalDuration > Duration.ZERO) {
                Spacer(modifier = Modifier.height(1.dp))
                val hoursStr = String.format(Locale.US, "%.1f", hours)
                Text(
                    text = "${hoursStr}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                )
            }
        }
    }
}

// -- Week View (P4.5.3) --

@Composable
private fun WeekView(
    days: List<CalendarDayData>,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        days.forEach { dayData ->
            WeekDayColumn(
                dayData = dayData,
                isSelected = dayData.date == selectedDate,
                onClick = { onDayClick(dayData.date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekDayColumn(
    dayData: CalendarDayData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        dayData.isToday -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected || dayData.isToday) 2.dp else 1.dp

    Surface(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
        ) {
            // Day header
            val dow = dayData.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
            val dayNum = dayData.date.dayOfMonth.toString()

            Text(
                text = "$dow $dayNum",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (dayData.isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (dayData.isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )

            // Total duration
            if (dayData.totalDuration > Duration.ZERO) {
                Text(
                    text = formatDuration(dayData.totalDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Task list
            if (dayData.tasks.isEmpty()) {
                Text(
                    text = I18n.t("calendar.no_tasks"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    dayData.tasks.forEach { task ->
                        WeekTaskItem(task = task)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTaskItem(task: Task) {
    val catColor = categoryColor(task.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(catColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(catColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// -- Day Detail Panel --

@Composable
private fun DayDetailPanel(
    selectedDate: LocalDate,
    tasks: List<Task>,
    sessions: List<WorkSession>,
    totalDuration: Duration,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Date header
        Text(
            text = I18n.t("calendar.day_detail"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
        Text(
            text = selectedDate.format(dateFormatter)
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Total time
        if (totalDuration > Duration.ZERO) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${I18n.t("calendar.total_time")}: ${formatDuration(totalDuration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tasks section
        Text(
            text = if (tasks.isEmpty()) I18n.t("calendar.no_tasks")
            else I18n.t("calendar.tasks_count", tasks.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tasks, key = { it.id }) { task ->
                DayDetailTaskCard(task = task)
            }

            if (tasks.isEmpty() && sessions.isEmpty()) {
                item {
                    Text(
                        text = I18n.t("calendar.no_sessions"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailTaskCard(task: Task) {
    val catColor = categoryColor(task.category)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category color indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(catColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        text = task.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                    )
                    if (task.jiraTickets.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.jiraTickets.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Status chip
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
