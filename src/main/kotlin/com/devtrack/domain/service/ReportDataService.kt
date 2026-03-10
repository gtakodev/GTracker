package com.devtrack.domain.service

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

/**
 * Service that aggregates session data into report structures (P3.1.2).
 * Provides daily, weekly, and monthly aggregated data for report generators.
 */
class ReportDataService(
    private val taskRepository: TaskRepository,
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val timeCalculator: TimeCalculator,
    private val jiraTicketParser: JiraTicketParser,
) {
    private val logger = LoggerFactory.getLogger(ReportDataService::class.java)

    /**
     * Get aggregated report data for a single day.
     */
    suspend fun getDailyData(date: LocalDate): DailyReportData {
        val sessions = sessionRepository.findByDate(date)
        if (sessions.isEmpty()) {
            return DailyReportData(
                date = date,
                ticketDurations = emptyMap(),
                noTicketDurations = emptyMap(),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ZERO,
            )
        }

        // Load events for all sessions
        val eventsMap = sessions.associate { session ->
            session.id to eventRepository.findBySessionId(session.id)
        }

        // Group sessions by taskId and compute per-task durations
        val sessionsByTask = sessions.groupBy { it.taskId }
        val ticketDurations = mutableMapOf<String, Duration>()
        val noTicketDurations = mutableMapOf<TaskCategory, Duration>()
        val ticketDescriptions = mutableMapOf<String, String>()
        var totalDuration = Duration.ZERO

        for ((taskId, taskSessions) in sessionsByTask) {
            val task = taskRepository.findById(taskId) ?: continue
            val taskEventsMap = taskSessions.associate { s -> s.id to (eventsMap[s.id] ?: emptyList()) }
            val duration = timeCalculator.calculateTotalForTask(taskSessions, taskEventsMap)

            if (duration <= Duration.ZERO) continue
            totalDuration += duration

            val tickets = jiraTicketParser.extractTickets(task.title)
            if (tickets.isNotEmpty()) {
                val description = cleanTitleForReport(task.title)
                for (ticket in tickets) {
                    ticketDurations[ticket] = (ticketDurations[ticket] ?: Duration.ZERO) + duration
                    if (ticket !in ticketDescriptions) {
                        ticketDescriptions[ticket] = description
                    }
                }
            } else {
                noTicketDurations[task.category] =
                    (noTicketDurations[task.category] ?: Duration.ZERO) + duration
            }
        }

        return DailyReportData(
            date = date,
            ticketDurations = ticketDurations,
            noTicketDurations = noTicketDurations,
            ticketDescriptions = ticketDescriptions,
            totalDuration = totalDuration,
        )
    }

    /**
     * Get aggregated report data for a week (Monday to Friday).
     *
     * @param weekStart the Monday of the week
     */
    suspend fun getWeeklyData(weekStart: LocalDate): WeeklyReportData {
        // Ensure weekStart is a Monday
        val monday = if (weekStart.dayOfWeek == DayOfWeek.MONDAY) weekStart
        else weekStart.with(DayOfWeek.MONDAY)
        val friday = monday.plusDays(4)

        val dailyDataList = (0L..4L).map { offset ->
            getDailyData(monday.plusDays(offset))
        }

        // Collect all tickets across the week
        val allTickets = dailyDataList
            .flatMap { it.ticketDurations.keys }
            .distinct()
            .sorted()

        // Merge ticket descriptions
        val ticketDescriptions = mutableMapOf<String, String>()
        dailyDataList.forEach { day ->
            ticketDescriptions.putAll(day.ticketDescriptions)
        }

        val totalDuration = dailyDataList.fold(Duration.ZERO) { acc, d -> acc + d.totalDuration }

        return WeeklyReportData(
            weekStart = monday,
            weekEnd = friday,
            dailyData = dailyDataList,
            ticketDescriptions = ticketDescriptions,
            allTickets = allTickets,
            totalDuration = totalDuration,
        )
    }

    /**
     * Get aggregated report data for a month.
     */
    suspend fun getMonthlyData(year: Int, month: Int): MonthlyReportData {
        val yearMonth = YearMonth.of(year, month)
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        // Fetch all sessions for the month in one query
        val sessions = sessionRepository.findByDateRange(firstDay, lastDay)

        // Group sessions by date
        val sessionsByDate = sessions.groupBy { it.date }

        // Build daily data for each day that has sessions
        val dailyDataMap = mutableMapOf<LocalDate, DailyReportData>()
        for ((date, dateSessions) in sessionsByDate) {
            val eventsMap = dateSessions.associate { session ->
                session.id to eventRepository.findBySessionId(session.id)
            }

            val sessionsByTask = dateSessions.groupBy { it.taskId }
            val ticketDurations = mutableMapOf<String, Duration>()
            val noTicketDurations = mutableMapOf<TaskCategory, Duration>()
            val ticketDescriptions = mutableMapOf<String, String>()
            var dayTotal = Duration.ZERO

            for ((taskId, taskSessions) in sessionsByTask) {
                val task = taskRepository.findById(taskId) ?: continue
                val taskEventsMap = taskSessions.associate { s -> s.id to (eventsMap[s.id] ?: emptyList()) }
                val duration = timeCalculator.calculateTotalForTask(taskSessions, taskEventsMap)

                if (duration <= Duration.ZERO) continue
                dayTotal += duration

                val tickets = jiraTicketParser.extractTickets(task.title)
                if (tickets.isNotEmpty()) {
                    val description = cleanTitleForReport(task.title)
                    for (ticket in tickets) {
                        ticketDurations[ticket] = (ticketDurations[ticket] ?: Duration.ZERO) + duration
                        if (ticket !in ticketDescriptions) {
                            ticketDescriptions[ticket] = description
                        }
                    }
                } else {
                    noTicketDurations[task.category] =
                        (noTicketDurations[task.category] ?: Duration.ZERO) + duration
                }
            }

            dailyDataMap[date] = DailyReportData(
                date = date,
                ticketDurations = ticketDurations,
                noTicketDurations = noTicketDurations,
                ticketDescriptions = ticketDescriptions,
                totalDuration = dayTotal,
            )
        }

        // Collect all tickets
        val allTickets = dailyDataMap.values
            .flatMap { it.ticketDurations.keys }
            .distinct()
            .sorted()

        // Merge descriptions
        val ticketDescriptions = mutableMapOf<String, String>()
        dailyDataMap.values.forEach { day ->
            ticketDescriptions.putAll(day.ticketDescriptions)
        }

        val totalDuration = dailyDataMap.values.fold(Duration.ZERO) { acc, d -> acc + d.totalDuration }

        // Count working days (Monday to Friday)
        var workingDays = 0
        var date = firstDay
        while (!date.isAfter(lastDay)) {
            val dow = date.dayOfWeek
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++
            }
            date = date.plusDays(1)
        }

        return MonthlyReportData(
            year = year,
            month = month,
            dailyData = dailyDataMap,
            allTickets = allTickets,
            ticketDescriptions = ticketDescriptions,
            totalDuration = totalDuration,
            workingDays = workingDays,
        )
    }

    /**
     * Remove Jira tickets and hashtags from a title to produce a clean description.
     */
    private fun cleanTitleForReport(title: String): String {
        var cleaned = JiraTicketParser.JIRA_TICKET_REGEX.replace(title, "")
        cleaned = JiraTicketParser.HASHTAG_REGEX.replace(cleaned, "")
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }
}
