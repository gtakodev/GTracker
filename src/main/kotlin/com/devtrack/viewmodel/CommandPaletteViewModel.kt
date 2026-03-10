package com.devtrack.viewmodel

import com.devtrack.data.repository.TaskRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.service.*
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.navigation.Screen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext

/**
 * Palette mode: whether the palette was opened for general commands or specifically for task creation.
 */
enum class PaletteMode {
    /** General mode: commands start with `/`, plain text searches tasks. */
    COMMAND,
    /** Creation mode: plain text creates a new task (opened via Ctrl+N). */
    CREATE,
}

/**
 * UI state for the Command Palette (P2.3).
 */
data class CommandPaletteUiState(
    val isVisible: Boolean = false,
    val mode: PaletteMode = PaletteMode.COMMAND,
    val input: String = "",
    val suggestions: List<PaletteSuggestion> = emptyList(),
    val selectedIndex: Int = 0,
    val feedbackMessage: String? = null,
    val isExecuting: Boolean = false,
)

/**
 * ViewModel for the Command Palette (P2.3).
 * Manages palette visibility, input parsing, suggestion generation, and command execution.
 */
class CommandPaletteViewModel(
    private val commandPaletteService: CommandPaletteService,
    private val taskService: TaskService,
    private val sessionService: SessionService,
    private val taskRepository: TaskRepository,
    private val dailyReportGenerator: DailyReportGenerator,
    private val navigationState: NavigationState,
    private val jiraAggregationService: JiraAggregationService,
    private val templateService: TemplateService,
    private val pomodoroService: PomodoroService,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(CommandPaletteViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(CommandPaletteUiState())
    val uiState: StateFlow<CommandPaletteUiState> = _uiState.asStateFlow()

    /** Emitted when a command execution requires the TodayViewModel to reload. */
    private val _reloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reloadSignal: SharedFlow<Unit> = _reloadSignal.asSharedFlow()

    // -- Palette Visibility --

    /**
     * Open the command palette in the specified mode.
     */
    fun open(mode: PaletteMode = PaletteMode.COMMAND) {
        _uiState.update {
            CommandPaletteUiState(
                isVisible = true,
                mode = mode,
                input = "",
                suggestions = if (mode == PaletteMode.COMMAND) {
                    commandPaletteService.availableCommands.map { cmd ->
                        PaletteSuggestion(
                            label = cmd.name,
                            description = cmd.description,
                        )
                    }
                } else {
                    emptyList()
                },
                selectedIndex = 0,
            )
        }
    }

    /**
     * Close the command palette and reset state.
     */
    fun close() {
        _uiState.update { CommandPaletteUiState(isVisible = false) }
    }

    // -- Input Handling --

    /**
     * Update the input text and regenerate suggestions.
     */
    fun updateInput(input: String) {
        _uiState.update { it.copy(input = input, selectedIndex = 0) }
        regenerateSuggestions(input)
    }

    private fun regenerateSuggestions(input: String) {
        scope.launch {
            try {
                val tasks = taskRepository.findAll()
                val mode = _uiState.value.mode

                val suggestions = if (mode == PaletteMode.CREATE) {
                    // In creation mode, only show task search results and "create" option
                    if (input.isBlank()) {
                        emptyList()
                    } else {
                        generateCreateModeSuggestions(input, tasks)
                    }
                } else {
                    val baseSuggestions = commandPaletteService.generateSuggestions(input, tasks)
                    // Add template auto-completion when typing /template (P4.2.4)
                    val trimmed = input.trim().lowercase()
                    if (trimmed.startsWith("/template ")) {
                        val arg = input.trim().substringAfter(" ", "").trim()
                        val templates = templateService.findByName(arg)
                        if (templates.isNotEmpty()) {
                            templates.map { t ->
                                val durationInfo = t.defaultDurationMin?.let { " (${it} min)" } ?: ""
                                PaletteSuggestion(
                                    label = "/template ${t.title}",
                                    description = "${t.category.labelFr}$durationInfo",
                                    command = PaletteCommand.Template(t.title),
                                )
                            }
                        } else {
                            baseSuggestions
                        }
                    } else {
                        baseSuggestions
                    }
                }

                _uiState.update { it.copy(suggestions = suggestions) }
            } catch (e: Exception) {
                logger.error("Failed to generate suggestions", e)
            }
        }
    }

    private fun generateCreateModeSuggestions(input: String, tasks: List<Task>): List<PaletteSuggestion> {
        val lowerQuery = input.lowercase()
        val suggestions = mutableListOf<PaletteSuggestion>()

        // Search existing tasks
        val matching = tasks.filter { task ->
            task.title.lowercase().contains(lowerQuery) ||
                task.jiraTickets.any { it.lowercase().contains(lowerQuery) }
        }.take(5)

        matching.forEach { task ->
            val ticketInfo = if (task.jiraTickets.isNotEmpty()) {
                " (${task.jiraTickets.joinToString(", ")})"
            } else ""

            suggestions.add(
                PaletteSuggestion(
                    label = task.title + ticketInfo,
                    description = "command_palette.navigate_task",
                    command = PaletteCommand.NavigateToTask(task),
                    task = task,
                ),
            )
        }

        // Always offer to create the task
        suggestions.add(
            PaletteSuggestion(
                label = input,
                description = "command_palette.create_task",
                command = PaletteCommand.CreateTask(input),
            ),
        )

        return suggestions
    }

    // -- Keyboard Navigation --

    /**
     * Move selection up in the suggestion list.
     */
    fun moveSelectionUp() {
        _uiState.update { state ->
            val newIndex = if (state.selectedIndex > 0) state.selectedIndex - 1 else state.suggestions.lastIndex.coerceAtLeast(0)
            state.copy(selectedIndex = newIndex)
        }
    }

    /**
     * Move selection down in the suggestion list.
     */
    fun moveSelectionDown() {
        _uiState.update { state ->
            val maxIndex = state.suggestions.lastIndex.coerceAtLeast(0)
            val newIndex = if (state.selectedIndex < maxIndex) state.selectedIndex + 1 else 0
            state.copy(selectedIndex = newIndex)
        }
    }

    // -- Command Execution --

    /**
     * Execute the currently selected suggestion, or parse and execute the raw input.
     */
    fun executeSelected() {
        val state = _uiState.value
        if (state.isExecuting) return

        val suggestion = state.suggestions.getOrNull(state.selectedIndex)

        if (suggestion?.command != null) {
            // Suggestion has a direct command — execute it
            executeCommand(suggestion.command)
        } else {
            // Try parsing the raw input as a command
            val command = commandPaletteService.parseCommand(state.input)
            if (command != null) {
                executeCommand(command)
            } else if (suggestion != null && state.input.startsWith("/")) {
                // Partial command — fill the suggestion label into input for completion
                _uiState.update { it.copy(input = suggestion.label + " ") }
            }
        }
    }

    /**
     * Execute a specific suggestion by index.
     */
    fun executeSuggestion(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
        executeSelected()
    }

    /**
     * Execute a parsed palette command.
     */
    private fun executeCommand(command: PaletteCommand) {
        _uiState.update { it.copy(isExecuting = true) }

        scope.launch {
            try {
                when (command) {
                    is PaletteCommand.Start -> executeStart(command.query)
                    is PaletteCommand.Pause -> executePause()
                    is PaletteCommand.Resume -> executeResume()
                    is PaletteCommand.Done -> executeDone()
                    is PaletteCommand.Switch -> executeSwitch(command.query)
                    is PaletteCommand.Plan -> executePlan(command.query, command.date)
                    is PaletteCommand.Template -> executeTemplate(command.name)
                    is PaletteCommand.Report -> executeReport(command.period)
                    is PaletteCommand.Pomodoro -> executePomodoro(command.query)
                    is PaletteCommand.CreateTask -> executeCreateTask(command.title)
                    is PaletteCommand.NavigateToTask -> executeNavigateToTask(command.task)
                    is PaletteCommand.TicketSearch -> executeTicketSearch(command.ticket)
                }
            } catch (e: Exception) {
                logger.error("Failed to execute command", e)
                showFeedback(e.message ?: "Unknown error")
            } finally {
                _uiState.update { it.copy(isExecuting = false) }
            }
        }
    }

    /**
     * /start <query> — Find task by title/ticket, create if not found, start timer.
     */
    private suspend fun executeStart(query: String) {
        val task = findOrCreateTask(query)
        sessionService.startSession(task.id)
        showFeedbackAndClose(I18n.t("command.executed.start", task.title))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /pause — Pause the active session.
     */
    private suspend fun executePause() {
        val active = sessionService.getActiveSession()
        if (active == null) {
            showFeedback(I18n.t("command.error.no_active_session"))
            return
        }
        if (active.isPaused) {
            showFeedback(I18n.t("command.error.already_paused"))
            return
        }
        sessionService.pauseSession(active.session.id)
        showFeedbackAndClose(I18n.t("command.executed.pause"))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /resume — Resume the paused session.
     */
    private suspend fun executeResume() {
        val active = sessionService.getActiveSession()
        if (active == null) {
            showFeedback(I18n.t("command.error.no_active_session"))
            return
        }
        if (!active.isPaused) {
            showFeedback(I18n.t("command.error.not_paused"))
            return
        }
        sessionService.resumeSession(active.session.id)
        showFeedbackAndClose(I18n.t("command.executed.resume"))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /done — Stop session and mark task as DONE.
     */
    private suspend fun executeDone() {
        val active = sessionService.getActiveSession()
        if (active == null) {
            showFeedback(I18n.t("command.error.no_active_session"))
            return
        }
        sessionService.stopSession(active.session.id)
        taskService.changeStatus(active.task.id, com.devtrack.domain.model.TaskStatus.DONE)
        showFeedbackAndClose(I18n.t("command.executed.done"))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /switch <query> — Stop current session and start on a different task.
     */
    private suspend fun executeSwitch(query: String) {
        val task = findOrCreateTask(query)
        // startSession auto-stops current session
        sessionService.startSession(task.id)
        showFeedbackAndClose(I18n.t("command.executed.switch", task.title))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /plan <query> <date> — Plan a task for a specific date.
     */
    private suspend fun executePlan(query: String, date: LocalDate?) {
        val planDate = date ?: LocalDate.now()
        val task = findOrCreateTask(query)
        taskService.planTask(task.id, planDate)
        showFeedbackAndClose(I18n.t("command.executed.plan", planDate.toString()))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * /report <period> — Navigate to the Reports screen with the appropriate report type (P3.5.3).
     * Supports: "today" (daily), "week" (weekly), "month" (monthly), "standup".
     */
    private suspend fun executeReport(period: String) {
        val reportPeriod = when (period.lowercase()) {
            "", "today" -> "today"
            "week" -> "week"
            "month" -> "month"
            "standup" -> "standup"
            else -> "today"
        }
        navigationState.navigateToReports(reportPeriod)
        showFeedbackAndClose(I18n.t("command.executed.report_navigate"))
    }

    /**
     * Instantiate a template by name for today (P4.2.4).
     * If no matching template is found, shows feedback.
     */
    private suspend fun executeTemplate(name: String) {
        val task = templateService.instantiateByName(name)
        if (task != null) {
            showFeedbackAndClose(I18n.t("command.executed.template", task.title))
            _reloadSignal.tryEmit(Unit)
        } else {
            showFeedback(I18n.t("command.template.not_found", name))
        }
    }

    /**
     * /pomodoro <query> — Find a task by title/ticket and start a Pomodoro cycle (P4.4.4).
     */
    private suspend fun executePomodoro(query: String) {
        val task = findOrCreateTask(query)
        pomodoroService.start(task.id, scope)
        showFeedbackAndClose(I18n.t("command.executed.pomodoro", task.title))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * Phase 4 stub commands.
     */
    private fun executeStub(messageKey: String) {
        showFeedback(I18n.t(messageKey))
    }

    /**
     * Create a new task from the palette input.
     */
    private suspend fun executeCreateTask(title: String) {
        val task = taskService.createTask(title, LocalDate.now())
        showFeedbackAndClose(I18n.t("command.executed.start", task.title))
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * Navigate to an existing task (close palette, signal task selection).
     */
    private suspend fun executeNavigateToTask(task: Task) {
        close()
        // Signal a reload — the caller (MainLayout/DevTrackApp) can react to open task detail
        _reloadSignal.tryEmit(Unit)
    }

    /**
     * Ticket search: look up the ticket summary and display it as feedback (P3.7.3).
     */
    private suspend fun executeTicketSearch(ticket: String) {
        val summary = jiraAggregationService.getTicketSummary(ticket)
        if (summary == null) {
            showFeedback(I18n.t("command.ticket_not_found", ticket))
            return
        }

        val durationStr = DailyReportGenerator.formatDuration(summary.totalDuration)
        val lastDay = if (summary.daysWorked.isNotEmpty()) {
            summary.daysWorked.max().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } else {
            "-"
        }

        showFeedbackAndClose(
            I18n.t("command.ticket_summary", ticket, durationStr, summary.sessionCount.toString(), lastDay)
        )
    }

    // -- Helpers --

    /**
     * Find a task by title or Jira ticket. Create one if not found.
     */
    private suspend fun findOrCreateTask(query: String): Task {
        // Try to find by Jira ticket first
        val byTicket = taskRepository.findByJiraTicket(query)
        if (byTicket.isNotEmpty()) return byTicket.first()

        // Search by title
        val bySearch = taskRepository.search(query)
        val exactMatch = bySearch.firstOrNull { it.title.equals(query, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // Close match (contains)
        val closeMatch = bySearch.firstOrNull()
        if (closeMatch != null) return closeMatch

        // Create new task planned for today
        return taskService.createTask(query, LocalDate.now())
    }

    private fun showFeedback(message: String) {
        _uiState.update { it.copy(feedbackMessage = message) }
    }

    private fun showFeedbackAndClose(message: String) {
        _uiState.update {
            it.copy(
                isVisible = false,
                feedbackMessage = message,
            )
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun dispose() {
        scope.cancel()
    }
}
