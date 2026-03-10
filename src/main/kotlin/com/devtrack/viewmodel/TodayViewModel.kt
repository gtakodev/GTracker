package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.SessionService
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
import com.devtrack.domain.service.PomodoroService
import com.devtrack.domain.service.PomodoroState
import com.devtrack.infrastructure.export.ClipboardService
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.i18n.I18n
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * UI state for the Today screen.
 */
data class TodayUiState(
    val tasks: List<TaskWithTime> = emptyList(),
    val totalTimeToday: Duration = Duration.ZERO,
    val isLoading: Boolean = false,
    val error: String? = null,
    val taskCount: Int = 0,
    val doneCount: Int = 0,
    val quickCreateText: String = "",
    val selectedTask: Task? = null,
    val showTaskDetail: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showExportPreview: Boolean = false,
    val exportMarkdown: String? = null,
    val snackbarMessage: String? = null,
    val backlogPeek: List<TaskWithTime> = emptyList(),
    val orphanSessions: List<OrphanSessionInfo> = emptyList(),
    val showOrphanDialog: Boolean = false,
    val showInactivityDialog: Boolean = false,
    val inactiveMinutes: Long = 0,
    val inactiveTaskTitle: String = "",
    val subTasks: List<Task> = emptyList(),
    val showManualSessionEditor: Boolean = false,
    val allTasks: List<Task> = emptyList(),
    val sessionListForTask: List<SessionWithEvents> = emptyList(),
    val editingSession: SessionWithEvents? = null,
    val showSessionEventEditor: Boolean = false,
)

/**
 * ViewModel for the Today screen (P1.4.1).
 * Manages task list, active session state, and timer updates.
 *
 * @param coroutineContext optional context override for testing (defaults to Dispatchers.Main)
 * @param enableTimerTicker whether to start the 1-second timer ticker (disable in tests)
 */
