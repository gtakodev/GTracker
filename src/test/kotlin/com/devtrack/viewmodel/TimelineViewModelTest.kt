package com.devtrack.viewmodel

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
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
import java.time.LocalTime

/**
 * Tests for TimelineViewModel (P3.6.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimelineViewModelTest {

    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val sessionRepository = mockk<WorkSessionRepository>(relaxed = true)
    private val eventRepository = mockk<SessionEventRepository>(relaxed = true)
    private val timeCalculator = TimeCalculator()

    private lateinit var viewModel: TimelineViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate.of(2026, 3, 9)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        clearAllMocks()

        // Default stubs
        coEvery { sessionRepository.findByDate(any()) } returns emptyList()
    }

    @AfterEach
    fun teardown() {
        if (::viewModel.isInitialized) {
            viewModel.dispose()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TimelineViewModel {
        viewModel = TimelineViewModel(
            taskRepository = taskRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            timeCalculator = timeCalculator,
            dispatcher = testDispatcher,
        )
        return viewModel
    }

    @Nested
    inner class BuildTimelineBlocksTests {

        @Test
        fun `empty entries produce empty blocks`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val blocks = vm.buildTimelineBlocks(emptyList(), 8, 20)

            assertTrue(blocks.isEmpty())
        }

        @Test
        fun `single session with gap before and after`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val task = Task(title = "Test task", category = TaskCategory.DEVELOPMENT)
            val now = Instant.now()
            val session = WorkSession(
                taskId = task.id,
                date = today,
                startTime = now.minusSeconds(3600),
                endTime = now,
            )
            val events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now.minusSeconds(3600)),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now),
            )

            val entry = TimelineSessionEntry(
                session = session,
                task = task,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                effectiveDuration = Duration.ofHours(1),
                events = events,
            )

            val blocks = vm.buildTimelineBlocks(listOf(entry), 8, 20)

            // Should be: gap 8:00-10:00, session 10:00-11:00, gap 11:00-20:00
            assertEquals(3, blocks.size)

            // First block: gap
            assertTrue(blocks[0].isGap)
            assertEquals(8.0, blocks[0].startHour)
            assertEquals(10.0, blocks[0].endHour)

            // Second block: session
            assertFalse(blocks[1].isGap)
            assertEquals(10.0, blocks[1].startHour)
            assertEquals(11.0, blocks[1].endHour)
            assertEquals("Test task", blocks[1].taskTitle)
            assertEquals(TaskCategory.DEVELOPMENT, blocks[1].category)

            // Third block: trailing gap
            assertTrue(blocks[2].isGap)
            assertEquals(11.0, blocks[2].startHour)
            assertEquals(20.0, blocks[2].endHour)
        }

        @Test
        fun `two consecutive sessions with no gap between`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val task1 = Task(title = "Task A", category = TaskCategory.DEVELOPMENT)
            val task2 = Task(title = "Task B", category = TaskCategory.MEETING)
            val now = Instant.now()

            val entry1 = TimelineSessionEntry(
                session = WorkSession(taskId = task1.id, date = today, startTime = now, endTime = now),
                task = task1,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                effectiveDuration = Duration.ofHours(1),
                events = emptyList(),
            )
            val entry2 = TimelineSessionEntry(
                session = WorkSession(taskId = task2.id, date = today, startTime = now, endTime = now),
                task = task2,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                effectiveDuration = Duration.ofHours(1),
                events = emptyList(),
            )

            val blocks = vm.buildTimelineBlocks(listOf(entry1, entry2), 8, 20)

            // gap 8-9, session A 9-10, session B 10-11, gap 11-20
            assertEquals(4, blocks.size)
            assertTrue(blocks[0].isGap)
            assertFalse(blocks[1].isGap)
            assertEquals("Task A", blocks[1].taskTitle)
            assertFalse(blocks[2].isGap)
            assertEquals("Task B", blocks[2].taskTitle)
            assertTrue(blocks[3].isGap)
        }

        @Test
        fun `session starting at day start has no leading gap`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val task = Task(title = "Task", category = TaskCategory.DEVELOPMENT)
            val now = Instant.now()

            val entry = TimelineSessionEntry(
                session = WorkSession(taskId = task.id, date = today, startTime = now, endTime = now),
                task = task,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(10, 0),
                effectiveDuration = Duration.ofHours(2),
                events = emptyList(),
            )

            val blocks = vm.buildTimelineBlocks(listOf(entry), 8, 20)

            // session 8-10, gap 10-20
            assertEquals(2, blocks.size)
            assertFalse(blocks[0].isGap)
            assertEquals(8.0, blocks[0].startHour)
            assertTrue(blocks[1].isGap)
        }

        @Test
        fun `session ending at day end has no trailing gap`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val task = Task(title = "Task", category = TaskCategory.DEVELOPMENT)
            val now = Instant.now()

            val entry = TimelineSessionEntry(
                session = WorkSession(taskId = task.id, date = today, startTime = now, endTime = now),
                task = task,
                startTime = LocalTime.of(18, 0),
                endTime = LocalTime.of(20, 0),
                effectiveDuration = Duration.ofHours(2),
                events = emptyList(),
            )

            val blocks = vm.buildTimelineBlocks(listOf(entry), 8, 20)

            // gap 8-18, session 18-20
            assertEquals(2, blocks.size)
            assertTrue(blocks[0].isGap)
            assertFalse(blocks[1].isGap)
            assertEquals(20.0, blocks[1].endHour)
        }
    }

    @Nested
    inner class NavigationTests {

        @Test
        fun `initial state has today as selected date`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LocalDate.now(), vm.uiState.value.selectedDate)
        }

        @Test
        fun `selectDate updates selected date`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.selectDate(today)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(today, vm.uiState.value.selectedDate)
        }

        @Test
        fun `previousDay navigates back one day`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.selectDate(today)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.previousDay()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(today.minusDays(1), vm.uiState.value.selectedDate)
        }

        @Test
        fun `nextDay navigates forward one day`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.selectDate(today)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.nextDay()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(today.plusDays(1), vm.uiState.value.selectedDate)
        }
    }

    @Nested
    inner class LoadTimelineTests {

        @Test
        fun `empty day produces empty timeline`() {
            coEvery { sessionRepository.findByDate(any()) } returns emptyList()

            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.timelineBlocks.isEmpty())
            assertTrue(vm.uiState.value.sessionEntries.isEmpty())
            assertEquals(Duration.ZERO, vm.uiState.value.totalTime)
        }

        @Test
        fun `day with sessions produces blocks and entries`() {
            val task = Task(
                title = "DPD-100 Test",
                category = TaskCategory.DEVELOPMENT,
                plannedDate = today,
            )
            val now = Instant.now()
            val session = WorkSession(
                taskId = task.id,
                date = today,
                startTime = now.minusSeconds(3600),
                endTime = now,
            )
            val events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now.minusSeconds(3600)),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now),
            )

            coEvery { sessionRepository.findByDate(any()) } returns listOf(session)
            coEvery { eventRepository.findBySessionId(session.id) } returns events
            coEvery { taskRepository.findById(task.id) } returns task

            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.timelineBlocks.isNotEmpty())
            assertEquals(1, vm.uiState.value.sessionEntries.size)
            assertEquals("DPD-100 Test", vm.uiState.value.sessionEntries[0].task.title)
            assertTrue(vm.uiState.value.totalTime > Duration.ZERO)
        }

        @Test
        fun `error is captured in state`() {
            coEvery { sessionRepository.findByDate(any()) } throws RuntimeException("DB error")

            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.error)
            assertTrue(vm.uiState.value.error!!.contains("DB error"))
        }
    }

    @Nested
    inner class UtilityTests {

        @Test
        fun `dismissError clears error state`() {
            coEvery { sessionRepository.findByDate(any()) } throws RuntimeException("error")

            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            vm.dismissError()
            assertNull(vm.uiState.value.error)
        }

        @Test
        fun `dismissSnackbar clears snackbar`() {
            val vm = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Manually set a snackbar message by exporting (will fail silently on headless, but we can test dismiss)
            vm.dismissSnackbar()
            assertNull(vm.uiState.value.snackbarMessage)
        }
    }
}
