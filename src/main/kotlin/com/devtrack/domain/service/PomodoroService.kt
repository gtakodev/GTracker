package com.devtrack.domain.service

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.SessionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Phases of a Pomodoro cycle.
 */
enum class PomodoroPhase {
    IDLE,
    WORK,
    BREAK,
    LONG_BREAK,
}

/**
 * State of the Pomodoro timer exposed to the UI.
 */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val isRunning: Boolean = false,
    /** Remaining seconds in the current phase. */
    val remainingSeconds: Long = 0,
    /** Total seconds for the current phase (for progress calculation). */
    val totalSeconds: Long = 0,
    /** Current session number within the cycle (1-based). */
    val currentSession: Int = 0,
    /** Total sessions before a long break. */
    val sessionsBeforeLongBreak: Int = 4,
    /** The task ID associated with this Pomodoro (null when idle). */
    val taskId: UUID? = null,
    /** Completed work sessions count in total. */
    val completedSessions: Int = 0,
)

/**
 * Events emitted by the Pomodoro service for notifications.
 */
sealed class PomodoroEvent {
    data class WorkComplete(val sessionNumber: Int, val totalSessions: Int) : PomodoroEvent()
    data class BreakComplete(val sessionNumber: Int, val totalSessions: Int) : PomodoroEvent()
    data object LongBreakComplete : PomodoroEvent()
    data object PomodoroStopped : PomodoroEvent()
}

/**
 * Pomodoro service managing work/break cycles (P4.4.1).
 *
 * Cycle: WORK -> BREAK -> WORK -> BREAK -> ... -> WORK -> LONG_BREAK
 * After sessionsBeforeLongBreak work sessions, a long break is triggered.
 *
 * The service manages its own countdown timer and delegates actual session
 * recording to [SessionService].
 */
class PomodoroService(
    private val sessionService: SessionService,
    private val userSettingsRepository: UserSettingsRepository,
) {
    private val logger = LoggerFactory.getLogger(PomodoroService::class.java)

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PomodoroEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var scope: CoroutineScope? = null

    // Cached config
    private var workMin = 25
    private var breakMin = 5
    private var longBreakMin = 15
    private var sessionsBeforeLong = 4

    /**
     * Start a Pomodoro cycle on a task.
     * Loads configuration from UserSettings, starts the first WORK phase,
     * and creates a session via SessionService.
     */
    suspend fun start(taskId: UUID, coroutineScope: CoroutineScope) {
        // Stop any existing pomodoro
        if (_state.value.isRunning) {
            stop()
        }

        scope = coroutineScope

        // Load config
        val settings = userSettingsRepository.get()
        workMin = settings.pomodoroWorkMin
        breakMin = settings.pomodoroBreakMin
        longBreakMin = settings.pomodoroLongBreakMin
        sessionsBeforeLong = settings.pomodoroSessionsBeforeLong

        // Start first work phase
        _state.value = PomodoroState(
            phase = PomodoroPhase.WORK,
            isRunning = true,
            remainingSeconds = workMin * 60L,
            totalSeconds = workMin * 60L,
            currentSession = 1,
            sessionsBeforeLongBreak = sessionsBeforeLong,
            taskId = taskId,
            completedSessions = 0,
        )

        // Start actual timer session
        sessionService.startSession(taskId, SessionSource.POMODORO)

        startCountdown()
        logger.info("Pomodoro started for task {} ({}min work / {}min break / {}min long / {} sessions)",
            taskId, workMin, breakMin, longBreakMin, sessionsBeforeLong)
    }

    /**
     * Stop the Pomodoro cycle entirely.
     */
    suspend fun stop() {
        timerJob?.cancel()
        timerJob = null

        val currentState = _state.value
        if (currentState.phase == PomodoroPhase.WORK && currentState.taskId != null) {
            // Stop the active work session
            val active = sessionService.getActiveSession()
            if (active != null) {
                sessionService.stopSession(active.session.id)
            }
        }

        _state.value = PomodoroState()
        _events.tryEmit(PomodoroEvent.PomodoroStopped)
        logger.info("Pomodoro stopped")
    }

    /**
     * Skip the current phase (e.g., skip break to start working immediately).
     */
    suspend fun skipPhase() {
        timerJob?.cancel()
        timerJob = null
        onPhaseComplete()
    }

    /**
     * Check if the Pomodoro is currently running.
     */
    fun isRunning(): Boolean = _state.value.isRunning

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = scope?.launch {
            while (_state.value.remainingSeconds > 0 && isActive) {
                delay(1000)
                _state.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }
            if (isActive) {
                onPhaseComplete()
            }
        }
    }

    private suspend fun onPhaseComplete() {
        val current = _state.value
        when (current.phase) {
            PomodoroPhase.WORK -> {
                // Stop the work session
                val active = sessionService.getActiveSession()
                if (active != null) {
                    sessionService.stopSession(active.session.id)
                }

                val completedSessions = current.completedSessions + 1
                val isLongBreak = completedSessions >= sessionsBeforeLong

                if (isLongBreak) {
                    // Start long break
                    _state.value = current.copy(
                        phase = PomodoroPhase.LONG_BREAK,
                        remainingSeconds = longBreakMin * 60L,
                        totalSeconds = longBreakMin * 60L,
                        completedSessions = completedSessions,
                    )
                    _events.tryEmit(PomodoroEvent.WorkComplete(current.currentSession, sessionsBeforeLong))
                } else {
                    // Start regular break
                    _state.value = current.copy(
                        phase = PomodoroPhase.BREAK,
                        remainingSeconds = breakMin * 60L,
                        totalSeconds = breakMin * 60L,
                        completedSessions = completedSessions,
                    )
                    _events.tryEmit(PomodoroEvent.WorkComplete(current.currentSession, sessionsBeforeLong))
                }
                startCountdown()
            }

            PomodoroPhase.BREAK -> {
                // Start next work phase
                val nextSession = current.currentSession + 1
                _state.value = current.copy(
                    phase = PomodoroPhase.WORK,
                    remainingSeconds = workMin * 60L,
                    totalSeconds = workMin * 60L,
                    currentSession = nextSession,
                )
                _events.tryEmit(PomodoroEvent.BreakComplete(nextSession, sessionsBeforeLong))

                // Start a new work session
                current.taskId?.let { taskId ->
                    sessionService.startSession(taskId, SessionSource.POMODORO)
                }
                startCountdown()
            }

            PomodoroPhase.LONG_BREAK -> {
                // Cycle complete, reset to next cycle
                _state.value = current.copy(
                    phase = PomodoroPhase.WORK,
                    remainingSeconds = workMin * 60L,
                    totalSeconds = workMin * 60L,
                    currentSession = 1,
                    completedSessions = 0,
                )
                _events.tryEmit(PomodoroEvent.LongBreakComplete)

                // Start a new work session
                current.taskId?.let { taskId ->
                    sessionService.startSession(taskId, SessionSource.POMODORO)
                }
                startCountdown()
            }

            PomodoroPhase.IDLE -> {
                // Should not happen
            }
        }
    }
}
