package com.devtrack.infrastructure.export

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.WeeklyReportData
import com.devtrack.domain.model.UserSettings
import com.devtrack.domain.service.ReportDataService
import com.devtrack.domain.service.TimeCalculator
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Generates a weekly report (P3.3.1).
 *
 * Produces a table: tickets, description, time per day of the week (Mon-Fri), total.
 */
class WeeklyReportGenerator(
    private val reportDataService: ReportDataService,
    private val timeCalculator: TimeCalculator,
    private val userSettingsRepository: UserSettingsRepository,
) : ReportGenerator {

    private val logger = LoggerFactory.getLogger(WeeklyReportGenerator::class.java)

    override suspend fun generate(period: ReportPeriod): ReportOutput {
        require(period is ReportPeriod.Week) { "WeeklyReportGenerator requires ReportPeriod.Week" }

        val data = reportDataService.getWeeklyData(period.weekStart)
        val settings = userSettingsRepository.get()
        val markdown = renderMarkdown(data, settings)
        val plainText = renderPlainText(data, settings)

        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        val title = "Rapport Semaine du ${data.weekStart.format(dateFormat)} au ${data.weekEnd.format(dateFormat)}"

        return ReportOutput(
            title = title,
            markdownContent = markdown,
            plainTextContent = plainText,
        )
    }

    internal fun renderMarkdown(data: WeeklyReportData, settings: UserSettings): String {
        val sb = StringBuilder()
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

        sb.appendLine("# Rapport Semaine du ${data.weekStart.format(dateFormat)} au ${data.weekEnd.format(dateFormat)}")
        sb.appendLine()

        if (data.allTickets.isEmpty() && data.dailyData.all { it.noTicketDurations.isEmpty() }) {
            sb.appendLine("*Aucune activite enregistree cette semaine.*")
            return sb.toString()
        }

        // Day labels
        val dayNames = data.dailyData.map { dayData ->
            dayData.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH)
                .replaceFirstChar { it.uppercase() } +
                " ${dayData.date.format(dateFormat)}"
        }

        // Header
        sb.append("| Ticket | Description ")
        for (dayName in dayNames) {
            sb.append("| $dayName ")
        }
        sb.appendLine("| Total |")

        // Separator
        sb.append("|---|---")
        for (i in dayNames.indices) {
            sb.append("|---:")
        }
        sb.appendLine("|---:|")

        // Ticket rows
        for (ticket in data.allTickets) {
            val desc = data.ticketDescriptions[ticket] ?: ""
            sb.append("| `$ticket` | ${truncate(desc, 30)} ")

            var ticketTotal = Duration.ZERO
            for (dayData in data.dailyData) {
                val duration = dayData.ticketDurations[ticket] ?: Duration.ZERO
                ticketTotal += duration
                sb.append("| ${DailyReportGenerator.formatDuration(duration)} ")
            }
            sb.appendLine("| **${DailyReportGenerator.formatDuration(ticketTotal)}** |")
        }

        // Non-ticket row
        val hasNoTicket = data.dailyData.any { it.noTicketDurations.isNotEmpty() }
        if (hasNoTicket) {
            sb.append("| *Hors-ticket* | Reunions/Annexes ")
            var noTicketTotal = Duration.ZERO
            for (dayData in data.dailyData) {
                val duration = dayData.noTicketDurations.values.fold(Duration.ZERO) { acc, d -> acc + d }
                noTicketTotal += duration
                sb.append("| ${DailyReportGenerator.formatDuration(duration)} ")
            }
            sb.appendLine("| **${DailyReportGenerator.formatDuration(noTicketTotal)}** |")
        }

        // Total row
        sb.append("| **Total** | ")
        for (dayData in data.dailyData) {
            sb.append("| **${DailyReportGenerator.formatDuration(dayData.totalDuration)}** ")
        }
        sb.appendLine("| **${DailyReportGenerator.formatDuration(data.totalDuration)}** |")

        return sb.toString()
    }

    internal fun renderPlainText(data: WeeklyReportData, settings: UserSettings): String {
        val sb = StringBuilder()
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

        sb.appendLine("Rapport Semaine du ${data.weekStart.format(dateFormat)} au ${data.weekEnd.format(dateFormat)}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        if (data.allTickets.isEmpty() && data.dailyData.all { it.noTicketDurations.isEmpty() }) {
            sb.appendLine("Aucune activite enregistree cette semaine.")
            return sb.toString()
        }

        for (ticket in data.allTickets) {
            var ticketTotal = Duration.ZERO
            val dayTimes = data.dailyData.map { dayData ->
                val d = dayData.ticketDurations[ticket] ?: Duration.ZERO
                ticketTotal += d
                DailyReportGenerator.formatDuration(d)
            }
            val desc = data.ticketDescriptions[ticket] ?: ""
            sb.appendLine("$ticket ($desc)")
            sb.appendLine("  ${dayTimes.joinToString(" | ")} | Total: ${DailyReportGenerator.formatDuration(ticketTotal)}")
        }

        sb.appendLine()
        sb.appendLine("Total semaine: ${DailyReportGenerator.formatDuration(data.totalDuration)}")

        return sb.toString()
    }

    companion object {
        private fun truncate(s: String, maxLen: Int): String {
            return if (s.length <= maxLen) s else s.take(maxLen - 3) + "..."
        }
    }
}
