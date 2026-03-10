package com.devtrack.domain.service

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.ActiveSessionState
import com.devtrack.domain.model.SessionSource
import com.devtrack.domain.model.UserSettings
import com.devtrack.domain.model.WorkSession
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for PomodoroService (P4.4.5).
 * Tests: cycle complete, interruption, custom config, phase transitions, events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PomodoroServiceTest {

    private val sessionService = mockk<SessionService>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)

    private lateinit var service: PomodoroService

    private val defaultTaskId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service = PomodoroService(sessionService, userSettingsRepository)

        // Default settings
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 25,
            pomodoroBreakMin = 5,
            pomodoroLongBreakMin = 15,
            pomodoroSessionsBeforeLong = 4,
        )

        // Default session mocks
        val session = WorkSession(taskId = defaultTaskId, date = LocalDate.now(), startTime = Instant.now())
        coEvery { sessionService.startSession(any(), any()) } returns session
        coEvery { sessionService.getActiveSession() } returns null
    }

    @AfterEach
    fun teardown() = runTest {
        // Ensure pomodoro is stopped to avoid uncompleted coroutines
        if (service.isRunning()) {
            service.stop()
        }
    }

    @Test
    fun `start initializes pomodoro in WORK phase`() = runTest {
        val taskId = UUID.randomUUID()

        service.start(taskId, this)

        val state = service.state.value
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(25 * 60L, state.totalSeconds)
        assertEquals(1, state.currentSession)
        assertEquals(4, state.sessionsBeforeLongBreak)
        assertEquals(taskId, state.taskId)
        assertEquals(0, state.completedSessions)

        coVerify { sessionService.startSession(taskId, SessionSource.POMODORO) }

        service.stop()
    }

    @Test
    fun `start loads config from UserSettings`() = runTest {
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 30,
            pomodoroBreakMin = 10,
            pomodoroLongBreakMin = 20,
            pomodoroSessionsBeforeLong = 3,
        )

        val taskId = UUID.randomUUID()
        service.start(taskId, this)

        val state = service.state.value
        assertEquals(30 * 60L, state.totalSeconds)
        assertEquals(3, state.sessionsBeforeLongBreak)

        service.stop()
    }

    @Test
    fun `stop resets state to idle`() = runTest {
        val taskId = UUID.randomUUID()
        service.start(taskId, this)

        assertTrue(service.isRunning())

        service.stop()

        val state = service.state.value
        assertEquals(PomodoroPhase.IDLE, state.phase)
        assertFalse(state.isRunning)
        assertFalse(service.isRunning())
    }

    @Test
    fun `stop during WORK phase stops the active session`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        service.start(taskId, this)

        service.stop()

        coVerify { sessionService.stopSession(sessionId) }
    }

    @Test
    fun `countdown decrements remainingSeconds`() = runTest {
        val taskId = UUID.randomUUID()
        // Use very short work time for test
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 1, // 1 minute = 60 seconds
            pomodoroBreakMin = 1,
            pomodoroLongBreakMin = 1,
            pomodoroSessionsBeforeLong = 4,
        )

        service.start(taskId, this)

        // Advance 5 seconds
        advanceTimeBy(5_000)
        runCurrent()

        val state = service.state.value
        assertTrue(state.remainingSeconds <= 55, "Expected <= 55 but was ${state.remainingSeconds}")
        assertTrue(state.remainingSeconds > 0)

        service.stop()
    }

    @Test
    fun `skipPhase during work transitions to break`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(1),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        service.start(taskId, this)

        assertEquals(PomodoroPhase.WORK, service.state.value.phase)

        service.skipPhase()

        val state = service.state.value
        assertEquals(PomodoroPhase.BREAK, state.phase)
        assertEquals(1, state.completedSessions)

        // Verify work session was stopped
        coVerify { sessionService.stopSession(sessionId) }

        service.stop()
    }

    @Test
    fun `skipPhase during break transitions to next work`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(1),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        service.start(taskId, this)

        // Skip work → break
        service.skipPhase()
        assertEquals(PomodoroPhase.BREAK, service.state.value.phase)

        // Skip break → next work
        service.skipPhase()

        val state = service.state.value
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(2, state.currentSession)

        // Verify new session was started
        coVerify(atLeast = 2) { sessionService.startSession(taskId, SessionSource.POMODORO) }

        service.stop()
    }

    @Test
    fun `long break triggered after N work sessions via skip`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(1),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        // 2 sessions before long break
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 1,
            pomodoroBreakMin = 1,
            pomodoroLongBreakMin = 2,
            pomodoroSessionsBeforeLong = 2,
        )

        service.start(taskId, this)

        // Session 1: skip work → break
        service.skipPhase()
        assertEquals(PomodoroPhase.BREAK, service.state.value.phase)

        // Skip break → work session 2
        service.skipPhase()
        assertEquals(PomodoroPhase.WORK, service.state.value.phase)
        assertEquals(2, service.state.value.currentSession)

        // Skip work session 2 → should be long break (2 sessions completed)
        service.skipPhase()

        val state = service.state.value
        assertEquals(PomodoroPhase.LONG_BREAK, state.phase)
        assertEquals(2, state.completedSessions)
        assertEquals(120L, state.totalSeconds) // 2 min long break

        service.stop()
    }

    @Test
    fun `long break transitions back to work session 1`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(1),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        // 2 sessions before long break
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 1,
            pomodoroBreakMin = 1,
            pomodoroLongBreakMin = 1,
            pomodoroSessionsBeforeLong = 2,
        )

        service.start(taskId, this)

        // Work 1 → Break → Work 2 → Long Break
        service.skipPhase() // work → break
        service.skipPhase() // break → work 2
        service.skipPhase() // work 2 → long break
        assertEquals(PomodoroPhase.LONG_BREAK, service.state.value.phase)

        // Skip long break → new cycle, work session 1
        service.skipPhase()

        val state = service.state.value
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(1, state.currentSession)
        assertEquals(0, state.completedSessions) // reset for new cycle

        service.stop()
    }

    @Test
    fun `start stops existing pomodoro before starting new one`() = runTest {
        val taskId1 = UUID.randomUUID()
        val taskId2 = UUID.randomUUID()

        service.start(taskId1, this)
        assertTrue(service.isRunning())

        service.start(taskId2, this)

        // Should be running with second task
        val state = service.state.value
        assertTrue(state.isRunning)
        assertEquals(taskId2, state.taskId)

        service.stop()
    }

    @Test
    fun `events are emitted on phase transitions`() = runTest {
        val taskId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val activeTask = com.devtrack.domain.model.Task(title = "Test", id = taskId)
        val activeSession = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = activeSession,
            task = activeTask,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(1),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        val collectedEvents = mutableListOf<PomodoroEvent>()
        val collectJob = backgroundScope.launch {
            service.events.collect { collectedEvents.add(it) }
        }

        // Let the collector coroutine start before emitting events
        runCurrent()

        // Use backgroundScope for the pomodoro timer so advanceUntilIdle/runCurrent
        // don't chase the infinite countdown loop through virtual time.
        service.start(taskId, backgroundScope)

        // Skip work → should emit WorkComplete
        service.skipPhase()
        yield() // give the collector a chance to process the buffered event
        assertTrue(collectedEvents.any { it is PomodoroEvent.WorkComplete },
            "Expected WorkComplete event but got: $collectedEvents")

        // Skip break → should emit BreakComplete
        service.skipPhase()
        yield()
        assertTrue(collectedEvents.any { it is PomodoroEvent.BreakComplete },
            "Expected BreakComplete event but got: $collectedEvents")

        // Stop → should emit PomodoroStopped
        service.stop()
        yield()
        assertTrue(collectedEvents.any { it is PomodoroEvent.PomodoroStopped },
            "Expected PomodoroStopped event but got: $collectedEvents")

        collectJob.cancel()
    }

    @Test
    fun `custom config with different session counts`() = runTest {
        coEvery { userSettingsRepository.get() } returns UserSettings(
            pomodoroWorkMin = 50,
            pomodoroBreakMin = 10,
            pomodoroLongBreakMin = 30,
            pomodoroSessionsBeforeLong = 2,
        )

        val taskId = UUID.randomUUID()
        service.start(taskId, this)

        val state = service.state.value
        assertEquals(50 * 60L, state.totalSeconds)
        assertEquals(50 * 60L, state.remainingSeconds)
        assertEquals(2, state.sessionsBeforeLongBreak)

        service.stop()
    }

    @Test
    fun `idle state reports isRunning as false`() {
        assertFalse(service.isRunning())
        val state = service.state.value
        assertEquals(PomodoroPhase.IDLE, state.phase)
        assertFalse(state.isRunning)
    }
}
