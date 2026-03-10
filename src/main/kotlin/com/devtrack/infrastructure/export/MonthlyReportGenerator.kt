package com.devtrack.infrastructure.export

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.MonthlyReportData
import com.devtrack.domain.model.UserSettings
import com.devtrack.domain.service.ReportDataService
import com.devtrack.domain.service.TimeCalculator
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Generates a monthly CRA (Compte Rendu d'Activite) report (P3.2.1).
 *
 * Produces a cross-table: Jira tickets as rows, days of the month as columns.
 * Values are in days/half-days according to configurable thresholds.
 */
class MonthlyReportGenerator(
    private val reportDataService: ReportDataService,
    private val timeCalculator: TimeCalculator,
    private val userSettingsRepository: UserSettingsRepository,
) : ReportGenerator {

    private val logger = LoggerFactory.getLogger(MonthlyReportGenerator::class.java)

    override suspend fun generate(period: ReportPeriod): ReportOutput {
        require(period is ReportPeriod.Month) { "MonthlyReportGenerator requires ReportPeriod.Month" }

        val data = reportDataService.getMonthlyData(period.year, period.month)
        val settings = userSettingsRepository.get()
        val markdown = renderMarkdown(data, settings)
        val plainText = renderPlainText(data, settings)

        val yearMonth = YearMonth.of(period.year, period.month)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)
        val title = "CRA - ${monthName.replaceFirstChar { it.uppercase() }} ${period.year}"

        return ReportOutput(
            title = title,
            markdownContent = markdown,
            plainTextContent = plainText,
        )
    }

    /**
     * Render the monthly CRA as a Markdown table.
     */
    internal fun renderMarkdown(data: MonthlyReportData, settings: UserSettings): String {
        val sb = StringBuilder()
        val yearMonth = YearMonth.of(data.year, data.month)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)
        val daysInMonth = yearMonth.lengthOfMonth()

        sb.appendLine("# CRA - ${monthName.replaceFirstChar { it.uppercase() }} ${data.year}")
        sb.appendLine()

        if (data.allTickets.isEmpty() && data.dailyData.values.all { it.noTicketDurations.isEmpty() }) {
            sb.appendLine("*Aucune activite enregistree ce mois.*")
            return sb.toString()
        }

        // Build header row: Ticket | 1 | 2 | 3 | ... | 31 | Total
        sb.append("| Ticket ")
        for (day in 1..daysInMonth) {
            sb.append("| $day ")
        }
        sb.appendLine("| Total |")

        // Separator row
        sb.append("|---")
        for (day in 1..daysInMonth) {
            sb.append("|---")
        }
        sb.appendLine("|---:|")

        // Ticket rows
        for (ticket in data.allTickets) {
            sb.append("| `$ticket` ")
            var ticketTotal = Duration.ZERO

            for (day in 1..daysInMonth) {
                val date = yearMonth.atDay(day)
                val dayData = data.dailyData[date]
                val duration = dayData?.ticketDurations?.get(ticket) ?: Duration.ZERO
                ticketTotal += duration

                val dayValue = roundToHalfDay(duration, settings)
                sb.append("| ${formatDayValue(dayValue)} ")
            }

            val totalValue = roundToHalfDay(ticketTotal, settings)
            sb.appendLine("| **${formatDayValue(totalValue)}** |")
        }

        // "Reunions / annexes" row for non-ticket tasks
        val hasNoTicketWork = data.dailyData.values.any { it.noTicketDurations.isNotEmpty() }
        if (hasNoTicketWork) {
            sb.append("| *Reunions/Annexes* ")
            var noTicketTotal = Duration.ZERO

            for (day in 1..daysInMonth) {
                val date = yearMonth.atDay(day)
                val dayData = data.dailyData[date]
                val duration = dayData?.noTicketDurations?.values
                    ?.fold(Duration.ZERO) { acc, d -> acc + d }
                    ?: Duration.ZERO
                noTicketTotal += duration

                val dayValue = roundToHalfDay(duration, settings)
                sb.append("| ${formatDayValue(dayValue)} ")
            }

            val totalValue = roundToHalfDay(noTicketTotal, settings)
            sb.appendLine("| **${formatDayValue(totalValue)}** |")
        }

        // Total row
        sb.append("| **Total** ")
        var grandTotal = Duration.ZERO

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val dayData = data.dailyData[date]
            val duration = dayData?.totalDuration ?: Duration.ZERO
            grandTotal += duration

            val dayValue = roundToHalfDay(duration, settings)
            sb.append("| **${formatDayValue(dayValue)}** ")
        }

        val grandTotalValue = roundToHalfDay(grandTotal, settings)
        sb.appendLine("| **${formatDayValue(grandTotalValue)}** |")

        sb.appendLine()
        sb.appendLine("*Valeurs en jours (1 jour = ${settings.hoursPerDay}h, 0.5 jour = ${settings.halfDayThreshold}h)*")

        return sb.toString()
    }

    /**
     * Render the monthly CRA as plain text (simplified).
     */
    internal fun renderPlainText(data: MonthlyReportData, settings: UserSettings): String {
        val sb = StringBuilder()
        val yearMonth = YearMonth.of(data.year, data.month)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)

        sb.appendLine("CRA - ${monthName.replaceFirstChar { it.uppercase() }} ${data.year}")
        sb.appendLine("=".repeat(50))
        sb.appendLine()

        if (data.allTickets.isEmpty() && data.dailyData.values.all { it.noTicketDurations.isEmpty() }) {
            sb.appendLine("Aucune activite enregistree ce mois.")
            return sb.toString()
        }

        for (ticket in data.allTickets) {
            var ticketTotal = Duration.ZERO
            data.dailyData.values.forEach { dayData ->
                ticketTotal += dayData.ticketDurations[ticket] ?: Duration.ZERO
            }
            val totalValue = roundToHalfDay(ticketTotal, settings)
            val desc = data.ticketDescriptions[ticket] ?: ""
            sb.appendLine("$ticket ($desc): ${formatDayValue(totalValue)} j")
        }

        val noTicketTotal = data.dailyData.values.fold(Duration.ZERO) { acc, d ->
            acc + d.noTicketDurations.values.fold(Duration.ZERO) { a, dur -> a + dur }
        }
        if (noTicketTotal > Duration.ZERO) {
            val totalValue = roundToHalfDay(noTicketTotal, settings)
            sb.appendLine("Reunions/Annexes: ${formatDayValue(totalValue)} j")
        }

        sb.appendLine()
        val grandTotalValue = roundToHalfDay(data.totalDuration, settings)
        sb.appendLine("Total: ${formatDayValue(grandTotalValue)} j")

        return sb.toString()
    }

    /**
     * Round a duration to the nearest 0.5 day using the configured thresholds (P3.2.2).
     */
    internal fun roundToHalfDay(duration: Duration, settings: UserSettings): Double {
        if (duration <= Duration.ZERO) return 0.0

        val hours = duration.toMinutes() / 60.0
        val rawDays = timeCalculator.convertToDays(duration, settings)

        // Round to nearest 0.5: 0, 0.5, 1.0, 1.5, ...
        return (Math.round(rawDays * 2.0) / 2.0)
    }

    companion object {
        /**
         * Format a day value for display. Shows nothing for 0, otherwise X or X.5.
         */
        fun formatDayValue(value: Double): String {
            if (value == 0.0) return ""
            return if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.1f", value)
            }
        }
    }
}
