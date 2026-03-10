package com.devtrack.viewmodel

import com.devtrack.data.repository.TaskRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.*
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.navigation.Screen
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for CommandPaletteViewModel (P2.3.6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandPaletteViewModelTest {

    private val commandPaletteService = CommandPaletteService(JiraTicketParser())
    private val taskService = mockk<TaskService>(relaxed = true)
    private val sessionService = mockk<SessionService>(relaxed = true)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val dailyReportGenerator = mockk<DailyReportGenerator>(relaxed = true)
    private val navigationState = NavigationState()
    private val jiraAggregationService = mockk<JiraAggregationService>(relaxed = true)
    private val templateService = mockk<TemplateService>(relaxed = true)
    private val pomodoroService = mockk<PomodoroService>(relaxed = true)

    private lateinit var viewModel: CommandPaletteViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        clearAllMocks()

        // Default stubs
        coEvery { taskRepository.findAll() } returns emptyList()
        coEvery { taskRepository.findByJiraTicket(any()) } returns emptyList()
        coEvery { taskRepository.search(any()) } returns emptyList()
        coEvery { sessionService.getActiveSession() } returns null
    }

    @AfterEach
    fun teardown() {
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CommandPaletteViewModel {
        viewModel = CommandPaletteViewModel(
            commandPaletteService = commandPaletteService,
            taskService = taskService,
            sessionService = sessionService,
            taskRepository = taskRepository,
            dailyReportGenerator = dailyReportGenerator,
            navigationState = navigationState,
            jiraAggregationService = jiraAggregationService,
            templateService = templateService,
            pomodoroService = pomodoroService,
            dispatcher = testDispatcher,
        )
        return viewModel
    }

    // -- Visibility --

    @Test
    fun `initial state is not visible`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isVisible)
    }

    @Test
    fun `open sets visible and mode to COMMAND`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        assertTrue(vm.uiState.value.isVisible)
        assertEquals(PaletteMode.COMMAND, vm.uiState.value.mode)
    }

    @Test
    fun `open with CREATE mode sets mode correctly`() {
        val vm = createViewModel()
        vm.open(PaletteMode.CREATE)
        assertTrue(vm.uiState.value.isVisible)
        assertEquals(PaletteMode.CREATE, vm.uiState.value.mode)
    }

    @Test
    fun `open in COMMAND mode shows available commands as suggestions`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        assertTrue(vm.uiState.value.suggestions.isNotEmpty())
        assertTrue(vm.uiState.value.suggestions.all { it.label.startsWith("/") })
    }

    @Test
    fun `open in CREATE mode shows no suggestions initially`() {
        val vm = createViewModel()
        vm.open(PaletteMode.CREATE)
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun `close resets state to not visible`() {
        val vm = createViewModel()
        vm.open()
        vm.close()
        assertFalse(vm.uiState.value.isVisible)
    }

    // -- Input Handling --

    @Test
    fun `updateInput updates input text`() = runTest {
        val vm = createViewModel()
        vm.open()
        vm.updateInput("/pa")
        advanceUntilIdle()
        assertEquals("/pa", vm.uiState.value.input)
    }

    @Test
    fun `updateInput resets selection index to 0`() = runTest {
        val vm = createViewModel()
        vm.open()
        vm.moveSelectionDown()
        vm.updateInput("/s")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `updateInput in COMMAND mode generates command suggestions`() = runTest {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        vm.updateInput("/pa")
        advanceUntilIdle()

        val suggestions = vm.uiState.value.suggestions
        assertTrue(suggestions.any { it.label == "/pause" })
    }

    @Test
    fun `updateInput with search query generates task suggestions`() = runTest {
        val task = Task(title = "Fix login page", category = TaskCategory.BUGFIX)
        coEvery { taskRepository.findAll() } returns listOf(task)

        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        vm.updateInput("Fix login")
        advanceUntilIdle()

        val suggestions = vm.uiState.value.suggestions
        assertTrue(suggestions.any { it.task == task })
    }

    @Test
    fun `updateInput in CREATE mode with text shows create suggestion`() = runTest {
        val vm = createViewModel()
        vm.open(PaletteMode.CREATE)
        vm.updateInput("New task")
        advanceUntilIdle()

        val suggestions = vm.uiState.value.suggestions
        assertTrue(suggestions.any { it.command is PaletteCommand.CreateTask })
    }

    // -- Keyboard Navigation --

    @Test
    fun `moveSelectionDown increments selectedIndex`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        assertEquals(0, vm.uiState.value.selectedIndex)
        vm.moveSelectionDown()
        assertEquals(1, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `moveSelectionDown wraps around to 0`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        val maxIndex = vm.uiState.value.suggestions.lastIndex
        // Move to end
        repeat(maxIndex) { vm.moveSelectionDown() }
        assertEquals(maxIndex, vm.uiState.value.selectedIndex)
        // One more should wrap
        vm.moveSelectionDown()
        assertEquals(0, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `moveSelectionUp decrements selectedIndex`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        vm.moveSelectionDown()
        vm.moveSelectionDown()
        assertEquals(2, vm.uiState.value.selectedIndex)
        vm.moveSelectionUp()
        assertEquals(1, vm.uiState.value.selectedIndex)
    }

    @Test
    fun `moveSelectionUp wraps around to last index`() {
        val vm = createViewModel()
        vm.open(PaletteMode.COMMAND)
        assertEquals(0, vm.uiState.value.selectedIndex)
        vm.moveSelectionUp()
        val maxIndex = vm.uiState.value.suggestions.lastIndex
        assertEquals(maxIndex, vm.uiState.value.selectedIndex)
    }

    // -- Command Execution --

    @Test
    fun `executeSelected with pause command calls sessionService pause`() = runTest {
        val sessionId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val task = Task(id = taskId, title = "Test task")
        val now = Instant.now()
        val session = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = now)
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = listOf(
                SessionEvent(sessionId = sessionId, type = EventType.START, timestamp = now),
            ),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/pause")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { sessionService.pauseSession(sessionId) }
    }

    @Test
    fun `executeSelected with resume command calls sessionService resume`() = runTest {
        val sessionId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val task = Task(id = taskId, title = "Test task")
        val now = Instant.now()
        val session = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = now)
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = listOf(
                SessionEvent(sessionId = sessionId, type = EventType.PAUSE, timestamp = now),
            ),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = true,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/resume")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { sessionService.resumeSession(sessionId) }
    }

    @Test
    fun `executeSelected with done command stops session and marks done`() = runTest {
        val sessionId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val task = Task(id = taskId, title = "Test task")
        val now = Instant.now()
        val session = WorkSession(id = sessionId, taskId = taskId, date = LocalDate.now(), startTime = now)
        val activeState = ActiveSessionState(
            session = session,
            task = task,
            events = emptyList(),
            effectiveDuration = Duration.ofMinutes(5),
            isPaused = false,
        )
        coEvery { sessionService.getActiveSession() } returns activeState

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/done")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { sessionService.stopSession(sessionId) }
        coVerify { taskService.changeStatus(taskId, TaskStatus.DONE) }
    }

    @Test
    fun `executeSelected with start command creates task if not found`() = runTest {
        val newTask = Task(title = "New task from palette")
        coEvery { taskService.createTask("New task from palette", LocalDate.now()) } returns newTask
        coEvery { taskRepository.findByJiraTicket(any()) } returns emptyList()
        coEvery { taskRepository.search(any()) } returns emptyList()

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/start New task from palette")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { taskService.createTask("New task from palette", LocalDate.now()) }
        coVerify { sessionService.startSession(newTask.id) }
    }

    @Test
    fun `executeSelected with start command finds task by ticket`() = runTest {
        val existingTask = Task(title = "Existing task", jiraTickets = listOf("PROJ-123"))
        coEvery { taskRepository.findByJiraTicket("PROJ-123") } returns listOf(existingTask)

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/start PROJ-123")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { sessionService.startSession(existingTask.id) }
        coVerify(exactly = 0) { taskService.createTask(any(), any()) }
    }

    @Test
    fun `executeSelected with pause shows error when no active session`() = runTest {
        coEvery { sessionService.getActiveSession() } returns null

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/pause")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        // Should still be visible (feedback shown, not closed)
        assertNotNull(vm.uiState.value.feedbackMessage)
        coVerify(exactly = 0) { sessionService.pauseSession(any()) }
    }

    @Test
    fun `executeSelected with plan command plans task for date`() = runTest {
        val existingTask = Task(title = "Plan task", jiraTickets = listOf("PROJ-456"))
        coEvery { taskRepository.findByJiraTicket("PROJ-456") } returns listOf(existingTask)

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/plan PROJ-456 today")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { taskService.planTask(existingTask.id, LocalDate.now()) }
    }

    @Test
    fun `executeSelected with switch command starts session on new task`() = runTest {
        val task = Task(title = "Switch target")
        coEvery { taskRepository.findByJiraTicket(any()) } returns emptyList()
        coEvery { taskRepository.search("Switch target") } returns listOf(task)

        val vm = createViewModel()
        vm.open()
        vm.updateInput("/switch Switch target")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        coVerify { sessionService.startSession(task.id) }
    }

    @Test
    fun `executeSelected in CREATE mode creates task`() = runTest {
        val newTask = Task(title = "Created from palette")
        coEvery { taskService.createTask("Created from palette", LocalDate.now()) } returns newTask

        val vm = createViewModel()
        vm.open(PaletteMode.CREATE)
        vm.updateInput("Created from palette")
        advanceUntilIdle()

        // Select the create suggestion
        val createIdx = vm.uiState.value.suggestions.indexOfFirst { it.command is PaletteCommand.CreateTask }
        assertTrue(createIdx >= 0)
        vm.executeSuggestion(createIdx)
        advanceUntilIdle()

        coVerify { taskService.createTask("Created from palette", LocalDate.now()) }
    }

    // -- Feedback --

    @Test
    fun `dismissFeedback clears feedback message`() {
        val vm = createViewModel()
        vm.open()
        // Manually trigger feedback
        vm.dismissFeedback()
        assertNull(vm.uiState.value.feedbackMessage)
    }

    @Test
    fun `report command with today navigates to reports screen`() = runTest {
        val vm = createViewModel()
        vm.open()
        vm.updateInput("/report today")
        advanceUntilIdle()
        vm.executeSelected()
        advanceUntilIdle()

        assertEquals(Screen.Reports, navigationState.currentScreen.value)
    }
}
