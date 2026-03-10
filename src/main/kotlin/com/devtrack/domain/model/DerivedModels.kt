package com.devtrack.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Task enriched with computed time data for display (P1.1.3).
 * Used in the Today view and other list views.
 */
data class TaskWithTime(
    val task: Task,
    val totalDuration: Duration = Duration.ZERO,
    val subTaskCount: Int = 0,
    val completedSubTaskCount: Int = 0,
    val sessionCount: Int = 0,
    val level: TaskLevel = TaskLevel.BACKLOG,
) {
    /** Sub-task completion ratio (0.0 to 1.0), or null if no sub-tasks. */
    val progress: Double?
        get() = if (subTaskCount > 0) completedSubTaskCount.toDouble() / subTaskCount else null
}

/**
 * Represents the state of the currently active timer session (P1.1.3).
 * Used by the TimerWidget and StatusBar for real-time display.
 */
data class ActiveSessionState(
    val session: WorkSession,
    val task: Task,
    val events: List<SessionEvent>,
    val effectiveDuration: Duration,
    val isPaused: Boolean,
)

/**
 * Data for a daily report export (P1.1.3, PRD F6.1).
 */
data class DailyReport(
    val date: LocalDate,
    val ticketEntries: List<TicketEntry>,
    val noTicketEntries: List<NoTicketEntry>,
    val totalDuration: Duration,
) {
    /**
     * A Jira ticket with its aggregated time.
     */
    data class TicketEntry(
        val ticket: String,
        val description: String,
        val duration: Duration,
    )

    /**
     * A task without a Jira ticket.
     */
    data class NoTicketEntry(
        val taskTitle: String,
        val category: TaskCategory,
        val duration: Duration,
    )
}

/**
 * Information about an orphan session detected at startup or during inactivity (P2.4).
 */
data class OrphanSessionInfo(
    val session: WorkSession,
    val task: Task,
    val lastEventTimestamp: Instant,
    val effectiveDuration: Duration,
)

/**
 * A work session enriched with its events and effective duration (P2.7).
 * Used in the session list view within task detail and session editors.
 */
data class SessionWithEvents(
    val session: WorkSession,
    val events: List<SessionEvent>,
    val effectiveDuration: Duration,
)
