package com.devtrack.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Records a state change within a work session (PRD 3.3).
 * The sequence of events is the source of truth for effective time calculation.
 *
 * Effective time = sum of periods between START/RESUME and PAUSE/END.
 */
data class SessionEvent(
    val id: UUID = UUID.randomUUID(),
    val sessionId: UUID,
    val type: EventType,
    val timestamp: Instant,
)
