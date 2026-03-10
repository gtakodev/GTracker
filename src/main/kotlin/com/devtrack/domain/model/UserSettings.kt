package com.devtrack.domain.model

import java.util.UUID

/**
 * Persistent user configuration (PRD 3.6).
 * All fields have sensible defaults matching the PRD specification.
 */
data class UserSettings(
    val id: UUID = UUID.randomUUID(),
    val locale: String = "fr",
    val theme: String = "SYSTEM",
    val inactivityThresholdMin: Int = 30,
    val hoursPerDay: Double = 8.0,
    val halfDayThreshold: Double = 4.0,
    val pomodoroWorkMin: Int = 25,
    val pomodoroBreakMin: Int = 5,
    val pomodoroLongBreakMin: Int = 15,
    val pomodoroSessionsBeforeLong: Int = 4,
)
