package com.devtrack.infrastructure.export

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.DailyReport
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.service.JiraTicketParser
import com.devtrack.domain.service.TimeCalculator
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates daily reports in Markdown format (P1.5.1, PRD F6.1).
 *
 * Retrieves all sessions for a given date, groups them by Jira ticket vs non-ticket,
 * calculates effective time, and produces a Markdown report.
 */
class DailyReportGenerator(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    private val jiraTicketParser: JiraTicketParser,
) {
    private val logger = LoggerFactory.getLogger(DailyReportGenerator::class.java)

    /**
     * Generate a [DailyReport] data object for the given date.
     */
    suspend fun generateReport(date: LocalDate): DailyReport {
        val tasks = taskRepository.findByDate(date)
        val sessions = sessionRepository.findByDate(date)

        // Build events map for all sessions
        val eventsMap = sessions.associate { session ->
            session.id to eventRepository.findBySessionId(session.id)
        }

        // Group sessions by task, compute per-task duration
        val taskDurations = tasks.map { task ->
            val taskSessions = sessions.filter { it.taskId == task.id }
            val taskEventsMap = taskSessions.associate { s -> s.id to (eventsMap[s.id] ?: emptyList()) }
            val duration = timeCalculator.calculateTotalForTask(taskSessions, taskEventsMap)
            Triple(task, jiraTicketParser.extractTickets(task.title), duration)
        }

        // Separate into ticket entries and non-ticket entries
        val ticketEntries = mutableListOf<DailyReport.TicketEntry>()
        val noTicketEntries = mutableListOf<DailyReport.NoTicketEntry>()

        for ((task, tickets, duration) in taskDurations) {
            if (duration <= Duration.ZERO) continue // skip tasks with no tracked time

            if (tickets.isNotEmpty()) {
                // Strip tickets and hashtags from title for the description
                val description = cleanTitleForReport(task.title)
                // Group by each ticket found in the task title
                for (ticket in tickets) {
                    ticketEntries.add(
                        DailyReport.TicketEntry(
                            ticket = ticket,
                            description = description,
                            duration = duration,
                        )
                    )
                }
            } else {
                noTicketEntries.add(
                    DailyReport.NoTicketEntry(
                        taskTitle = task.title,
                        category = task.category,
                        duration = duration,
                    )
                )
            }
        }

        // Aggregate ticket entries by ticket (in case multiple tasks reference the same ticket)
        val aggregatedTickets = ticketEntries
            .groupBy { it.ticket }
            .map { (ticket, entries) ->
                DailyReport.TicketEntry(
                    ticket = ticket,
                    description = entries.first().description,
                    duration = entries.fold(Duration.ZERO) { acc, e -> acc + e.duration },
                )
            }

        val totalDuration = taskDurations.fold(Duration.ZERO) { acc, (_, _, d) -> acc + d }

        return DailyReport(
            date = date,
            ticketEntries = aggregatedTickets,
            noTicketEntries = noTicketEntries,
            totalDuration = totalDuration,
        )
    }

    /**
     * Generate a Markdown string for the daily report (PRD F6.1 format).
     */
    suspend fun generateMarkdown(date: LocalDate): String {
        val report = generateReport(date)
        return renderMarkdown(report)
    }

    /**
     * Render a [DailyReport] to Markdown format.
     */
    fun renderMarkdown(report: DailyReport): String {
        val sb = StringBuilder()
        val dateStr = report.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

        sb.appendLine("# Rapport du $dateStr")
        sb.appendLine()

        // Jira Tickets section
        if (report.ticketEntries.isNotEmpty()) {
            sb.appendLine("## Tickets Jira")
            sb.appendLine("| Ticket    | Description        | Temps   |")
            sb.appendLine("|-----------|--------------------|---------|")
            for (entry in report.ticketEntries) {
                val time = formatDuration(entry.duration)
                sb.appendLine("| ${padRight(entry.ticket, 9)} | ${padRight(entry.description, 18)} | ${padRight(time, 7)} |")
            }
            sb.appendLine()
        }

        // Non-ticket tasks section
        if (report.noTicketEntries.isNotEmpty()) {
            sb.appendLine("## Taches hors-ticket")
            sb.appendLine("| Tache              | Categorie    | Temps  |")
            sb.appendLine("|--------------------|-------------|--------|")
            for (entry in report.noTicketEntries) {
                val time = formatDuration(entry.duration)
                val category = categoryLabel(entry.category)
                sb.appendLine("| ${padRight(entry.taskTitle, 18)} | ${padRight(category, 11)} | ${padRight(time, 6)} |")
            }
            sb.appendLine()
        }

        // Total
        sb.appendLine("**Total : ${formatDuration(report.totalDuration)}**")

        return sb.toString().trimEnd() + "\n"
    }

    companion object {
        /**
         * Format a Duration as `XhYYm` (e.g., "2h15m", "0h30m").
         */
        fun formatDuration(duration: Duration): String {
            val totalMinutes = duration.toMinutes()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return "${hours}h${minutes.toString().padStart(2, '0')}m"
        }

        /**
         * Get the French label for a category (used in the report).
         */
        fun categoryLabel(category: TaskCategory): String {
            return when (category) {
                TaskCategory.DEVELOPMENT -> "Developpement"
                TaskCategory.BUGFIX -> "Correction"
                TaskCategory.MEETING -> "Reunion"
                TaskCategory.REVIEW -> "Review"
                TaskCategory.DOCUMENTATION -> "Documentation"
                TaskCategory.LEARNING -> "Apprentissage"
                TaskCategory.MAINTENANCE -> "Maintenance"
                TaskCategory.SUPPORT -> "Support"
            }
        }

        private fun padRight(s: String, minLen: Int): String {
            return if (s.length >= minLen) s else s + " ".repeat(minLen - s.length)
        }
    }

    /**
     * Remove Jira tickets and hashtags from a title to produce a clean description.
     */
    private fun cleanTitleForReport(title: String): String {
        // Remove Jira tickets
        var cleaned = JiraTicketParser.JIRA_TICKET_REGEX.replace(title, "")
        // Remove hashtags
        cleaned = JiraTicketParser.HASHTAG_REGEX.replace(cleaned, "")
        // Normalize whitespace
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }
}
