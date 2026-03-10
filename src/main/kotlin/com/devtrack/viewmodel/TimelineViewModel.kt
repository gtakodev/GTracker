package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.TimeCalculator
import com.devtrack.infrastructure.export.ClipboardService
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.i18n.I18n
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext

/**
 * A block on the timeline representing a work session or a gap.
 */
data class TimelineBlock(
    /** Start time of this block (hour of day as double, e.g. 9.5 = 9:30). */
    val startHour: Double,
    /** End time of this block. */
    val endHour: Double,
    /** The task name (null for gaps). */
    val taskTitle: String?,
    /** Task category (null for gaps). */
    val category: TaskCategory?,
    /** Duration of this block. */
    val duration: Duration,
    /** Whether this block is a gap (no session). */
    val isGap: Boolean = false,
    /** Whether this block represents a paused period. */
    val isPaused: Boolean = false,
    /** The session ID (null for gaps). */
    val sessionId: java.util.UUID? = null,
)

/**
 * A detailed session entry for the list below the timeline.
 */
data class TimelineSessionEntry(
    val session: WorkSession,
    val task: Task,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val effectiveDuration: Duration,
    val events: List<SessionEvent>,
)

/**
 * UI state for the Timeline screen (P3.6.1).
 */
data class TimelineUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val timelineBlocks: List<TimelineBlock> = emptyList(),
    val sessionEntries: List<TimelineSessionEntry> = emptyList(),
    val totalTime: Duration = Duration.ZERO,
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    /** Timeline view range (hours of day). */
    val dayStartHour: Int = 8,
    val dayEndHour: Int = 20,
)

/**
 * ViewModel for the Timeline screen (P3.6.1).
 * Manages date selection, timeline block computation, and gap calculation.
 */
class TimelineViewModel(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(TimelineViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(TimelineUiState(isLoading = true))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadTimeline()
    }

    /**
     * Select a date and reload the timeline.
     */
    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadTimeline()
    }

    /**
     * Navigate to the previous day.
     */
    fun previousDay() {
        selectDate(_uiState.value.selectedDate.minusDays(1))
    }

    /**
     * Navigate to the next day.
     */
    fun nextDay() {
        selectDate(_uiState.value.selectedDate.plusDays(1))
    }

    /**
     * Navigate to today.
     */
    fun goToToday() {
        selectDate(LocalDate.now())
    }

    /**
     * Load timeline data for the selected date.
     */
    fun loadTimeline() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val date = _uiState.value.selectedDate
                val zone = ZoneId.systemDefault()

                val sessions = sessionRepository.findByDate(date)

                if (sessions.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            timelineBlocks = emptyList(),
                            sessionEntries = emptyList(),
                            totalTime = Duration.ZERO,
                            isLoading = false,
                        )
                    }
                    return@launch
                }

                // Load events and tasks for all sessions
                val eventsMap = sessions.associate { s ->
                    s.id to eventRepository.findBySessionId(s.id)
                }
                val tasksMap = mutableMapOf<java.util.UUID, Task>()
                sessions.forEach { s ->
                    if (s.taskId !in tasksMap) {
                        taskRepository.findById(s.taskId)?.let { tasksMap[s.taskId] = it }
                    }
                }

                // Build session entries sorted by start time
                val entries = sessions
                    .mapNotNull { session ->
                        val task = tasksMap[session.taskId] ?: return@mapNotNull null
                        val events = eventsMap[session.id] ?: emptyList()
                        val effectiveDuration = timeCalculator.calculateEffectiveTime(events)

                        val startLocal = session.startTime.atZone(zone).toLocalTime()
                        val endLocal = (session.endTime ?: Instant.now()).atZone(zone).toLocalTime()

                        TimelineSessionEntry(
                            session = session,
                            task = task,
                            startTime = startLocal,
                            endTime = endLocal,
                            effectiveDuration = effectiveDuration,
                            events = events,
                        )
                    }
                    .sortedBy { it.startTime }

                // Build timeline blocks (sessions + gaps)
                val blocks = buildTimelineBlocks(entries, _uiState.value.dayStartHour, _uiState.value.dayEndHour)

                // Calculate total effective time
                val totalTime = entries.fold(Duration.ZERO) { acc, e -> acc + e.effectiveDuration }

                _uiState.update {
                    it.copy(
                        timelineBlocks = blocks,
                        sessionEntries = entries,
                        totalTime = totalTime,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to load timeline", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Export the timeline as plain text and copy to clipboard.
     */
    fun exportTimeline() {
        val state = _uiState.value
        if (state.sessionEntries.isEmpty()) return

        val sb = StringBuilder()
        val dateStr = state.selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        sb.appendLine("Timeline - $dateStr")
        sb.appendLine("=".repeat(50))
        sb.appendLine()

        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        for (entry in state.sessionEntries) {
            val start = entry.startTime.format(timeFormat)
            val end = entry.endTime.format(timeFormat)
            val duration = DailyReportGenerator.formatDuration(entry.effectiveDuration)
            sb.appendLine("$start - $end  ${entry.task.title}  ($duration)")
        }

        sb.appendLine()
        sb.appendLine("Total: ${DailyReportGenerator.formatDuration(state.totalTime)}")

        val success = ClipboardService.copyToClipboard(sb.toString())
        if (success) {
            _uiState.update { it.copy(snackbarMessage = I18n.t("reports.copied")) }
        }
    }

    // -- Helpers --

    /**
     * Build timeline blocks including session blocks and gap blocks between them.
     */
    internal fun buildTimelineBlocks(
        entries: List<TimelineSessionEntry>,
        dayStartHour: Int,
        dayEndHour: Int,
    ): List<TimelineBlock> {
        if (entries.isEmpty()) return emptyList()

        val blocks = mutableListOf<TimelineBlock>()
        var currentHour = dayStartHour.toDouble()

        for (entry in entries) {
            val startHour = entry.startTime.hour + entry.startTime.minute / 60.0
            val endHour = entry.endTime.hour + entry.endTime.minute / 60.0

            // Clamp to day range
            val clampedStart = startHour.coerceIn(dayStartHour.toDouble(), dayEndHour.toDouble())
            val clampedEnd = endHour.coerceIn(dayStartHour.toDouble(), dayEndHour.toDouble())

            if (clampedStart <= clampedEnd && clampedEnd > dayStartHour.toDouble()) {
                // Add gap before this session if needed
                if (clampedStart > currentHour + 0.01) {
                    blocks.add(
                        TimelineBlock(
                            startHour = currentHour,
                            endHour = clampedStart,
                            taskTitle = null,
                            category = null,
                            duration = Duration.ofMinutes(((clampedStart - currentHour) * 60).toLong()),
                            isGap = true,
                        )
                    )
                }

                // Add the session block
                blocks.add(
                    TimelineBlock(
                        startHour = clampedStart,
                        endHour = clampedEnd,
                        taskTitle = entry.task.title,
                        category = entry.task.category,
                        duration = entry.effectiveDuration,
                        isGap = false,
                        sessionId = entry.session.id,
                    )
                )

                currentHour = clampedEnd
            }
        }

        // Add trailing gap to end of day
        if (currentHour < dayEndHour.toDouble() - 0.01) {
            blocks.add(
                TimelineBlock(
                    startHour = currentHour,
                    endHour = dayEndHour.toDouble(),
                    taskTitle = null,
                    category = null,
                    duration = Duration.ofMinutes(((dayEndHour - currentHour) * 60).toLong()),
                    isGap = true,
                )
            )
        }

        return blocks
    }

    // -- Utility --

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun dispose() {
        scope.cancel()
    }
}
