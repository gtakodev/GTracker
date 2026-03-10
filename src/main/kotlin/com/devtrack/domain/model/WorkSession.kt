package com.devtrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a period of work on a single task (PRD 3.2).
 * One task per session; switching tasks closes the current session automatically.
 * A null endTime indicates an orphan session (not properly closed).
 */
data class WorkSession(
    val id: UUID = UUID.randomUUID(),
    val taskId: UUID,
    val date: LocalDate,
    val startTime: Instant,
    val endTime: Instant? = null,
    val source: SessionSource = SessionSource.TIMER,
    val notes: String? = null,
)
