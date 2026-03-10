package com.devtrack.infrastructure.export

import java.time.LocalDate
import java.time.YearMonth

/**
 * Report time period (P3.1.1).
 * Used to specify which period a report covers.
 */
sealed class ReportPeriod {
    /** Single day report. */
    data class Day(val date: LocalDate) : ReportPeriod()

    /** Weekly report (Monday to Friday). */
    data class Week(val weekStart: LocalDate) : ReportPeriod()

    /** Monthly report. */
    data class Month(val year: Int, val month: Int) : ReportPeriod() {
        val yearMonth: YearMonth get() = YearMonth.of(year, month)
    }
}

/**
 * Output of a generated report (P3.1.1).
 */
data class ReportOutput(
    val title: String,
    val markdownContent: String,
    val plainTextContent: String,
)

/**
 * Common interface for report generators (P3.1.1).
 * Each implementation produces a [ReportOutput] for a specific [ReportPeriod].
 */
interface ReportGenerator {
    /**
     * Generate a report for the given period.
     */
    suspend fun generate(period: ReportPeriod): ReportOutput
}
