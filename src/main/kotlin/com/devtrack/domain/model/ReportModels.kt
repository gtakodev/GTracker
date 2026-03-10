package com.devtrack.domain.model

import java.time.Duration
import java.time.LocalDate

/**
 * Aggregated data for a daily report (P3.1.2).
 */
data class DailyReportData(
    val date: LocalDate,
    /** Ticket -> total duration for that ticket on this day. */
    val ticketDurations: Map<String, Duration>,
    /** Category -> total duration for non-ticket tasks on this day. */
    val noTicketDurations: Map<TaskCategory, Duration>,
    /** Ticket -> task title/description. */
    val ticketDescriptions: Map<String, String>,
    /** Total duration for the day. */
    val totalDuration: Duration,
)

/**
 * Aggregated data for a weekly report (P3.1.2).
 */
data class WeeklyReportData(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** Per-day data for each day of the week (Monday to Friday). */
    val dailyData: List<DailyReportData>,
    /** Ticket -> description used in the weekly summary. */
    val ticketDescriptions: Map<String, String>,
    /** All unique tickets encountered in the week. */
    val allTickets: List<String>,
    /** Total duration for the week. */
    val totalDuration: Duration,
)

/**
 * Aggregated data for a monthly report / CRA (P3.1.2).
 */
data class MonthlyReportData(
    val year: Int,
    val month: Int,
    /** Per-day data for every day in the month. */
    val dailyData: Map<LocalDate, DailyReportData>,
    /** All unique tickets encountered in the month. */
    val allTickets: List<String>,
    /** Ticket -> description. */
    val ticketDescriptions: Map<String, String>,
    /** Total duration for the month. */
    val totalDuration: Duration,
    /** Number of working days in the month. */
    val workingDays: Int,
)

/**
 * Summary of all work related to a single Jira ticket (P3.7.1).
 */
data class TicketSummary(
    val ticket: String,
    val tasks: List<Task>,
    val totalDuration: Duration,
    val daysWorked: Set<LocalDate>,
    val sessionCount: Int,
)
