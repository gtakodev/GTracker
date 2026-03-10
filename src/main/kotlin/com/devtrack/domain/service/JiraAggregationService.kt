package com.devtrack.domain.service

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.TicketSummary
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Service for aggregating work data by Jira ticket (P3.7.1).
 * Provides per-ticket summaries across all tasks and sessions.
 */
class JiraAggregationService(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    private val jiraTicketParser: JiraTicketParser,
) {
    private val logger = LoggerFactory.getLogger(JiraAggregationService::class.java)

    /**
     * Get a summary for a specific Jira ticket.
     * Returns null if the ticket is not found in any task.
     */
    suspend fun getTicketSummary(ticket: String): TicketSummary? {
        val tasks = taskRepository.findByJiraTicket(ticket)
        if (tasks.isEmpty()) return null

        var totalDuration = Duration.ZERO
        val daysWorked = mutableSetOf<java.time.LocalDate>()
        var sessionCount = 0

        for (task in tasks) {
            val sessions = sessionRepository.findByTaskId(task.id)
            sessionCount += sessions.size

            for (session in sessions) {
                daysWorked.add(session.date)
            }

            val eventsMap = sessions.associate { s ->
                s.id to eventRepository.findBySessionId(s.id)
            }
            totalDuration += timeCalculator.calculateTotalForTask(sessions, eventsMap)
        }

        return TicketSummary(
            ticket = ticket,
            tasks = tasks,
            totalDuration = totalDuration,
            daysWorked = daysWorked,
            sessionCount = sessionCount,
        )
    }

    /**
     * Get summaries for all known Jira tickets, sorted by total time descending.
     */
    suspend fun getAllTickets(): List<TicketSummary> {
        val allTasks = taskRepository.findAll()

        // Collect all unique tickets across all tasks
        val ticketToTasks = mutableMapOf<String, MutableList<com.devtrack.domain.model.Task>>()
        for (task in allTasks) {
            val tickets = jiraTicketParser.extractTickets(task.title)
            // Also check stored jiraTickets
            val allTickets = (tickets + task.jiraTickets).distinct()
            for (ticket in allTickets) {
                ticketToTasks.getOrPut(ticket) { mutableListOf() }.add(task)
            }
        }

        if (ticketToTasks.isEmpty()) return emptyList()

        // Build summaries
        val summaries = mutableListOf<TicketSummary>()
        for ((ticket, tasks) in ticketToTasks) {
            var totalDuration = Duration.ZERO
            val daysWorked = mutableSetOf<java.time.LocalDate>()
            var sessionCount = 0

            for (task in tasks) {
                val sessions = sessionRepository.findByTaskId(task.id)
                sessionCount += sessions.size

                for (session in sessions) {
                    daysWorked.add(session.date)
                }

                val eventsMap = sessions.associate { s ->
                    s.id to eventRepository.findBySessionId(s.id)
                }
                totalDuration += timeCalculator.calculateTotalForTask(sessions, eventsMap)
            }

            summaries.add(
                TicketSummary(
                    ticket = ticket,
                    tasks = tasks,
                    totalDuration = totalDuration,
                    daysWorked = daysWorked,
                    sessionCount = sessionCount,
                )
            )
        }

        return summaries.sortedByDescending { it.totalDuration }
    }
}
