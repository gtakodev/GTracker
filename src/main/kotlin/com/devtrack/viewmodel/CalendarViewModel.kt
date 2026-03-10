package com.devtrack.viewmodel

import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.WorkSession
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/**
 * View mode for the calendar (month grid vs. week columns).
 */
enum class CalendarViewMode {
    MONTH,
    WEEK,
}

/**
 * Data for a single calendar day cell: density (hours), task count, tasks list.
 */
data class CalendarDayData(
    val date: LocalDate,
    val totalDuration: Duration = Duration.ZERO,
    val taskCount: Int = 0,
    val tasks: List<Task> = emptyList(),
    val sessions: List<WorkSession> = emptyList(),
    val isCurrentMonth: Boolean = true,
    val isToday: Boolean = false,
)

/**
 * UI state for the Calendar screen (P4.5.1).
 */
data class CalendarUiState(
    /** The currently displayed month (first day of month). */
    val displayedMonth: YearMonth = YearMonth.now(),
    /** The first day of the displayed week (for week view). */
    val displayedWeekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    /** The selected day (for side panel). */
    val selectedDate: LocalDate = LocalDate.now(),
    /** Day data for the full month grid (includes prev/next month padding days). */
    val monthDays: List<CalendarDayData> = emptyList(),
    /** Day data for the week view (Mon-Fri). */
    val weekDays: List<CalendarDayData> = emptyList(),
    /** Current view mode. */
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    /** Tasks for the selected day (for side panel). */
    val selectedDayTasks: List<Task> = emptyList(),
    /** Sessions for the selected day (for side panel). */
    val selectedDaySessions: List<WorkSession> = emptyList(),
    /** Total duration for the selected day. */
    val selectedDayDuration: Duration = Duration.ZERO,
    /** Maximum hours in any day in the current view (for heatmap normalization). */
    val maxDayHours: Double = 0.0,
    /** Loading state. */
    val isLoading: Boolean = false,
    /** Error message. */
    val error: String? = null,
)

/**
 * ViewModel for the Calendar screen (P4.5.1).
 * Manages month/week navigation, density heatmap data, and selected day details.
 */
