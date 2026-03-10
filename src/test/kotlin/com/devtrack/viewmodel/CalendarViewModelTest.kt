package com.devtrack.viewmodel

import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.TaskService
import com.devtrack.domain.service.TimeCalculator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Unit tests for CalendarViewModel (P4.5.5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val sessionRepository = mockk<WorkSessionRepository>(relaxed = true)
    private val timeCalculator = mockk<TimeCalculator>(relaxed = true)
    private val taskService = mockk<TaskService>(relaxed = true)

    private lateinit var viewModel: CalendarViewModel

    @BeforeAll
    fun setupAll() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterAll
    fun tearDownAll() {
        Dispatchers.resetMain()
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coEvery { taskRepository.findByDateRange(any(), any()) } returns emptyList()
        coEvery { sessionRepository.findByDateRange(any(), any()) } returns emptyList()
        coEvery { taskRepository.findByDate(any()) } returns emptyList()
        coEvery { sessionRepository.findByDate(any()) } returns emptyList()
    }

    private fun createViewModel(): CalendarViewModel {
        return CalendarViewModel(taskRepository, sessionRepository, timeCalculator, taskService, testDispatcher)
    }

    @Test
    fun `initial state loads calendar for current month`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(YearMonth.now(), state.displayedMonth)
        assertEquals(CalendarViewMode.MONTH, state.viewMode)
        assertEquals(LocalDate.now(), state.selectedDate)
        assertFalse(state.isLoading)
        assertTrue(state.monthDays.isNotEmpty())
    }

    @Test
    fun `month grid has correct number of days including padding`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Month grid should be a multiple of 7 (full weeks)
        assertEquals(0, state.monthDays.size % 7, "Month grid should have full weeks")
        // Should have between 28 and 42 days
        assertTrue(state.monthDays.size in 28..42,
            "Month grid should have 28-42 days but had ${state.monthDays.size}")
    }

    @Test
    fun `month grid marks today correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val todayDay = viewModel.uiState.value.monthDays.find { it.isToday }
        assertNotNull(todayDay, "Should have a day marked as today")
        assertEquals(LocalDate.now(), todayDay!!.date)
    }

    @Test
    fun `month grid marks current month days correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val currentMonth = state.displayedMonth
        val currentMonthDays = state.monthDays.filter { it.isCurrentMonth }
        val otherMonthDays = state.monthDays.filter { !it.isCurrentMonth }

        // All current month days should be in the displayed month
        currentMonthDays.forEach { day ->
            assertEquals(currentMonth, YearMonth.from(day.date),
                "Day ${day.date} should be in ${currentMonth}")
        }
        // Other month days should be outside
        otherMonthDays.forEach { day ->
            assertNotEquals(currentMonth, YearMonth.from(day.date),
                "Day ${day.date} should NOT be in ${currentMonth}")
        }
    }

    @Test
    fun `previousPeriod navigates to previous month`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val originalMonth = viewModel.uiState.value.displayedMonth
        viewModel.previousPeriod()
        advanceUntilIdle()

        assertEquals(originalMonth.minusMonths(1), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun `nextPeriod navigates to next month`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val originalMonth = viewModel.uiState.value.displayedMonth
        viewModel.nextPeriod()
        advanceUntilIdle()

        assertEquals(originalMonth.plusMonths(1), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun `goToToday returns to current month and selects today`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate away
        viewModel.nextPeriod()
        viewModel.nextPeriod()
        advanceUntilIdle()

        // Go back to today
        viewModel.goToToday()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(YearMonth.now(), state.displayedMonth)
        assertEquals(LocalDate.now(), state.selectedDate)
    }

    @Test
    fun `selectDay updates selected date and loads day details`() = runTest {
        val targetDate = LocalDate.now().plusDays(3)
        val task = Task(title = "Test task", plannedDate = targetDate)
        coEvery { taskRepository.findByDate(targetDate) } returns listOf(task)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectDay(targetDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetDate, state.selectedDate)
        assertEquals(1, state.selectedDayTasks.size)
        assertEquals("Test task", state.selectedDayTasks.first().title)
    }

    @Test
    fun `toggleViewMode switches between month and week`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(CalendarViewMode.MONTH, viewModel.uiState.value.viewMode)

        viewModel.toggleViewMode()
        advanceUntilIdle()

        assertEquals(CalendarViewMode.WEEK, viewModel.uiState.value.viewMode)

        viewModel.toggleViewMode()
        advanceUntilIdle()

        assertEquals(CalendarViewMode.MONTH, viewModel.uiState.value.viewMode)
    }

    @Test
    fun `week view has 5 weekdays`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setViewMode(CalendarViewMode.WEEK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.weekDays.size, "Week view should have 5 days (Mon-Fri)")

        // First day should be Monday
        assertEquals(DayOfWeek.MONDAY, state.weekDays.first().date.dayOfWeek)
        // Last day should be Friday
        assertEquals(DayOfWeek.FRIDAY, state.weekDays.last().date.dayOfWeek)
    }

    @Test
    fun `week view previousPeriod navigates to previous week`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setViewMode(CalendarViewMode.WEEK)
        advanceUntilIdle()

        val originalWeekStart = viewModel.uiState.value.displayedWeekStart
        viewModel.previousPeriod()
        advanceUntilIdle()

        assertEquals(originalWeekStart.minusWeeks(1), viewModel.uiState.value.displayedWeekStart)
    }

    @Test
    fun `day data includes task count and duration from sessions`() = runTest {
        val date = LocalDate.now()
        val task1 = Task(title = "Task 1", plannedDate = date)
        val task2 = Task(title = "Task 2", plannedDate = date)
        val session = WorkSession(
            taskId = task1.id,
            date = date,
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now(),
        )

        coEvery { taskRepository.findByDateRange(any(), any()) } returns listOf(task1, task2)
        coEvery { sessionRepository.findByDateRange(any(), any()) } returns listOf(session)
        coEvery { taskRepository.findByDate(date) } returns listOf(task1, task2)
        coEvery { sessionRepository.findByDate(date) } returns listOf(session)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Find today in the month grid
        val todayData = viewModel.uiState.value.monthDays.find { it.date == date }
        assertNotNull(todayData, "Should find today in the month grid")
        assertEquals(2, todayData!!.taskCount)
        assertTrue(todayData.totalDuration.toMinutes() >= 59,
            "Duration should be ~60 min but was ${todayData.totalDuration.toMinutes()}")
    }

    @Test
    fun `maxDayHours is computed from the day with most work`() = runTest {
        val date1 = LocalDate.now()
        val date2 = LocalDate.now().minusDays(1)

        val session1 = WorkSession(
            taskId = UUID.randomUUID(),
            date = date1,
            startTime = Instant.now().minusSeconds(7200), // 2 hours
            endTime = Instant.now(),
        )
        val session2 = WorkSession(
            taskId = UUID.randomUUID(),
            date = date2,
            startTime = Instant.now().minusSeconds(3600), // 1 hour
            endTime = Instant.now(),
        )

        coEvery { sessionRepository.findByDateRange(any(), any()) } returns listOf(session1, session2)

        viewModel = createViewModel()
        advanceUntilIdle()

        val maxHours = viewModel.uiState.value.maxDayHours
        assertTrue(maxHours >= 1.9, "Max day hours should be ~2.0 but was $maxHours")
    }

    @Test
    fun `dismissError clears error state`() = runTest {
        coEvery { taskRepository.findByDateRange(any(), any()) } throws RuntimeException("DB error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadDayRange builds correct day data`() = runTest {
        val start = LocalDate.of(2025, 3, 1)
        val end = LocalDate.of(2025, 3, 7)
        val referenceMonth = YearMonth.of(2025, 3)

        val task = Task(title = "Test", plannedDate = LocalDate.of(2025, 3, 3))
        coEvery { taskRepository.findByDateRange(start, end) } returns listOf(task)
        coEvery { sessionRepository.findByDateRange(start, end) } returns emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        val days = viewModel.loadDayRange(start, end, referenceMonth)

        assertEquals(7, days.size)
        assertTrue(days.all { it.isCurrentMonth })
        val day3 = days.find { it.date == LocalDate.of(2025, 3, 3) }
        assertNotNull(day3)
        assertEquals(1, day3!!.taskCount)
    }
}
