package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
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
 * Unit tests for BacklogViewModel (P2.1.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacklogViewModelTest {

    private val taskService = mockk<TaskService>(relaxed = true)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val sessionRepository = mockk<WorkSessionRepository>(relaxed = true)
    private val eventRepository = mockk<SessionEventRepository>(relaxed = true)
    private val timeCalculator = TimeCalculator()

    private lateinit var viewModel: BacklogViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        clearAllMocks()

        // Default stubs
        coEvery { taskRepository.findBacklog() } returns emptyList()
        coEvery { taskRepository.findByParentId(any()) } returns emptyList()
        coEvery { sessionRepository.findByTaskId(any()) } returns emptyList()
    }

    @AfterEach
    fun teardown() {
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BacklogViewModel {
        viewModel = BacklogViewModel(
            taskService = taskService,
            taskRepository = taskRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            timeCalculator = timeCalculator,
            dispatcher = testDispatcher,
        )
        return viewModel
    }

    @Test
    fun `initial state is loading`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadTasks populates backlog tasks`() = runTest {
        val task1 = Task(title = "Backlog task 1")
        val task2 = Task(title = "Backlog task 2")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.tasks.size)
        assertEquals(2, state.filteredTasks.size)
        assertNull(state.error)
    }

    @Test
    fun `loadTasks handles errors gracefully`() = runTest {
        coEvery { taskRepository.findBacklog() } throws RuntimeException("DB error")

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
    }

    @Test
    fun `search query filters tasks`() = runTest {
        val task1 = Task(title = "Fix login bug")
        val task2 = Task(title = "Add feature")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateSearchQuery("login")
        val state = vm.uiState.value
        assertEquals(1, state.filteredTasks.size)
        assertEquals("Fix login bug", state.filteredTasks[0].task.title)
    }

    @Test
    fun `category filter works`() = runTest {
        val task1 = Task(title = "Bug", category = TaskCategory.BUGFIX)
        val task2 = Task(title = "Meeting", category = TaskCategory.MEETING)
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleCategoryFilter(TaskCategory.BUGFIX)
        val state = vm.uiState.value
        assertEquals(1, state.filteredTasks.size)
        assertEquals(TaskCategory.BUGFIX, state.filteredTasks[0].task.category)
    }

    @Test
    fun `toggle category filter off removes filter`() = runTest {
        val task1 = Task(title = "Bug", category = TaskCategory.BUGFIX)
        val task2 = Task(title = "Meeting", category = TaskCategory.MEETING)
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleCategoryFilter(TaskCategory.BUGFIX)
        assertEquals(1, vm.uiState.value.filteredTasks.size)

        vm.toggleCategoryFilter(TaskCategory.BUGFIX)
        assertEquals(2, vm.uiState.value.filteredTasks.size)
    }

    @Test
    fun `sort by title ascending`() = runTest {
        val task1 = Task(title = "Zebra task")
        val task2 = Task(title = "Alpha task")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setSortOption(BacklogSortOption.TITLE_ASC)
        val state = vm.uiState.value
        assertEquals("Alpha task", state.filteredTasks[0].task.title)
        assertEquals("Zebra task", state.filteredTasks[1].task.title)
    }

    @Test
    fun `multi-select toggle and selection`() = runTest {
        val task1 = Task(title = "Task 1")
        val task2 = Task(title = "Task 2")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isMultiSelectMode)

        vm.toggleMultiSelectMode()
        assertTrue(vm.uiState.value.isMultiSelectMode)

        vm.toggleTaskSelection(task1.id)
        assertTrue(task1.id in vm.uiState.value.selectedTaskIds)
        assertEquals(1, vm.uiState.value.selectedTaskIds.size)

        vm.toggleTaskSelection(task2.id)
        assertEquals(2, vm.uiState.value.selectedTaskIds.size)

        vm.toggleTaskSelection(task1.id)
        assertEquals(1, vm.uiState.value.selectedTaskIds.size)
        assertFalse(task1.id in vm.uiState.value.selectedTaskIds)
    }

    @Test
    fun `select all and deselect all`() = runTest {
        val task1 = Task(title = "Task 1")
        val task2 = Task(title = "Task 2")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.selectAll()
        assertEquals(2, vm.uiState.value.selectedTaskIds.size)

        vm.deselectAll()
        assertEquals(0, vm.uiState.value.selectedTaskIds.size)
    }

    @Test
    fun `batch plan today calls taskService for each selected task`() = runTest {
        val task1 = Task(title = "Task 1")
        val task2 = Task(title = "Task 2")
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleMultiSelectMode()
        vm.toggleTaskSelection(task1.id)
        vm.toggleTaskSelection(task2.id)
        vm.batchPlanToday()
        advanceUntilIdle()

        coVerify { taskService.planTask(task1.id, LocalDate.now()) }
        coVerify { taskService.planTask(task2.id, LocalDate.now()) }

        // Multi-select should be cleared
        assertFalse(vm.uiState.value.isMultiSelectMode)
        assertEquals(0, vm.uiState.value.selectedTaskIds.size)
    }

    @Test
    fun `batch archive calls changeStatus ARCHIVED for each selected task`() = runTest {
        val task1 = Task(title = "Task 1")
        coEvery { taskRepository.findBacklog() } returns listOf(task1)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleMultiSelectMode()
        vm.toggleTaskSelection(task1.id)
        vm.batchArchive()
        advanceUntilIdle()

        coVerify { taskService.changeStatus(task1.id, TaskStatus.ARCHIVED) }
    }

    @Test
    fun `batch delete calls deleteTask for each selected task`() = runTest {
        val task1 = Task(title = "Task 1")
        coEvery { taskRepository.findBacklog() } returns listOf(task1)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleMultiSelectMode()
        vm.toggleTaskSelection(task1.id)
        vm.batchDelete()
        advanceUntilIdle()

        coVerify { taskService.deleteTask(task1.id) }
    }

    @Test
    fun `planTaskToday calls taskService planTask`() = runTest {
        val task = Task(title = "Plan me")
        coEvery { taskRepository.findBacklog() } returns listOf(task)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.planTaskToday(task.id)
        advanceUntilIdle()

        coVerify { taskService.planTask(task.id, LocalDate.now()) }
    }

    @Test
    fun `quickCreateTask creates task without planned date`() = runTest {
        val task = Task(title = "New backlog task")
        coEvery { taskService.createTask(any(), isNull()) } returns task
        coEvery { taskRepository.findBacklog() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateQuickCreateText("New backlog task #dev")
        assertEquals("New backlog task #dev", vm.uiState.value.quickCreateText)

        vm.quickCreateTask()
        advanceUntilIdle()

        coVerify { taskService.createTask("New backlog task #dev", null) }
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
    fun `clear filters resets search, categories, and statuses`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateSearchQuery("test")
        vm.toggleCategoryFilter(TaskCategory.BUGFIX)
        vm.toggleStatusFilter(TaskStatus.DONE)

        vm.clearFilters()

        val state = vm.uiState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.selectedCategories.isEmpty())
        assertEquals(setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED), state.selectedStatuses)
    }

    @Test
    fun `dismissError clears error from state`() = runTest {
        coEvery { taskRepository.findBacklog() } throws RuntimeException("Error")

        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)

        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `status filter excludes DONE tasks by default`() = runTest {
        val task1 = Task(title = "Todo task", status = TaskStatus.TODO)
        val task2 = Task(title = "Done task", status = TaskStatus.DONE)
        coEvery { taskRepository.findBacklog() } returns listOf(task1, task2)

        val vm = createViewModel()
        advanceUntilIdle()

        // Default statuses: TODO, IN_PROGRESS, PAUSED - DONE is excluded
        val state = vm.uiState.value
        assertEquals(1, state.filteredTasks.size)
        assertEquals("Todo task", state.filteredTasks[0].task.title)
    }
}
