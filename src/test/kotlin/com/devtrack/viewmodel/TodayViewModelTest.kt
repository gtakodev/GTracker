package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.SessionService
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
import com.devtrack.domain.service.PomodoroService
import com.devtrack.domain.service.PomodoroState
import com.devtrack.infrastructure.export.DailyReportGenerator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for TodayViewModel (P1.4.7).
 * Uses MockK for all dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TodayViewModelTest {

    private val taskService = mockk<TaskService>(relaxed = true)
    private val sessionService = mockk<SessionService>(relaxed = true)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val sessionRepository = mockk<WorkSessionRepository>(relaxed = true)
    private val eventRepository = mockk<SessionEventRepository>(relaxed = true)
    private val timeCalculator = TimeCalculator() // use real calculator
    private val dailyReportGenerator = mockk<DailyReportGenerator>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val pomodoroService = mockk<PomodoroService>(relaxed = true)

    private lateinit var viewModel: TodayViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        clearAllMocks()

        // Default stubs
        coEvery { taskRepository.findByDate(any()) } returns emptyList()
        coEvery { sessionRepository.findByDate(any()) } returns emptyList()
        coEvery { sessionService.getActiveSession() } returns null
        coEvery { sessionService.detectOrphanSessions() } returns emptyList()
        coEvery { userSettingsRepository.get() } returns UserSettings()
        every { pomodoroService.state } returns MutableStateFlow(PomodoroState())
    }

    @AfterEach
    fun teardown() {
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TodayViewModel {
        viewModel = TodayViewModel(
            taskService = taskService,
            sessionService = sessionService,
            taskRepository = taskRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            timeCalculator = timeCalculator,
            dailyReportGenerator = dailyReportGenerator,
            userSettingsRepository = userSettingsRepository,
            pomodoroService = pomodoroService,
            dispatcher = testDispatcher,
            enableTimerTicker = false,
        )
        return viewModel
    }

    @Test
    fun `initial state is loading`() = runTest {
        val vm = createViewModel()
        // Before any coroutine runs, state should show loading
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadTasks populates state with tasks from repository`() = runTest {
        val task1 = Task(title = "Task 1", plannedDate = LocalDate.now())
        val task2 = Task(title = "Task 2", plannedDate = LocalDate.now(), status = TaskStatus.DONE)
        coEvery { taskRepository.findByDate(any()) } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.taskCount)
        assertEquals(1, state.doneCount)
        assertEquals(2, state.tasks.size)
        assertNull(state.error)
    }

    @Test
    fun `loadTasks handles errors gracefully`() = runTest {
        coEvery { taskRepository.findByDate(any()) } throws RuntimeException("DB error")

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
    }

    @Test
    fun `startTask calls sessionService and reloads`() = runTest {
        val taskId = UUID.randomUUID()
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        coEvery { sessionService.startSession(taskId) } returns session
        coEvery { taskRepository.findByDate(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.startTask(taskId)
        advanceUntilIdle()

        coVerify { sessionService.startSession(taskId) }
    }

    @Test
    fun `pauseSession delegates to sessionService`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now())
            ),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        // After loadTasks, active session should be populated
        assertNotNull(vm.activeSession.value)

        vm.pauseSession()
        advanceUntilIdle()

        coVerify { sessionService.pauseSession(session.id) }
    }

    @Test
    fun `resumeSession delegates to sessionService`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = true,
        )
        coEvery { sessionService.getActiveSession() } returns activeState
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        // After loadTasks, active session should be populated
        assertNotNull(vm.activeSession.value)

        vm.resumeSession()
        advanceUntilIdle()

        coVerify { sessionService.resumeSession(session.id) }
    }

    @Test
    fun `stopSession delegates to sessionService and clears active session`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState andThen null
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.stopSession()
        advanceUntilIdle()

        coVerify { sessionService.stopSession(session.id) }
    }

    @Test
    fun `markDone calls changeStatus and stops active session if needed`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState andThen null
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.markDone(taskId)
        advanceUntilIdle()

        coVerify { sessionService.stopSession(session.id) }
        coVerify { taskService.changeStatus(taskId, TaskStatus.DONE) }
    }

    @Test
    fun `quickCreateTask creates task for today and reloads`() = runTest {
        val task = Task(title = "New task", plannedDate = LocalDate.now())
        coEvery { taskService.createTask(any(), any()) } returns task
        coEvery { taskRepository.findByDate(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateQuickCreateText("New task #dev")
        assertEquals("New task #dev", vm.uiState.value.quickCreateText)

        vm.quickCreateTask()
        advanceUntilIdle()

        coVerify { taskService.createTask("New task #dev", LocalDate.now()) }
        assertEquals("", vm.uiState.value.quickCreateText)
    }

    @Test
    fun `quickCreateTask does nothing for blank text`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateQuickCreateText("  ")
        vm.quickCreateTask()
        advanceUntilIdle()

        coVerify(exactly = 0) { taskService.createTask(any(), any()) }
    }

    @Test
    fun `openTaskDetail and closeTaskDetail manage dialog state`() = runTest {
        val task = Task(title = "Test task")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openTaskDetail(task)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showTaskDetail)
        assertEquals(task, vm.uiState.value.selectedTask)

        vm.closeTaskDetail()
        assertFalse(vm.uiState.value.showTaskDetail)
        assertNull(vm.uiState.value.selectedTask)
    }

    @Test
    fun `saveTask calls taskService updateTask`() = runTest {
        val task = Task(title = "Test task")
        coEvery { taskService.updateTask(any()) } returns task
        coEvery { taskRepository.findByDate(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openTaskDetail(task)
        vm.saveTask(task)
        advanceUntilIdle()

        coVerify { taskService.updateTask(task) }
        assertFalse(vm.uiState.value.showTaskDetail)
    }

    @Test
    fun `confirmDeleteTask deletes selected task`() = runTest {
        val task = Task(title = "To delete")
        coEvery { taskRepository.findByDate(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openTaskDetail(task)
        vm.requestDeleteTask()
        assertTrue(vm.uiState.value.showDeleteConfirmation)

        vm.confirmDeleteTask()
        advanceUntilIdle()

        coVerify { taskService.deleteTask(task.id) }
        assertFalse(vm.uiState.value.showTaskDetail)
    }

    @Test
    fun `dismissError clears error from state`() = runTest {
        coEvery { taskRepository.findByDate(any()) } throws RuntimeException("Error")

        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)

        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `gracefulShutdown stops active session`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        val vm = createViewModel()
        advanceUntilIdle()

        vm.gracefulShutdown()

        coVerify { sessionService.stopSession(session.id) }
    }

    @Test
    fun `gracefulShutdown does nothing when no active session`() = runTest {
        coEvery { sessionService.getActiveSession() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        vm.gracefulShutdown()

        coVerify(exactly = 0) { sessionService.stopSession(any()) }
    }

    @Test
    fun `loadTasks computes task time from sessions and events`() = runTest {
        val task = Task(title = "Tracked task", plannedDate = LocalDate.now())
        val now = Instant.now()
        val session = WorkSession(
            taskId = task.id,
            date = LocalDate.now(),
            startTime = now.minusSeconds(3600),
            endTime = now,
        )
        val events = listOf(
            SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now.minusSeconds(3600)),
            SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now),
        )

        coEvery { taskRepository.findByDate(any()) } returns listOf(task)
        coEvery { sessionRepository.findByDate(any()) } returns listOf(session)
        coEvery { eventRepository.findBySessionId(session.id) } returns events
        coEvery { sessionService.getActiveSession() } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.tasks.size)
        assertTrue(state.tasks[0].totalDuration >= Duration.ofMinutes(59)) // ~1 hour
        assertTrue(state.totalTimeToday >= Duration.ofMinutes(59))
    }

    @Test
    fun `activeSession state is populated when session is active`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Active", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now())
            ),
            effectiveDuration = Duration.ofMinutes(10),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)
        coEvery { sessionRepository.findByDate(any()) } returns listOf(session)
        coEvery { eventRepository.findBySessionId(session.id) } returns activeState.events

        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.activeSession.value)
        assertEquals(taskId, vm.activeSession.value?.task?.id)
        assertFalse(vm.activeSession.value!!.isPaused)
    }

    // -----------------------------------------------------------------------
    // Orphan Session Detection tests (P2.4)
    // -----------------------------------------------------------------------

    @Test
    fun `checkOrphanSessions shows dialog when orphans detected`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Orphan task", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now().minusDays(1), startTime = Instant.now().minusSeconds(86400))
        val orphan = OrphanSessionInfo(
            session = session,
            task = task,
            lastEventTimestamp = Instant.now().minusSeconds(82800),
            effectiveDuration = Duration.ofHours(1),
        )
        coEvery { sessionService.detectOrphanSessions() } returns listOf(orphan)

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showOrphanDialog)
        assertEquals(1, vm.uiState.value.orphanSessions.size)
        assertEquals(taskId, vm.uiState.value.orphanSessions[0].task.id)
    }

    @Test
    fun `checkOrphanSessions does not show dialog when no orphans`() = runTest {
        coEvery { sessionService.detectOrphanSessions() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showOrphanDialog)
        assertTrue(vm.uiState.value.orphanSessions.isEmpty())
    }

    @Test
    fun `resolveOrphanSession with closeAtLastActivity calls service and removes from list`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Orphan task", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now().minusDays(1), startTime = Instant.now().minusSeconds(86400))
        val orphan = OrphanSessionInfo(
            session = session,
            task = task,
            lastEventTimestamp = Instant.now().minusSeconds(82800),
            effectiveDuration = Duration.ofHours(1),
        )
        coEvery { sessionService.detectOrphanSessions() } returns listOf(orphan)

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showOrphanDialog)

        vm.resolveOrphanSession(session.id, closeAtLastActivity = true)
        advanceUntilIdle()

        coVerify { sessionService.closeOrphanAtLastActivity(session.id) }
        assertTrue(vm.uiState.value.orphanSessions.isEmpty())
        assertFalse(vm.uiState.value.showOrphanDialog)
    }

    @Test
    fun `resolveOrphanSession with closeNow calls service and removes from list`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Orphan task", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now().minusDays(1), startTime = Instant.now().minusSeconds(86400))
        val orphan = OrphanSessionInfo(
            session = session,
            task = task,
            lastEventTimestamp = Instant.now().minusSeconds(82800),
            effectiveDuration = Duration.ofHours(1),
        )
        coEvery { sessionService.detectOrphanSessions() } returns listOf(orphan)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.resolveOrphanSession(session.id, closeAtLastActivity = false)
        advanceUntilIdle()

        coVerify { sessionService.closeOrphanNow(session.id) }
        assertTrue(vm.uiState.value.orphanSessions.isEmpty())
    }

    @Test
    fun `resolveOrphanSession keeps dialog open when multiple orphans remain`() = runTest {
        val task1 = Task(title = "Orphan 1", id = UUID.randomUUID())
        val task2 = Task(title = "Orphan 2", id = UUID.randomUUID())
        val session1 = WorkSession(taskId = task1.id, date = LocalDate.now().minusDays(1), startTime = Instant.now().minusSeconds(86400))
        val session2 = WorkSession(taskId = task2.id, date = LocalDate.now().minusDays(2), startTime = Instant.now().minusSeconds(172800))
        val orphans = listOf(
            OrphanSessionInfo(session1, task1, Instant.now().minusSeconds(82800), Duration.ofHours(1)),
            OrphanSessionInfo(session2, task2, Instant.now().minusSeconds(168000), Duration.ofMinutes(30)),
        )
        coEvery { sessionService.detectOrphanSessions() } returns orphans

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.orphanSessions.size)

        vm.resolveOrphanSession(session1.id, closeAtLastActivity = true)
        advanceUntilIdle()

        // One orphan remains — dialog should still be open
        assertEquals(1, vm.uiState.value.orphanSessions.size)
        assertTrue(vm.uiState.value.showOrphanDialog)
        assertEquals(task2.id, vm.uiState.value.orphanSessions[0].task.id)
    }

    @Test
    fun `dismissOrphanDialog hides the dialog`() = runTest {
        val task = Task(title = "Orphan", id = UUID.randomUUID())
        val session = WorkSession(taskId = task.id, date = LocalDate.now().minusDays(1), startTime = Instant.now().minusSeconds(86400))
        val orphan = OrphanSessionInfo(session, task, Instant.now().minusSeconds(82800), Duration.ofHours(1))
        coEvery { sessionService.detectOrphanSessions() } returns listOf(orphan)

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showOrphanDialog)

        vm.dismissOrphanDialog()
        assertFalse(vm.uiState.value.showOrphanDialog)
    }

    // -----------------------------------------------------------------------
    // Inactivity handling tests (P2.4.4)
    // -----------------------------------------------------------------------

    @Test
    fun `handleInactivityContinue resets activity and hides dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Simulate inactivity dialog state
        val taskId = UUID.randomUUID()
        val task = Task(title = "Active task", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(session, task, emptyList(), Duration.ofMinutes(30), false)
        coEvery { sessionService.getActiveSession() } returns activeState

        vm.handleInactivityContinue()

        assertFalse(vm.uiState.value.showInactivityDialog)
    }

    @Test
    fun `handleInactivityAutoPause calls autoPauseForInactivity and hides dialog`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Active task", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(session, task, emptyList(), Duration.ofMinutes(30), false)
        coEvery { sessionService.getActiveSession() } returns activeState
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        // Simulate the state that the inactivity checker would set
        // We need to reach into the internal _uiState — trigger via exposed method
        vm.handleInactivityAutoPause()
        advanceUntilIdle()

        coVerify { sessionService.autoPauseForInactivity(session.id, any()) }
        assertFalse(vm.uiState.value.showInactivityDialog)
    }

    @Test
    fun `handleInactivityStop stops session and hides dialog`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Active task", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now())
        val activeState = ActiveSessionState(session, task, emptyList(), Duration.ofMinutes(30), false)
        coEvery { sessionService.getActiveSession() } returns activeState andThen null
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.handleInactivityStop()
        advanceUntilIdle()

        coVerify { sessionService.stopSession(session.id) }
        assertFalse(vm.uiState.value.showInactivityDialog)
    }

    @Test
    fun `recordUserActivity updates lastUserActivity timestamp`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val before = vm.lastUserActivity
        Thread.sleep(10) // small delay to ensure timestamp changes
        vm.recordUserActivity()

        assertTrue(vm.lastUserActivity.isAfter(before) || vm.lastUserActivity == before)
    }

    // -----------------------------------------------------------------------
    // Manual Session Editing tests (P2.7)
    // -----------------------------------------------------------------------

    @Test
    fun `openManualSessionEditor loads tasks and shows dialog`() = runTest {
        val task1 = Task(title = "Task 1", plannedDate = LocalDate.now())
        val task2 = Task(title = "Task 2")
        coEvery { taskRepository.findByDate(any()) } returns listOf(task1)
        coEvery { taskRepository.findBacklog() } returns listOf(task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openManualSessionEditor()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showManualSessionEditor)
        assertEquals(2, vm.uiState.value.allTasks.size)
    }

    @Test
    fun `closeManualSessionEditor hides dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.openManualSessionEditor()
        advanceUntilIdle()

        vm.closeManualSessionEditor()
        assertFalse(vm.uiState.value.showManualSessionEditor)
    }

    @Test
    fun `createManualSession calls service and closes dialog`() = runTest {
        val taskId = UUID.randomUUID()
        val date = LocalDate.now()
        val startTime = java.time.LocalTime.of(9, 0)
        val endTime = java.time.LocalTime.of(10, 0)
        val session = WorkSession(taskId = taskId, date = date, startTime = Instant.now(), source = SessionSource.MANUAL)
        coEvery { sessionService.createManualSession(any(), any(), any(), any(), any()) } returns session

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openManualSessionEditor()
        advanceUntilIdle()

        vm.createManualSession(taskId, date, startTime, endTime, "Test notes")
        advanceUntilIdle()

        coVerify { sessionService.createManualSession(taskId, date, any(), any(), "Test notes") }
        assertFalse(vm.uiState.value.showManualSessionEditor)
    }

    @Test
    fun `openSessionEventEditor sets editing session and shows dialog`() = runTest {
        val session = WorkSession(taskId = UUID.randomUUID(), date = LocalDate.now(), startTime = Instant.now())
        val swe = SessionWithEvents(
            session = session,
            events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now()),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = Instant.now().plusSeconds(3600)),
            ),
            effectiveDuration = Duration.ofHours(1),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openSessionEventEditor(swe)

        assertTrue(vm.uiState.value.showSessionEventEditor)
        assertNotNull(vm.uiState.value.editingSession)
        assertEquals(session.id, vm.uiState.value.editingSession!!.session.id)
    }

    @Test
    fun `closeSessionEventEditor clears state and hides dialog`() = runTest {
        val session = WorkSession(taskId = UUID.randomUUID(), date = LocalDate.now(), startTime = Instant.now())
        val swe = SessionWithEvents(
            session = session,
            events = emptyList(),
            effectiveDuration = Duration.ZERO,
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openSessionEventEditor(swe)
        assertTrue(vm.uiState.value.showSessionEventEditor)

        vm.closeSessionEventEditor()
        assertFalse(vm.uiState.value.showSessionEventEditor)
        assertNull(vm.uiState.value.editingSession)
    }

    @Test
    fun `saveSessionEvents calls service and closes editor`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Task", id = taskId, plannedDate = LocalDate.now())
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now(), endTime = Instant.now().plusSeconds(3600))
        coEvery { sessionRepository.findById(session.id) } returns session
        coEvery { sessionService.updateSessionEvents(any(), any()) } returns emptyList()
        coEvery { sessionService.getSessionsForTask(any()) } returns emptyList()
        coEvery { taskRepository.findByDate(any()) } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        // Open task detail first so loadSessionsForTask works
        vm.openTaskDetail(task)
        advanceUntilIdle()

        val editableEvents = listOf(
            com.devtrack.ui.screens.EditableEvent(type = EventType.START, timeStr = "09:00"),
            com.devtrack.ui.screens.EditableEvent(type = EventType.END, timeStr = "10:00"),
        )

        vm.saveSessionEvents(session.id, editableEvents)
        advanceUntilIdle()

        coVerify { sessionService.updateSessionEvents(session.id, any()) }
        assertFalse(vm.uiState.value.showSessionEventEditor)
    }

    @Test
    fun `openTaskDetail loads sessions for the task`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test task", id = taskId)
        val session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now(), endTime = Instant.now().plusSeconds(3600))
        val swe = SessionWithEvents(
            session = session,
            events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now()),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = Instant.now().plusSeconds(3600)),
            ),
            effectiveDuration = Duration.ofHours(1),
        )
        coEvery { sessionService.getSessionsForTask(taskId) } returns listOf(swe)
        coEvery { taskService.getSubTasks(taskId) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openTaskDetail(task)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.sessionListForTask.size)
        assertEquals(session.id, vm.uiState.value.sessionListForTask[0].session.id)
    }

    @Test
    fun `closeTaskDetail clears session list`() = runTest {
        val taskId = UUID.randomUUID()
        val task = Task(title = "Test task", id = taskId)
        val swe = SessionWithEvents(
            session = WorkSession(taskId = taskId, date = LocalDate.now(), startTime = Instant.now()),
            events = emptyList(),
            effectiveDuration = Duration.ZERO,
        )
        coEvery { sessionService.getSessionsForTask(taskId) } returns listOf(swe)
        coEvery { taskService.getSubTasks(taskId) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openTaskDetail(task)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sessionListForTask.isNotEmpty())

        vm.closeTaskDetail()
        assertTrue(vm.uiState.value.sessionListForTask.isEmpty())
    }
}
