package com.devtrack.domain.model

/**
 * Session event type (PRD 3.3).
 * Represents state transitions within a work session.
 */
enum class EventType {
    START,
    PAUSE,
    RESUME,
    END,
}
