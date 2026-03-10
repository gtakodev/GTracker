package com.devtrack.infrastructure.export

import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.JiraTicketParser
import com.devtrack.domain.service.TimeCalculator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Tests for StandupGenerator (P3.4.2).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StandupGeneratorTest {

    private val taskRepository = mockk<TaskRepository>()
    private val sessionRepository = mockk<WorkSessionRepository>()
    private val eventRepository = mockk<SessionEventRepository>()
    private val timeCalculator = TimeCalculator()
    private val jiraTicketParser = JiraTicketParser()

    private lateinit var generator: StandupGenerator

    // Wednesday 2026-03-11
    private val wednesday = LocalDate.of(2026, 3, 11)
    // Monday 2026-03-09
    private val monday = LocalDate.of(2026, 3, 9)

    @BeforeEach
    fun setup() {
        generator = StandupGenerator(
            taskRepository = taskRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            timeCalculator = timeCalculator,
            jiraTicketParser = jiraTicketParser,
        )
    }

    @Nested
    inner class GetPreviousWorkDayTests {

        @Test
        fun `Tuesday returns Monday`() {
            val tuesday = LocalDate.of(2026, 3, 10)
            assertEquals(LocalDate.of(2026, 3, 9), StandupGenerator.getPreviousWorkDay(tuesday))
        }

        @Test
        fun `Wednesday returns Tuesday`() {
            assertEquals(LocalDate.of(2026, 3, 10), StandupGenerator.getPreviousWorkDay(wednesday))
        }

        @Test
        fun `Monday returns previous Friday`() {
            assertEquals(LocalDate.of(2026, 3, 6), StandupGenerator.getPreviousWorkDay(monday))
        }

        @Test
        fun `Saturday returns Friday`() {
            val saturday = LocalDate.of(2026, 3, 14)
            assertEquals(LocalDate.of(2026, 3, 13), StandupGenerator.getPreviousWorkDay(saturday))
        }

        @Test
        fun `Sunday returns Friday`() {
            val sunday = LocalDate.of(2026, 3, 15)
            assertEquals(LocalDate.of(2026, 3, 13), StandupGenerator.getPreviousWorkDay(sunday))
        }
    }

    @Nested
    inner class GenerateStandupTests {

        @Test
        fun `empty standup for day with no data`() = runTest {
            // Previous work day for Wednesday is Tuesday
            val tuesday = LocalDate.of(2026, 3, 10)
            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns emptyList()

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("# Standup"))
            assertTrue(result.contains("Hier j'ai travaille sur"))
            assertTrue(result.contains("Aucune session enregistree"))
            assertTrue(result.contains("Aujourd'hui je vais"))
            assertTrue(result.contains("Aucune tache planifiee"))
            assertTrue(result.contains("Blocages"))
        }

        @Test
        fun `Monday standup says Vendredi dernier`() = runTest {
            val friday = LocalDate.of(2026, 3, 6)
            coEvery { sessionRepository.findByDate(friday) } returns emptyList()
            coEvery { taskRepository.findByDate(monday) } returns emptyList()

            val result = generator.generateStandup(monday)

            assertTrue(result.contains("Vendredi dernier j'ai travaille sur"))
            assertFalse(result.contains("Hier j'ai travaille sur"))
        }

        @Test
        fun `non-Monday standup says Hier`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns emptyList()

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("Hier j'ai travaille sur"))
            assertFalse(result.contains("Vendredi dernier"))
        }

        @Test
        fun `previous day sessions are listed with durations`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            val task = Task(
                title = "DPD-100 Fix login bug",
                category = TaskCategory.BUGFIX,
                plannedDate = tuesday,
            )
            val now = Instant.now()
            val session = WorkSession(
                taskId = task.id,
                date = tuesday,
                startTime = now.minusSeconds(7200),
                endTime = now,
            )
            val events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now.minusSeconds(7200)),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now),
            )

            coEvery { sessionRepository.findByDate(tuesday) } returns listOf(session)
            coEvery { taskRepository.findById(task.id) } returns task
            coEvery { eventRepository.findBySessionId(session.id) } returns events
            coEvery { taskRepository.findByDate(wednesday) } returns emptyList()

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("`DPD-100`"))
            assertTrue(result.contains("Fix login bug"))
            assertTrue(result.contains("2h00m"))
        }

        @Test
        fun `today planned tasks are listed`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            val todayTask = Task(
                title = "DPD-200 Implement feature",
                category = TaskCategory.DEVELOPMENT,
                status = TaskStatus.TODO,
                plannedDate = wednesday,
            )

            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns listOf(todayTask)

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("`DPD-200`"))
            assertTrue(result.contains("Implement feature"))
        }

        @Test
        fun `done tasks are excluded from today section`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            val doneTask = Task(
                title = "DPD-300 Old task",
                status = TaskStatus.DONE,
                plannedDate = wednesday,
            )
            val pendingTask = Task(
                title = "DPD-400 New task",
                status = TaskStatus.TODO,
                plannedDate = wednesday,
            )

            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns listOf(doneTask, pendingTask)

            val result = generator.generateStandup(wednesday)

            assertFalse(result.contains("DPD-300"))
            assertTrue(result.contains("DPD-400"))
        }

        @Test
        fun `in-progress task shows status label`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            val inProgressTask = Task(
                title = "DPD-500 Active work",
                status = TaskStatus.IN_PROGRESS,
                plannedDate = wednesday,
            )

            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns listOf(inProgressTask)

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("*(en cours)*"))
        }

        @Test
        fun `blockers section is always present`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns emptyList()

            val result = generator.generateStandup(wednesday)

            assertTrue(result.contains("## Blocages"))
            assertTrue(result.contains("aucun blocage signale"))
        }
    }

    @Nested
    inner class GenerateTests {

        @Test
        fun `generate returns ReportOutput with standup content`() = runTest {
            val tuesday = LocalDate.of(2026, 3, 10)
            coEvery { sessionRepository.findByDate(tuesday) } returns emptyList()
            coEvery { taskRepository.findByDate(wednesday) } returns emptyList()

            val output = generator.generate(ReportPeriod.Day(wednesday))

            assertTrue(output.title.contains("Standup"))
            assertTrue(output.title.contains("11/03/2026"))
            assertNotNull(output.markdownContent)
            // For standup, markdown and plain text are the same
            assertEquals(output.markdownContent, output.plainTextContent)
        }

        @Test
        fun `generate throws for non-Day period`() {
            assertThrows(IllegalArgumentException::class.java) {
                runTest {
                    generator.generate(ReportPeriod.Week(LocalDate.now()))
                }
            }
        }
    }
}
