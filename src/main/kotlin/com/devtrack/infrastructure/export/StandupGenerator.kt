package com.devtrack.infrastructure.export

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskStatus
import com.devtrack.domain.service.JiraTicketParser
import com.devtrack.domain.service.TimeCalculator
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates a daily standup message (P3.4.1, PRD F6.2).
 *
 * Sections:
 * - "Yesterday I worked on" (previous working day's sessions)
 * - "Today I will" (today's planned tasks)
 * - "Blockers" (placeholder)
 *
 * Handles Monday special case: "Last Friday" instead of "Yesterday".
 */
class StandupGenerator(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    private val jiraTicketParser: JiraTicketParser,
) : ReportGenerator {

    private val logger = LoggerFactory.getLogger(StandupGenerator::class.java)

    override suspend fun generate(period: ReportPeriod): ReportOutput {
        require(period is ReportPeriod.Day) { "StandupGenerator requires ReportPeriod.Day" }

        val today = period.date
        val markdown = generateStandup(today)

        return ReportOutput(
            title = "Standup - ${today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
            markdownContent = markdown,
            plainTextContent = markdown, // Standup is already simple text
        )
    }

    /**
     * Generate the standup message content.
     */
    suspend fun generateStandup(today: LocalDate): String {
        val previousWorkDay = getPreviousWorkDay(today)
        val sb = StringBuilder()

        sb.appendLine("# Standup - ${today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
        sb.appendLine()

        // Section: Yesterday (or Last Friday)
        val dayLabel = if (today.dayOfWeek == DayOfWeek.MONDAY) {
            "Vendredi dernier"
        } else {
            "Hier"
        }

        sb.appendLine("## $dayLabel j'ai travaille sur")
        sb.appendLine()

        val previousSessions = sessionRepository.findByDate(previousWorkDay)
        if (previousSessions.isEmpty()) {
            sb.appendLine("- *Aucune session enregistree*")
        } else {
            // Group sessions by task, compute duration
            val sessionsByTask = previousSessions.groupBy { it.taskId }
            val taskEntries = mutableListOf<Pair<Task, Duration>>()

            for ((taskId, taskSessions) in sessionsByTask) {
                val task = taskRepository.findById(taskId) ?: continue
                val eventsMap = taskSessions.associate { s ->
                    s.id to eventRepository.findBySessionId(s.id)
                }
                val duration = timeCalculator.calculateTotalForTask(taskSessions, eventsMap)
                if (duration > Duration.ZERO) {
                    taskEntries.add(task to duration)
                }
            }

            // Sort by duration descending
            taskEntries.sortByDescending { it.second }

            for ((task, duration) in taskEntries) {
                val tickets = jiraTicketParser.extractTickets(task.title)
                val ticketStr = if (tickets.isNotEmpty()) {
                    tickets.joinToString(", ") { "`$it`" } + " — "
                } else ""
                sb.appendLine("- ${ticketStr}${task.title} (${DailyReportGenerator.formatDuration(duration)})")
            }
        }

        sb.appendLine()

        // Section: Today I will
        sb.appendLine("## Aujourd'hui je vais")
        sb.appendLine()

        val todayTasks = taskRepository.findByDate(today)
        val pendingTasks = todayTasks.filter { it.status != TaskStatus.DONE && it.status != TaskStatus.ARCHIVED }
        if (pendingTasks.isEmpty()) {
            sb.appendLine("- *Aucune tache planifiee*")
        } else {
            for (task in pendingTasks) {
                val tickets = jiraTicketParser.extractTickets(task.title)
                val ticketStr = if (tickets.isNotEmpty()) {
                    tickets.joinToString(", ") { "`$it`" } + " — "
                } else ""
                val statusLabel = when (task.status) {
                    TaskStatus.IN_PROGRESS -> " *(en cours)*"
                    TaskStatus.PAUSED -> " *(en pause)*"
                    else -> ""
                }
                sb.appendLine("- ${ticketStr}${task.title}${statusLabel}")
            }
        }

        sb.appendLine()

        // Section: Blockers (placeholder)
        sb.appendLine("## Blocages")
        sb.appendLine()
        sb.appendLine("- *(aucun blocage signale)*")

        return sb.toString()
    }

    companion object {
        /**
         * Get the previous working day (skipping weekends).
         * Monday -> Friday, Tuesday-Friday -> previous day.
         */
        fun getPreviousWorkDay(date: LocalDate): LocalDate {
            return when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> date.minusDays(3) // Friday
                DayOfWeek.SUNDAY -> date.minusDays(2) // Friday
                DayOfWeek.SATURDAY -> date.minusDays(1) // Friday
                else -> date.minusDays(1)
            }
        }
    }
}
