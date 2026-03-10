package com.devtrack.viewmodel

import com.devtrack.domain.model.TicketSummary
import com.devtrack.domain.service.JiraAggregationService
import com.devtrack.infrastructure.export.*
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext

/**
 * Available report types for the Reports screen.
 */
enum class ReportType {
    DAY,
    WEEK,
    MONTH,
    STANDUP,
}

/**
 * UI state for the Reports screen (P3.5.1).
 */
data class ReportsUiState(
    val selectedType: ReportType = ReportType.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedWeekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val isGenerating: Boolean = false,
    val reportOutput: ReportOutput? = null,
    val error: String? = null,
    val snackbarMessage: String? = null,
    /** Ticket summaries for the "By ticket" section (P3.7.2). */
    val ticketSummaries: List<TicketSummary> = emptyList(),
    val isLoadingTickets: Boolean = false,
    val selectedTicket: TicketSummary? = null,
    val showTicketDetail: Boolean = false,
)

/**
 * ViewModel for the Reports screen (P3.5.1).
 * Manages report type selection, period selection, generation, and clipboard/export.
 */
class ReportsViewModel(
    private val monthlyReportGenerator: MonthlyReportGenerator,
    private val weeklyReportGenerator: WeeklyReportGenerator,
    private val standupGenerator: StandupGenerator,
    private val dailyReportGenerator: com.devtrack.infrastructure.export.DailyReportGenerator,
    private val jiraAggregationService: JiraAggregationService,
    private val navigationState: NavigationState,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(ReportsViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        generateReport()
        loadTicketSummaries()

        // Listen for report type signals from Command Palette navigation (P3.5.3)
        scope.launch {
            navigationState.reportTypeSignal.collect { periodKey ->
                val type = when (periodKey) {
                    "today" -> ReportType.DAY
                    "week" -> ReportType.WEEK
                    "month" -> ReportType.MONTH
                    "standup" -> ReportType.STANDUP
                    else -> ReportType.DAY
                }
                selectReportType(type)
            }
        }
    }

    // -- Report Type & Period Selection --

    /**
     * Select a report type and auto-generate.
     */
    fun selectReportType(type: ReportType) {
        _uiState.update { it.copy(selectedType = type, reportOutput = null) }
        generateReport()
    }

    /**
     * Select a specific date (for day and standup reports).
     */
    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedWeekStart = date.with(DayOfWeek.MONDAY),
                selectedYearMonth = YearMonth.from(date),
                reportOutput = null,
            )
        }
        generateReport()
    }

    /**
     * Select a week start date (Monday).
     */
    fun selectWeekStart(weekStart: LocalDate) {
        val monday = if (weekStart.dayOfWeek == DayOfWeek.MONDAY) weekStart
        else weekStart.with(DayOfWeek.MONDAY)
        _uiState.update { it.copy(selectedWeekStart = monday, reportOutput = null) }
        generateReport()
    }

    /**
     * Select a year-month for monthly reports.
     */
    fun selectYearMonth(yearMonth: YearMonth) {
        _uiState.update { it.copy(selectedYearMonth = yearMonth, reportOutput = null) }
        generateReport()
    }

    /**
     * Navigate to the previous period for the currently selected report type.
     */
    fun previousPeriod() {
        when (_uiState.value.selectedType) {
            ReportType.DAY, ReportType.STANDUP -> selectDate(_uiState.value.selectedDate.minusDays(1))
            ReportType.WEEK -> selectWeekStart(_uiState.value.selectedWeekStart.minusWeeks(1))
            ReportType.MONTH -> selectYearMonth(_uiState.value.selectedYearMonth.minusMonths(1))
        }
    }

    /**
     * Navigate to the next period for the currently selected report type.
     */
    fun nextPeriod() {
        when (_uiState.value.selectedType) {
            ReportType.DAY, ReportType.STANDUP -> selectDate(_uiState.value.selectedDate.plusDays(1))
            ReportType.WEEK -> selectWeekStart(_uiState.value.selectedWeekStart.plusWeeks(1))
            ReportType.MONTH -> selectYearMonth(_uiState.value.selectedYearMonth.plusMonths(1))
        }
    }

    // -- Report Generation --

    /**
     * Generate the report for the current type and period.
     */
    fun generateReport() {
        scope.launch {
            try {
                _uiState.update { it.copy(isGenerating = true, error = null) }
                val state = _uiState.value

                val output = when (state.selectedType) {
                    ReportType.DAY -> {
                        val markdown = dailyReportGenerator.generateMarkdown(state.selectedDate)
                        ReportOutput(
                            title = I18n.t("reports.daily.title"),
                            markdownContent = markdown,
                            plainTextContent = markdown,
                        )
                    }
                    ReportType.WEEK -> {
                        weeklyReportGenerator.generate(ReportPeriod.Week(state.selectedWeekStart))
                    }
                    ReportType.MONTH -> {
                        monthlyReportGenerator.generate(
                            ReportPeriod.Month(state.selectedYearMonth.year, state.selectedYearMonth.monthValue)
                        )
                    }
                    ReportType.STANDUP -> {
                        standupGenerator.generate(ReportPeriod.Day(state.selectedDate))
                    }
                }

                _uiState.update { it.copy(reportOutput = output, isGenerating = false) }
            } catch (e: Exception) {
                logger.error("Failed to generate report", e)
                _uiState.update { it.copy(isGenerating = false, error = e.message) }
            }
        }
    }

    // -- Clipboard & Export --

    /**
     * Copy the generated Markdown report to the clipboard.
     */
    fun copyToClipboard() {
        val content = _uiState.value.reportOutput?.markdownContent ?: return
        val success = ClipboardService.copyToClipboard(content)
        if (success) {
            _uiState.update { it.copy(snackbarMessage = I18n.t("reports.copied")) }
        } else {
            _uiState.update { it.copy(error = I18n.t("reports.copy_failed")) }
        }
    }

    /**
     * Export the report to a .md file.
     * Returns the markdown content for the caller to handle file saving.
     */
    fun getExportContent(): Pair<String, String>? {
        val output = _uiState.value.reportOutput ?: return null
        val filename = output.title.replace(Regex("[^a-zA-Z0-9\\- ]"), "").replace(" ", "_") + ".md"
        return filename to output.markdownContent
    }

    // -- Ticket Summaries (P3.7.2) --

    /**
     * Load all ticket summaries for the "By ticket" section.
     */
    fun loadTicketSummaries() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoadingTickets = true) }
                val summaries = jiraAggregationService.getAllTickets()
                _uiState.update { it.copy(ticketSummaries = summaries, isLoadingTickets = false) }
            } catch (e: Exception) {
                logger.error("Failed to load ticket summaries", e)
                _uiState.update { it.copy(isLoadingTickets = false) }
            }
        }
    }

    /**
     * Open the detail view for a ticket summary.
     */
    fun openTicketDetail(ticket: TicketSummary) {
        _uiState.update { it.copy(selectedTicket = ticket, showTicketDetail = true) }
    }

    /**
     * Close the ticket detail view.
     */
    fun closeTicketDetail() {
        _uiState.update { it.copy(selectedTicket = null, showTicketDetail = false) }
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