class CalendarViewModel(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val timeCalculator: TimeCalculator,
    private val taskService: TaskService,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(CalendarViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadCalendar()
    }

    // -- Navigation --

    /**
     * Go to the previous month (month view) or previous week (week view).
     */
    fun previousPeriod() {
        val state = _uiState.value
        if (state.viewMode == CalendarViewMode.MONTH) {
            _uiState.update { it.copy(displayedMonth = it.displayedMonth.minusMonths(1)) }
        } else {
            _uiState.update { it.copy(displayedWeekStart = it.displayedWeekStart.minusWeeks(1)) }
        }
        loadCalendar()
    }

    /**
     * Go to the next month (month view) or next week (week view).
     */
    fun nextPeriod() {
        val state = _uiState.value
        if (state.viewMode == CalendarViewMode.MONTH) {
            _uiState.update { it.copy(displayedMonth = it.displayedMonth.plusMonths(1)) }
        } else {
            _uiState.update { it.copy(displayedWeekStart = it.displayedWeekStart.plusWeeks(1)) }
        }
        loadCalendar()
    }

    /**
     * Navigate to today and select it.
     */
    fun goToToday() {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                displayedMonth = YearMonth.from(today),
                displayedWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                selectedDate = today,
            )
        }
        loadCalendar()
    }

    /**
     * Select a day to show its details in the side panel.
     */
    fun selectDay(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadSelectedDayDetails()
    }

    /**
     * Toggle between month and week view.
     */
    fun toggleViewMode() {
        val state = _uiState.value
        val newMode = if (state.viewMode == CalendarViewMode.MONTH) {
            CalendarViewMode.WEEK
        } else {
            CalendarViewMode.MONTH
        }
        // When switching to week view, set the week start to the week containing the selected date
        val weekStart = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update {
            it.copy(
                viewMode = newMode,
                displayedWeekStart = weekStart,
            )
        }
        loadCalendar()
    }

    /**
     * Set view mode explicitly.
     */
    fun setViewMode(mode: CalendarViewMode) {
        if (_uiState.value.viewMode == mode) return
        val state = _uiState.value
        val weekStart = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update {
            it.copy(viewMode = mode, displayedWeekStart = weekStart)
        }
        loadCalendar()
    }

    // -- Data loading --

    /**
     * Load calendar data for the current view (month or week).
     */
    fun loadCalendar() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val state = _uiState.value

                if (state.viewMode == CalendarViewMode.MONTH) {
                    loadMonthData(state.displayedMonth)
                } else {
                    loadWeekData(state.displayedWeekStart)
                }

                // Also load selected day details
                loadSelectedDayDetailsInternal()

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                logger.error("Failed to load calendar data", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadMonthData(yearMonth: YearMonth) {
        // Build the full grid: start from Monday of the week containing the 1st
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()

        // Grid starts on Monday of the first week
        val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Grid ends on Sunday of the last week
        val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        val dayDataList = loadDayRange(gridStart, gridEnd, yearMonth)
        val maxHours = dayDataList.maxOfOrNull { it.totalDuration.toMinutes() / 60.0 } ?: 0.0

        _uiState.update {
            it.copy(
                monthDays = dayDataList,
                maxDayHours = maxHours,
            )
        }
    }

    private suspend fun loadWeekData(weekStart: LocalDate) {
        // Monday to Friday
        val weekEnd = weekStart.plusDays(4)
        val dayDataList = loadDayRange(weekStart, weekEnd, YearMonth.from(weekStart))
        val maxHours = dayDataList.maxOfOrNull { it.totalDuration.toMinutes() / 60.0 } ?: 0.0

        _uiState.update {
            it.copy(
                weekDays = dayDataList,
                maxDayHours = maxHours,
            )
        }
    }

    /**
     * Build CalendarDayData for each day in a range.
     * Fetches tasks and sessions for the range in bulk.
     */
    internal suspend fun loadDayRange(
        start: LocalDate,
        end: LocalDate,
        referenceMonth: YearMonth,
    ): List<CalendarDayData> {
        val today = LocalDate.now()

        // Bulk fetch tasks and sessions for the range
        val tasks = taskRepository.findByDateRange(start, end)
        val sessions = sessionRepository.findByDateRange(start, end)

        // Group by date
        val tasksByDate = tasks.groupBy { it.plannedDate }
        val sessionsByDate = sessions.groupBy { it.date }

        val days = mutableListOf<CalendarDayData>()
        var currentDate = start
        while (!currentDate.isAfter(end)) {
            val dayTasks = tasksByDate[currentDate] ?: emptyList()
            val daySessions = sessionsByDate[currentDate] ?: emptyList()

            // Calculate total duration from sessions
            val totalDuration = daySessions.fold(Duration.ZERO) { acc, session ->
                val sessionDuration = if (session.endTime != null) {
                    Duration.between(session.startTime, session.endTime)
                } else {
                    Duration.ZERO
                }
                acc + sessionDuration
            }

            days.add(
                CalendarDayData(
                    date = currentDate,
                    totalDuration = totalDuration,
                    taskCount = dayTasks.size,
                    tasks = dayTasks,
                    sessions = daySessions,
                    isCurrentMonth = YearMonth.from(currentDate) == referenceMonth,
                    isToday = currentDate == today,
                )
            )
            currentDate = currentDate.plusDays(1)
        }
        return days
    }

    private fun loadSelectedDayDetails() {
        scope.launch {
            try {
                loadSelectedDayDetailsInternal()
            } catch (e: Exception) {
                logger.error("Failed to load selected day details", e)
            }
        }
    }

    private suspend fun loadSelectedDayDetailsInternal() {
        val selectedDate = _uiState.value.selectedDate
        val tasks = taskRepository.findByDate(selectedDate)
        val sessions = sessionRepository.findByDate(selectedDate)

        val totalDuration = sessions.fold(Duration.ZERO) { acc, session ->
            val sessionDuration = if (session.endTime != null) {
                Duration.between(session.startTime, session.endTime)
            } else {
                Duration.ZERO
            }
            acc + sessionDuration
        }

        _uiState.update {
            it.copy(
                selectedDayTasks = tasks,
                selectedDaySessions = sessions,
                selectedDayDuration = totalDuration,
            )
        }
    }

    // -- Drag & Drop (P4.1.2) --

    /**
     * Move a task to a different date (Calendar drag & drop).
     */
    fun moveTaskToDate(taskId: java.util.UUID, targetDate: LocalDate) {
        scope.launch {
            try {
                taskService.moveTaskToDate(taskId, targetDate)
                loadCalendar()
            } catch (e: Exception) {
                logger.error("Failed to move task to date", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Utility --

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dispose() {
        scope.cancel()
    }
}
