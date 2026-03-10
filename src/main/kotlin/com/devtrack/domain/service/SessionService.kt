package com.devtrack.domain.service

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.infrastructure.logging.AuditLogger
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Business service for timer/session operations (P1.3.4).
 * Manages session lifecycle: start, pause, resume, stop, switch.
 * Only one session can be active at a time.
 */
class SessionService(
    private val sessionRepository: WorkSessionRepository,
    private val eventRepository: SessionEventRepository,
    private val taskRepository: TaskRepository,
    private val timeCalculator: TimeCalculator,
    private val auditLogger: AuditLogger,
) {

    /**
     * Start a new session on a task.
     * If another session is already active, stops it first (auto-switch).
     */
    suspend fun startSession(taskId: UUID, source: SessionSource = SessionSource.TIMER): WorkSession {
        // Auto-stop any active session
        val active = getActiveSession()
        if (active != null) {
            stopSession(active.session.id)
        }

        val now = Instant.now()
        val session = WorkSession(
            taskId = taskId,
            date = LocalDate.now(),
            startTime = now,
            source = source,
        )

        sessionRepository.insert(session)

        val startEvent = SessionEvent(
            sessionId = session.id,
            type = EventType.START,
            timestamp = now,
        )
        eventRepository.insert(startEvent)

        // Set task status to IN_PROGRESS
        val task = taskRepository.findById(taskId)
        if (task != null && task.status != TaskStatus.IN_PROGRESS) {
            taskRepository.update(task.copy(status = TaskStatus.IN_PROGRESS, updatedAt = now))
        }

        auditLogger.logUserAction("START_SESSION", "SESSION", session.id.toString(),
            mapOf("taskId" to taskId.toString(), "source" to source.name))

        return session
    }

    /**
     * Pause the active session.
     */
    suspend fun pauseSession(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId) ?: return
        if (session.endTime != null) return // already closed

        val now = Instant.now()
        val pauseEvent = SessionEvent(
            sessionId = sessionId,
            type = EventType.PAUSE,
            timestamp = now,
        )
        eventRepository.insert(pauseEvent)

        // Set task status to PAUSED
        val task = taskRepository.findById(session.taskId)
        if (task != null) {
            taskRepository.update(task.copy(status = TaskStatus.PAUSED, updatedAt = now))
        }

        auditLogger.logUserAction("PAUSE_SESSION", "SESSION", sessionId.toString())
    }

    /**
     * Resume a paused session.
     */
    suspend fun resumeSession(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId) ?: return
        if (session.endTime != null) return // already closed

        val now = Instant.now()
        val resumeEvent = SessionEvent(
            sessionId = sessionId,
            type = EventType.RESUME,
            timestamp = now,
        )
        eventRepository.insert(resumeEvent)

        // Set task status back to IN_PROGRESS
        val task = taskRepository.findById(session.taskId)
        if (task != null) {
            taskRepository.update(task.copy(status = TaskStatus.IN_PROGRESS, updatedAt = now))
        }

        auditLogger.logUserAction("RESUME_SESSION", "SESSION", sessionId.toString())
    }

    /**
     * Stop a session by adding an END event and setting the endTime.
     */
    suspend fun stopSession(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId) ?: return
        if (session.endTime != null) return // already closed

        val now = Instant.now()
        val endEvent = SessionEvent(
            sessionId = sessionId,
            type = EventType.END,
            timestamp = now,
        )
        eventRepository.insert(endEvent)

        sessionRepository.update(session.copy(endTime = now))

        auditLogger.logUserAction("STOP_SESSION", "SESSION", sessionId.toString())
    }

    /**
     * Get the currently active session state, or null if no session is active.
     * An active session has no endTime.
     */
    suspend fun getActiveSession(): ActiveSessionState? {
        val orphans = sessionRepository.findOrphans()
        val activeSession = orphans.firstOrNull() ?: return null

        val task = taskRepository.findById(activeSession.taskId) ?: return null
        val events = eventRepository.findBySessionId(activeSession.id)
        val effectiveDuration = timeCalculator.calculateEffectiveTime(events)

        val isPaused = events.lastOrNull()?.type == EventType.PAUSE

        return ActiveSessionState(
            session = activeSession,
            task = task,
            events = events,
            effectiveDuration = effectiveDuration,
            isPaused = isPaused,
        )
    }

    /**
     * Switch to a different task: stop current session and start a new one.
     */
    suspend fun switchToTask(taskId: UUID, source: SessionSource = SessionSource.TIMER): WorkSession {
        return startSession(taskId, source) // startSession auto-stops the active one
    }

    /**
     * Detect orphan sessions — sessions with no endTime (P2.4.1).
     * For each orphan, retrieves the task and last event timestamp.
     * Returns an empty list if no orphan sessions exist.
     */
    suspend fun detectOrphanSessions(): List<OrphanSessionInfo> {
        val orphans = sessionRepository.findOrphans()
        return orphans.mapNotNull { session ->
            val task = taskRepository.findById(session.taskId) ?: return@mapNotNull null
            val events = eventRepository.findBySessionId(session.id)
            val lastTimestamp = events.maxByOrNull { it.timestamp }?.timestamp ?: session.startTime
            val effectiveDuration = timeCalculator.calculateEffectiveTime(events)

            OrphanSessionInfo(
                session = session,
                task = task,
                lastEventTimestamp = lastTimestamp,
                effectiveDuration = effectiveDuration,
            )
        }
    }

    /**
     * Close an orphan session at the last activity timestamp (P2.4.2).
     */
    suspend fun closeOrphanAtLastActivity(sessionId: UUID) {
        val session = sessionRepository.findById(sessionId) ?: return
        if (session.endTime != null) return

        val events = eventRepository.findBySessionId(sessionId)
        val lastTimestamp = events.maxByOrNull { it.timestamp }?.timestamp ?: session.startTime

        val endEvent = SessionEvent(
            sessionId = sessionId,
            type = EventType.END,
            timestamp = lastTimestamp,
        )
        eventRepository.insert(endEvent)
        sessionRepository.update(session.copy(endTime = lastTimestamp))

        auditLogger.logUserAction("CLOSE_ORPHAN_LAST_ACTIVITY", "SESSION", sessionId.toString(),
            mapOf("endTime" to lastTimestamp.toString()))
    }

    /**
     * Close an orphan session at the current time (P2.4.2).
     */
    suspend fun closeOrphanNow(sessionId: UUID) {
        stopSession(sessionId)
        auditLogger.logUserAction("CLOSE_ORPHAN_NOW", "SESSION", sessionId.toString())
    }

    /**
     * Insert a retroactive pause for inactivity detection (P2.4.4).
     * Creates a PAUSE event at (now - inactivityDuration) and stops the session.
     */
    suspend fun autoPauseForInactivity(sessionId: UUID, inactivityDurationMinutes: Long) {
        val session = sessionRepository.findById(sessionId) ?: return
        if (session.endTime != null) return

        val now = Instant.now()
        val pauseTimestamp = now.minusSeconds(inactivityDurationMinutes * 60)

        val pauseEvent = SessionEvent(
            sessionId = sessionId,
            type = EventType.PAUSE,
            timestamp = pauseTimestamp,
        )
        eventRepository.insert(pauseEvent)

        // Set task to PAUSED
        val task = taskRepository.findById(session.taskId)
        if (task != null) {
            taskRepository.update(task.copy(status = TaskStatus.PAUSED, updatedAt = now))
        }

        auditLogger.logUserAction("AUTO_PAUSE_INACTIVITY", "SESSION", sessionId.toString(),
            mapOf("pauseAt" to pauseTimestamp.toString(), "inactivityMin" to inactivityDurationMinutes.toString()))
    }

    /**
     * Create a manual session with pre-defined start and end times (P2.7.1).
     * Creates a closed WorkSession with START and END events.
     *
     * @param taskId the task to associate the session with
     * @param date the date of the session
     * @param startTime the session start time
     * @param endTime the session end time
     * @param notes optional session notes
     * @throws IllegalArgumentException if startTime >= endTime or task doesn't exist
     */
    suspend fun createManualSession(
        taskId: UUID,
        date: LocalDate,
        startTime: Instant,
        endTime: Instant,
        notes: String? = null,
    ): WorkSession {
        require(startTime.isBefore(endTime)) { "Start time must be before end time" }
        val task = taskRepository.findById(taskId)
            ?: throw IllegalArgumentException("Task not found: $taskId")

        val session = WorkSession(
            taskId = taskId,
            date = date,
            startTime = startTime,
            endTime = endTime,
            source = SessionSource.MANUAL,
            notes = notes,
        )
        sessionRepository.insert(session)

        val startEvent = SessionEvent(
            sessionId = session.id,
            type = EventType.START,
            timestamp = startTime,
        )
        val endEvent = SessionEvent(
            sessionId = session.id,
            type = EventType.END,
            timestamp = endTime,
        )
        eventRepository.insert(startEvent)
        eventRepository.insert(endEvent)

        auditLogger.logUserAction("CREATE_MANUAL_SESSION", "SESSION", session.id.toString(),
            mapOf("taskId" to taskId.toString(), "date" to date.toString(),
                "startTime" to startTime.toString(), "endTime" to endTime.toString()))

        return session
    }

    /**
     * Update the events of an existing session (P2.7.2).
     * Replaces all events with the provided list after validation.
     * Also updates the session's startTime and endTime based on the events.
     *
     * Validation rules:
     * - Must have at least 2 events (START and END)
     * - First event must be START, last must be END
     * - Events must be in chronological order
     * - Between START/RESUME and the next event, only PAUSE or END are valid
     * - Between PAUSE and the next event, only RESUME or END are valid
     *
     * @throws IllegalArgumentException if validation fails
     */
    suspend fun updateSessionEvents(sessionId: UUID, newEvents: List<SessionEvent>): List<SessionEvent> {
        val session = sessionRepository.findById(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        validateEventSequence(newEvents)

        // Delete existing events and insert new ones
        eventRepository.deleteBySessionId(sessionId)
        val insertedEvents = newEvents.map { event ->
            val e = event.copy(sessionId = sessionId)
            eventRepository.insert(e)
            e
        }

        // Update session start/end times based on events
        val firstEvent = insertedEvents.first()
        val lastEvent = insertedEvents.last()
        sessionRepository.update(session.copy(
            startTime = firstEvent.timestamp,
            endTime = lastEvent.timestamp,
        ))

        auditLogger.logUserAction("UPDATE_SESSION_EVENTS", "SESSION", sessionId.toString(),
            mapOf("eventCount" to newEvents.size.toString()))

        return insertedEvents
    }

    /**
     * Get all sessions for a given task, with their events and effective durations (P2.7.3).
     */
    suspend fun getSessionsForTask(taskId: UUID): List<SessionWithEvents> {
        val sessions = sessionRepository.findByTaskId(taskId)
        return sessions.map { session ->
            val events = eventRepository.findBySessionId(session.id)
            val effectiveDuration = timeCalculator.calculateEffectiveTime(events)
            SessionWithEvents(
                session = session,
                events = events,
                effectiveDuration = effectiveDuration,
            )
        }.sortedByDescending { it.session.startTime }
    }

    /**
     * Validate that a sequence of session events is coherent (P2.7.2).
     *
     * @throws IllegalArgumentException with a descriptive message if invalid
     */
    internal fun validateEventSequence(events: List<SessionEvent>) {
        require(events.size >= 2) { "Session must have at least START and END events" }
        require(events.first().type == EventType.START) { "First event must be START" }
        require(events.last().type == EventType.END) { "Last event must be END" }

        // Check chronological order
        for (i in 1 until events.size) {
            require(!events[i].timestamp.isBefore(events[i - 1].timestamp)) {
                "Events must be in chronological order: event ${i + 1} is before event $i"
            }
        }

        // Check valid state transitions
        for (i in 0 until events.size - 1) {
            val current = events[i].type
            val next = events[i + 1].type
            when (current) {
                EventType.START, EventType.RESUME -> {
                    require(next == EventType.PAUSE || next == EventType.END) {
                        "After ${current.name}, expected PAUSE or END but got ${next.name}"
                    }
                }
                EventType.PAUSE -> {
                    require(next == EventType.RESUME || next == EventType.END) {
                        "After PAUSE, expected RESUME or END but got ${next.name}"
                    }
                }
                EventType.END -> {
                    // END should only appear as the last event, already validated above
                    throw IllegalArgumentException("END event found before the last position")
                }
            }
        }
    }
}