class TodayViewModel(
    private val taskService: TaskService,
    private val sessionService: SessionService,
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    private val dailyReportGenerator: DailyReportGenerator,
    private val userSettingsRepository: UserSettingsRepository,
    private val pomodoroService: PomodoroService,
    dispatcher: CoroutineContext = Dispatchers.Main,
    private val enableTimerTicker: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(TodayViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(TodayUiState(isLoading = true))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveSessionState?>(null)
    val activeSession: StateFlow<ActiveSessionState?> = _activeSession.asStateFlow()

    /** Pomodoro state exposed to the UI (P4.4.2). */
    val pomodoroState: StateFlow<PomodoroState> = pomodoroService.state

    private var timerJob: Job? = null
    private var inactivityJob: Job? = null

    /**
     * Tracks the last user input timestamp for inactivity detection (P2.4.3).
     * Updated by the UI layer when mouse/keyboard activity is detected.
     */
    @Volatile
    var lastUserActivity: Instant = Instant.now()
        private set

    init {
        loadTasks(showLoading = true)
        checkOrphanSessions()
        if (enableTimerTicker) {
            startTimerTicker()
            startInactivityChecker()
        }
    }

    /**
     * Load tasks planned for today and compute their time data.
     *
     * @param showLoading when true, sets isLoading = true so the UI shows a spinner.
     *   Only used for the initial load; background refreshes (e.g. after toggling a
     *   subtask) pass false to avoid replacing the task list with a loading indicator.
     */
    fun loadTasks(showLoading: Boolean = false) {
        scope.launch {
            try {
                if (showLoading) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }

                val today = LocalDate.now()
                val tasks = taskRepository.findByDate(today)
                val todaySessions = sessionRepository.findByDate(today)

                // Refresh active session first (needed for level calculation)
                val active = sessionService.getActiveSession()
                _activeSession.value = active

                // Build TaskWithTime for each task
                val tasksWithTime = tasks.map { task ->
                    val taskSessions = todaySessions.filter { it.taskId == task.id }
                    val eventsMap = taskSessions.associate { session ->
                        session.id to eventRepository.findBySessionId(session.id)
                    }
                    val totalDuration = timeCalculator.calculateTotalForTask(taskSessions, eventsMap)
                    val subTasks = taskRepository.findByParentId(task.id)

                    TaskWithTime(
                        task = task,
                        totalDuration = totalDuration,
                        subTasks = subTasks,
                        sessionCount = taskSessions.size,
                        subTaskCount = subTasks.size,
                        completedSubTaskCount = subTasks.count { it.status == TaskStatus.DONE },
                        level = taskService.getTaskLevel(task, active?.task?.id),
                    )
                }

                // Calculate total time for today across all tasks
                val allEventsMap = todaySessions.associate { session ->
                    session.id to eventRepository.findBySessionId(session.id)
                }
                val totalTimeToday = timeCalculator.calculateTotalForTask(todaySessions, allEventsMap)

                // Load backlog peek (first 5 unplanned tasks)
                val backlogTasks = taskRepository.findBacklog()
                val backlogPeek = backlogTasks.take(5).map { task ->
                    TaskWithTime(
                        task = task,
                        level = TaskLevel.BACKLOG,
                    )
                }

                _uiState.update {
                    it.copy(
                        tasks = tasksWithTime,
                        totalTimeToday = totalTimeToday,
                        taskCount = tasks.size,
                        doneCount = tasks.count { t -> t.status == TaskStatus.DONE },
                        isLoading = false,
                        backlogPeek = backlogPeek,
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to load tasks", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    /**
     * Start a timer session on a task.
     */
    fun startTask(taskId: UUID) {
        scope.launch {
            try {
                sessionService.startSession(taskId)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to start task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Start a Pomodoro cycle on a task (P4.4.4).
     */
    fun startPomodoro(taskId: UUID) {
        scope.launch {
            try {
                pomodoroService.start(taskId, scope)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to start Pomodoro", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Stop the current Pomodoro cycle (P4.4.4).
     */
    fun stopPomodoro() {
        scope.launch {
            try {
                pomodoroService.stop()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to stop Pomodoro", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Skip the current Pomodoro phase (P4.4.4).
     */
    fun skipPomodoroPhase() {
        scope.launch {
            try {
                pomodoroService.skipPhase()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to skip Pomodoro phase", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Pause the current session.
     */
    fun pauseSession() {
        scope.launch {
            try {
                val active = _activeSession.value ?: return@launch
                sessionService.pauseSession(active.session.id)
                refreshActiveSession()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to pause session", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Resume a paused session.
     */
    fun resumeSession() {
        scope.launch {
            try {
                val active = _activeSession.value ?: return@launch
                sessionService.resumeSession(active.session.id)
                refreshActiveSession()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to resume session", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Stop the current session.
     */
    fun stopSession() {
        scope.launch {
            try {
                val active = _activeSession.value ?: return@launch
                sessionService.stopSession(active.session.id)
                _activeSession.value = null
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to stop session", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Mark a task as done.
     */
    fun markDone(taskId: UUID) {
        scope.launch {
            try {
                // If active session is on this task, stop it first
                val active = _activeSession.value
                if (active != null && active.task.id == taskId) {
                    sessionService.stopSession(active.session.id)
                    _activeSession.value = null
                }
                taskService.changeStatus(taskId, TaskStatus.DONE)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to mark task done", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Quick-create a task for today from the title text.
     */
    fun quickCreateTask() {
        scope.launch {
            try {
                val title = _uiState.value.quickCreateText.trim()
                if (title.isBlank()) return@launch

                taskService.createTask(title, LocalDate.now())
                _uiState.update { it.copy(quickCreateText = "") }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to create task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Update the quick-create text field.
     */
    fun updateQuickCreateText(text: String) {
        _uiState.update { it.copy(quickCreateText = text) }
    }

    /**
     * Open the task detail dialog for a task. Also loads sub-tasks and sessions.
     */
    fun openTaskDetail(task: Task) {
        scope.launch {
            val subTasks = taskService.getSubTasks(task.id)
            val sessions = sessionService.getSessionsForTask(task.id)
            _uiState.update {
                it.copy(
                    selectedTask = task,
                    showTaskDetail = true,
                    subTasks = subTasks,
                    sessionListForTask = sessions,
                )
            }
        }
    }

    /**
     * Close the task detail dialog.
     */
    fun closeTaskDetail() {
        _uiState.update {
            it.copy(
                selectedTask = null,
                showTaskDetail = false,
                showDeleteConfirmation = false,
                subTasks = emptyList(),
                sessionListForTask = emptyList(),
            )
        }
    }

    /**
     * Save an edited task from the detail dialog.
     */
    fun saveTask(task: Task) {
        scope.launch {
            try {
                taskService.updateTask(task)
                closeTaskDetail()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to save task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Request delete confirmation.
     */
    fun requestDeleteTask() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    /**
     * Delete the currently selected task.
     */
    fun confirmDeleteTask() {
        scope.launch {
            try {
                val taskId = _uiState.value.selectedTask?.id ?: return@launch
                taskService.deleteTask(taskId)
                closeTaskDetail()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to delete task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Sub-task operations (P2.5) --

    /**
     * Create a sub-task for the currently selected task.
     */
    fun createSubTask(title: String) {
        scope.launch {
            try {
                val parentId = _uiState.value.selectedTask?.id ?: return@launch
                taskService.createSubTask(parentId, title)
                // Refresh sub-tasks in the dialog
                val subTasks = taskService.getSubTasks(parentId)
                _uiState.update { it.copy(subTasks = subTasks) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to create sub-task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Delete a sub-task.
     */
    fun deleteSubTask(subTaskId: UUID) {
        deleteSubTask(subTaskId, _uiState.value.selectedTask?.id)
    }

    fun deleteSubTask(subTask: Task) {
        deleteSubTask(subTask.id, subTask.parentId)
    }

    private fun deleteSubTask(subTaskId: UUID, parentIdHint: UUID?) {
        scope.launch {
            try {
                val parentId = parentIdHint ?: taskRepository.findById(subTaskId)?.parentId ?: return@launch
                taskService.deleteTask(subTaskId)
                refreshSelectedTaskSubTasks(parentId)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to delete sub-task", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Toggle a sub-task's done status.
     * Uses optimistic update for fluid UI: local state is updated immediately,
     * then persisted to DB in the background.
     */
    fun toggleSubTaskDone(subTask: Task) {
        val parentId = subTask.parentId ?: _uiState.value.selectedTask?.id ?: return
        val newStatus = if (subTask.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
        val updatedSubTask = subTask.copy(status = newStatus)

        // Optimistic update: immediately reflect in the task list
        _uiState.update { state ->
            state.copy(
                tasks = state.tasks.map { twt ->
                    if (twt.task.id == parentId) {
                        val newSubTasks = twt.subTasks.map { if (it.id == subTask.id) updatedSubTask else it }
                        val newCompleted = newSubTasks.count { it.status == TaskStatus.DONE }
                        twt.copy(
                            subTasks = newSubTasks,
                            completedSubTaskCount = newCompleted,
                        )
                    } else twt
                },
                // Also update the dialog sub-task list if open
                subTasks = if (state.selectedTask?.id == parentId) {
                    state.subTasks.map { if (it.id == subTask.id) updatedSubTask else it }
                } else state.subTasks,
            )
        }

        // Persist in the background and reconcile
        scope.launch {
            try {
                taskService.changeStatus(subTask.id, newStatus)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to toggle sub-task status", e)
                // Revert optimistic update on failure
                loadTasks()
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun refreshSelectedTaskSubTasks(parentId: UUID) {
        val selectedTaskId = _uiState.value.selectedTask?.id
        if (selectedTaskId == parentId) {
            val subTasks = taskService.getSubTasks(parentId)
            _uiState.update { it.copy(subTasks = subTasks) }
        }
    }

    /**
     * Start a timer on a sub-task.
     */
    fun startSubTask(subTaskId: UUID) {
        scope.launch {
            try {
                sessionService.startSession(subTaskId)
                closeTaskDetail()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to start sub-task timer", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Dismiss an error message.
     */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Dismiss the snackbar message.
     */
    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // -- Manual Session Editing (P2.7) --

    /**
     * Open the manual session editor dialog.
     * Loads all tasks for the task picker.
     */
    fun openManualSessionEditor() {
        scope.launch {
            try {
                val tasks = taskRepository.findByDate(LocalDate.now()) +
                    taskRepository.findBacklog()
                _uiState.update {
                    it.copy(showManualSessionEditor = true, allTasks = tasks.distinctBy { t -> t.id })
                }
            } catch (e: Exception) {
                logger.error("Failed to open manual session editor", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Close the manual session editor dialog.
     */
    fun closeManualSessionEditor() {
        _uiState.update { it.copy(showManualSessionEditor = false) }
    }

    /**
     * Create a manual session from the editor dialog (P2.7.1).
     */
    fun createManualSession(
        taskId: UUID,
        date: LocalDate,
        startTime: java.time.LocalTime,
        endTime: java.time.LocalTime,
        notes: String?,
    ) {
        scope.launch {
            try {
                val zone = java.time.ZoneId.systemDefault()
                val startInstant = date.atTime(startTime).atZone(zone).toInstant()
                val endInstant = date.atTime(endTime).atZone(zone).toInstant()

                sessionService.createManualSession(taskId, date, startInstant, endInstant, notes)
                closeManualSessionEditor()
                _uiState.update { it.copy(snackbarMessage = I18n.t("session.manual.created")) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to create manual session", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Load sessions for the currently selected task (for the detail dialog).
     */
    fun loadSessionsForTask() {
        scope.launch {
            try {
                val taskId = _uiState.value.selectedTask?.id ?: return@launch
                val sessions = sessionService.getSessionsForTask(taskId)
                _uiState.update { it.copy(sessionListForTask = sessions) }
            } catch (e: Exception) {
                logger.error("Failed to load sessions for task", e)
            }
        }
    }

    /**
     * Open the session event editor for a specific session (P2.7.2).
     */
    fun openSessionEventEditor(sessionWithEvents: SessionWithEvents) {
        _uiState.update {
            it.copy(editingSession = sessionWithEvents, showSessionEventEditor = true)
        }
    }

    /**
     * Close the session event editor.
     */
    fun closeSessionEventEditor() {
        _uiState.update { it.copy(editingSession = null, showSessionEventEditor = false) }
    }

    /**
     * Save edited session events (P2.7.2).
     * Converts EditableEvent list to SessionEvents, validates, and updates.
     */
    fun saveSessionEvents(sessionId: UUID, editableEvents: List<com.devtrack.ui.screens.EditableEvent>) {
        scope.launch {
            try {
                val session = sessionRepository.findById(sessionId) ?: return@launch
                val zone = java.time.ZoneId.systemDefault()
                val sessionDate = session.date

                val sessionEvents = editableEvents.map { editable ->
                    val parts = editable.timeStr.trim().split(":")
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val localTime = java.time.LocalTime.of(hours, minutes)
                    val timestamp = sessionDate.atTime(localTime).atZone(zone).toInstant()

                    SessionEvent(
                        id = editable.id,
                        sessionId = sessionId,
                        type = editable.type,
                        timestamp = timestamp,
                    )
                }

                sessionService.updateSessionEvents(sessionId, sessionEvents)
                closeSessionEventEditor()
                _uiState.update { it.copy(snackbarMessage = I18n.t("session.edit_events.saved")) }
                // Refresh session list in the detail dialog
                loadSessionsForTask()
                loadTasks()
            } catch (e: IllegalArgumentException) {
                logger.error("Event validation failed", e)
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                logger.error("Failed to save session events", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Plan a backlog task for today (from the backlog peek section).
     */
    fun planTaskToday(taskId: UUID) {
        scope.launch {
            try {
                taskService.planTask(taskId, LocalDate.now())
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to plan task for today", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Reorder tasks for today (P4.1.1).
     * Called after drag & drop completes with the new ordered list of task IDs.
     */
    fun reorderTasks(orderedTaskIds: List<UUID>) {
        scope.launch {
            try {
                taskService.reorderTasks(orderedTaskIds)
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to reorder tasks", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Generate and show the daily export preview dialog.
     */
    fun showExportPreview() {
        scope.launch {
            try {
                val markdown = dailyReportGenerator.generateMarkdown(LocalDate.now())
                _uiState.update { it.copy(showExportPreview = true, exportMarkdown = markdown) }
            } catch (e: Exception) {
                logger.error("Failed to generate export", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Close the export preview dialog.
     */
    fun closeExportPreview() {
        _uiState.update { it.copy(showExportPreview = false, exportMarkdown = null) }
    }

    /**
     * Copy the export markdown to the clipboard.
     */
    fun copyExportToClipboard() {
        val markdown = _uiState.value.exportMarkdown ?: return
        val success = ClipboardService.copyToClipboard(markdown)
        if (success) {
            _uiState.update { it.copy(snackbarMessage = "export.copy_success") }
        } else {
            _uiState.update { it.copy(error = "Failed to copy to clipboard") }
        }
    }

    /**
     * Record user activity for inactivity detection (P2.4.3).
     * Should be called from the UI layer on mouse/keyboard events.
     */
    fun recordUserActivity() {
        lastUserActivity = Instant.now()
    }

    /**
     * Check for orphan sessions at startup (P2.4.1).
     */
    private fun checkOrphanSessions() {
        scope.launch {
            try {
                val orphans = sessionService.detectOrphanSessions()
                if (orphans.isNotEmpty()) {
                    _uiState.update {
                        it.copy(orphanSessions = orphans, showOrphanDialog = true)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to detect orphan sessions", e)
            }
        }
    }

    /**
     * Resolve an orphan session with the chosen action (P2.4.2).
     */
    fun resolveOrphanSession(sessionId: UUID, closeAtLastActivity: Boolean) {
        scope.launch {
            try {
                if (closeAtLastActivity) {
                    sessionService.closeOrphanAtLastActivity(sessionId)
                } else {
                    sessionService.closeOrphanNow(sessionId)
                }
                // Remove this orphan from the list
                val remaining = _uiState.value.orphanSessions.filter { it.session.id != sessionId }
                _uiState.update {
                    it.copy(
                        orphanSessions = remaining,
                        showOrphanDialog = remaining.isNotEmpty(),
                    )
                }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to resolve orphan session", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Dismiss the orphan session dialog (P2.4.2).
     */
    fun dismissOrphanDialog() {
        _uiState.update { it.copy(showOrphanDialog = false) }
    }

    /**
     * Handle inactivity response: continue working (P2.4.4).
     */
    fun handleInactivityContinue() {
        lastUserActivity = Instant.now()
        _uiState.update { it.copy(showInactivityDialog = false) }
    }

    /**
     * Handle inactivity response: auto-pause with retroactive timestamp (P2.4.4).
     */
    fun handleInactivityAutoPause() {
        scope.launch {
            try {
                val active = _activeSession.value ?: return@launch
                val inactiveMin = _uiState.value.inactiveMinutes
                sessionService.autoPauseForInactivity(active.session.id, inactiveMin)
                _uiState.update { it.copy(showInactivityDialog = false) }
                refreshActiveSession()
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to auto-pause for inactivity", e)
                _uiState.update { it.copy(showInactivityDialog = false, error = e.message) }
            }
        }
    }

    /**
     * Handle inactivity response: stop the session (P2.4.4).
     */
    fun handleInactivityStop() {
        scope.launch {
            try {
                val active = _activeSession.value ?: return@launch
                sessionService.stopSession(active.session.id)
                _activeSession.value = null
                _uiState.update { it.copy(showInactivityDialog = false) }
                loadTasks()
            } catch (e: Exception) {
                logger.error("Failed to stop session on inactivity", e)
                _uiState.update { it.copy(showInactivityDialog = false, error = e.message) }
            }
        }
    }

    /**
     * Gracefully stop any active session (called on app close).
     */
    suspend fun gracefulShutdown() {
        try {
            val active = sessionService.getActiveSession()
            if (active != null) {
                sessionService.stopSession(active.session.id)
                logger.info("Stopped active session on shutdown: {}", active.session.id)
            }
        } catch (e: Exception) {
            logger.error("Error during graceful shutdown", e)
        }
    }

    /**
     * Cancel the ViewModel's coroutine scope.
     */
    fun dispose() {
        timerJob?.cancel()
        inactivityJob?.cancel()
        scope.cancel()
    }

    private fun startTimerTicker() {
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L) // tick every second
                refreshActiveSession()
            }
        }
    }

    /**
     * Periodically check for user inactivity (P2.4.3).
     * If the user has been inactive for longer than the threshold
     * and a session is active (not paused), show the inactivity dialog.
     */
    private fun startInactivityChecker() {
        inactivityJob = scope.launch {
            while (isActive) {
                delay(60_000L) // check every minute
                try {
                    val active = _activeSession.value ?: continue
                    if (active.isPaused) continue
                    if (_uiState.value.showInactivityDialog) continue

                    val settings = userSettingsRepository.get()
                    val thresholdMinutes = settings.inactivityThresholdMin.toLong()
                    val inactiveDuration = Duration.between(lastUserActivity, Instant.now())
                    val inactiveMinutes = inactiveDuration.toMinutes()

                    if (inactiveMinutes >= thresholdMinutes) {
                        _uiState.update {
                            it.copy(
                                showInactivityDialog = true,
                                inactiveMinutes = inactiveMinutes,
                                inactiveTaskTitle = active.task.title,
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error in inactivity checker", e)
                }
            }
        }
    }

    private suspend fun refreshActiveSession() {
        try {
            val active = sessionService.getActiveSession()
            _activeSession.value = active

            // Update total time as well
            if (active != null) {
                val today = LocalDate.now()
                val todaySessions = sessionRepository.findByDate(today)
                val allEventsMap = todaySessions.associate { session ->
                    session.id to eventRepository.findBySessionId(session.id)
                }
                val totalTimeToday = timeCalculator.calculateTotalForTask(todaySessions, allEventsMap)
                _uiState.update { it.copy(totalTimeToday = totalTimeToday) }
            }
        } catch (e: Exception) {
            logger.error("Failed to refresh active session", e)
        }
    }
}
