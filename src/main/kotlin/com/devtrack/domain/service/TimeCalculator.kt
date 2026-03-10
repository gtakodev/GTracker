package com.devtrack.domain.service

import com.devtrack.domain.model.EventType
import com.devtrack.domain.model.SessionEvent
import com.devtrack.domain.model.UserSettings
import com.devtrack.domain.model.WorkSession
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Calculates effective work time from session events (P1.3.2).
 *
 * Effective time = sum of periods between START/RESUME and PAUSE/END.
 * Pause periods are excluded.
 */
class TimeCalculator {

    /**
     * Calculate effective work time from a list of session events.
     * Events must be ordered by timestamp ASC.
     *
     * Algorithm:
     * - Track when work starts (START or RESUME event)
     * - Track when work stops (PAUSE or END event)
     * - Sum all work periods
     * - For orphan sessions (no END), use [now] as implicit end
     */
    fun calculateEffectiveTime(events: List<SessionEvent>, now: Instant = Instant.now()): Duration {
        if (events.isEmpty()) return Duration.ZERO

        var totalDuration = Duration.ZERO
        var workStartedAt: Instant? = null

        for (event in events) {
            when (event.type) {
                EventType.START, EventType.RESUME -> {
                    if (workStartedAt == null) {
                        workStartedAt = event.timestamp
                    }
                }
                EventType.PAUSE, EventType.END -> {
                    if (workStartedAt != null) {
                        totalDuration += Duration.between(workStartedAt, event.timestamp)
                        workStartedAt = null
                    }
                }
            }
        }

        // If still working (no END after last START/RESUME), count up to now
        if (workStartedAt != null) {
            totalDuration += Duration.between(workStartedAt, now)
        }

        return totalDuration
    }

    /**
     * Calculate total effective time across multiple sessions for a task.
     *
     * @param sessions list of work sessions for the task
     * @param eventsMap map of sessionId -> list of events for that session
     * @param now current time for orphan session calculation
     */
    fun calculateTotalForTask(
        sessions: List<WorkSession>,
        eventsMap: Map<UUID, List<SessionEvent>>,
        now: Instant = Instant.now(),
    ): Duration {
        return sessions.fold(Duration.ZERO) { total, session ->
            val events = eventsMap[session.id] ?: emptyList()
            total + calculateEffectiveTime(events, now)
        }
    }

    /**
     * Calculate total effective time across multiple sessions for a task,
     * including sessions from sub-tasks.
     *
     * @param sessions list of work sessions for the parent task
     * @param subTaskSessions list of work sessions for all sub-tasks
     * @param eventsMap map of sessionId -> list of events (must include parent + sub-task sessions)
     * @param now current time for orphan session calculation
     */
    fun calculateTotalForTaskWithSubTasks(
        sessions: List<WorkSession>,
        subTaskSessions: List<WorkSession>,
        eventsMap: Map<UUID, List<SessionEvent>>,
        now: Instant = Instant.now(),
    ): Duration {
        val allSessions = sessions + subTaskSessions
        return allSessions.fold(Duration.ZERO) { total, session ->
            val events = eventsMap[session.id] ?: emptyList()
            total + calculateEffectiveTime(events, now)
        }
    }

    /**
     * Convert a duration to days based on user settings thresholds.
     *
     * - >= hoursPerDay -> 1.0 day
     * - >= halfDayThreshold -> 0.5 day
     * - Otherwise -> fractional day (hours / hoursPerDay)
     */
    fun convertToDays(duration: Duration, settings: UserSettings): Double {
        val hours = duration.toMinutes() / 60.0
        return when {
            hours >= settings.hoursPerDay -> {
                // Full days + remainder
                val fullDays = (hours / settings.hoursPerDay).toInt()
                val remainder = hours - (fullDays * settings.hoursPerDay)
                fullDays + when {
                    remainder >= settings.halfDayThreshold -> 0.5
                    remainder > 0 -> remainder / settings.hoursPerDay
                    else -> 0.0
                }
            }
            hours >= settings.halfDayThreshold -> 0.5
            else -> hours / settings.hoursPerDay
        }
    }
}
