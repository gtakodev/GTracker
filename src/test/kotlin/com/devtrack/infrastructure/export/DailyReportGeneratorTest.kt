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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Tests for DailyReportGenerator (P1.5.5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyReportGeneratorTest {

    private val taskRepository = mockk<TaskRepository>()
    private val sessionRepository = mockk<WorkSessionRepository>()
    private val eventRepository = mockk<SessionEventRepository>()
    private val timeCalculator = TimeCalculator()
    private val jiraTicketParser = JiraTicketParser()

    private lateinit var generator: DailyReportGenerator

    private val today = LocalDate.of(2026, 3, 9)

    @BeforeEach
    fun setup() {
        generator = DailyReportGenerator(
            taskRepository = taskRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            timeCalculator = timeCalculator,
            jiraTicketParser = jiraTicketParser,
        )
    }

    @Nested
    inner class GenerateReportTests {

        @Test
        fun `empty day produces empty report`() = runTest {
            coEvery { taskRepository.findByDate(today) } returns emptyList()
            coEvery { sessionRepository.findByDate(today) } returns emptyList()

            val report = generator.generateReport(today)

            assertEquals(today, report.date)
            assertTrue(report.ticketEntries.isEmpty())
            assertTrue(report.noTicketEntries.isEmpty())
            assertEquals(Duration.ZERO, report.totalDuration)
        }

        @Test
        fun `tasks with jira tickets go into ticket entries`() = runTest {
            val task = Task(
                title = "DPD-1423 Fix pagination",
                category = TaskCategory.BUGFIX,
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

            coEvery { taskRepository.findByDate(today) } returns listOf(task)
            coEvery { sessionRepository.findByDate(today) } returns listOf(session)
            coEvery { eventRepository.findBySessionId(session.id) } returns events

            val report = generator.generateReport(today)

            assertEquals(1, report.ticketEntries.size)
            assertEquals("DPD-1423", report.ticketEntries[0].ticket)
            assertEquals("Fix pagination", report.ticketEntries[0].description)
            assertTrue(report.ticketEntries[0].duration >= Duration.ofMinutes(59))
            assertTrue(report.noTicketEntries.isEmpty())
        }

        @Test
        fun `tasks without jira tickets go into no-ticket entries`() = runTest {
            val task = Task(
                title = "Daily standup",
                category = TaskCategory.MEETING,
                plannedDate = today,
            )
            val now = Instant.now()
            val session = WorkSession(
                taskId = task.id,
                date = today,
                startTime = now.minusSeconds(1800),
                endTime = now,
            )
            val events = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now.minusSeconds(1800)),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now),
            )

            coEvery { taskRepository.findByDate(today) } returns listOf(task)
            coEvery { sessionRepository.findByDate(today) } returns listOf(session)
            coEvery { eventRepository.findBySessionId(session.id) } returns events

            val report = generator.generateReport(today)

            assertTrue(report.ticketEntries.isEmpty())
            assertEquals(1, report.noTicketEntries.size)
            assertEquals("Daily standup", report.noTicketEntries[0].taskTitle)
            assertEquals(TaskCategory.MEETING, report.noTicketEntries[0].category)
            assertTrue(report.noTicketEntries[0].duration >= Duration.ofMinutes(29))
        }

        @Test
        fun `mixed tasks are correctly separated`() = runTest {
            val ticketTask = Task(
                title = "DPD-2456 Analyse cache",
                category = TaskCategory.DEVELOPMENT,
                plannedDate = today,
            )
            val noTicketTask = Task(
                title = "Code review PR #42",
                category = TaskCategory.REVIEW,
                plannedDate = today,
            )
            val now = Instant.now()

            val session1 = WorkSession(
                taskId = ticketTask.id,
                date = today,
                startTime = now.minusSeconds(5400), // 1h30m
                endTime = now.minusSeconds(0),
            )
            val events1 = listOf(
                SessionEvent(sessionId = session1.id, type = EventType.START, timestamp = now.minusSeconds(5400)),
                SessionEvent(sessionId = session1.id, type = EventType.END, timestamp = now),
            )

            val session2 = WorkSession(
                taskId = noTicketTask.id,
                date = today,
                startTime = now.minusSeconds(2700), // 45m
                endTime = now,
            )
            val events2 = listOf(
                SessionEvent(sessionId = session2.id, type = EventType.START, timestamp = now.minusSeconds(2700)),
                SessionEvent(sessionId = session2.id, type = EventType.END, timestamp = now),
            )

            coEvery { taskRepository.findByDate(today) } returns listOf(ticketTask, noTicketTask)
            coEvery { sessionRepository.findByDate(today) } returns listOf(session1, session2)
            coEvery { eventRepository.findBySessionId(session1.id) } returns events1
            coEvery { eventRepository.findBySessionId(session2.id) } returns events2

            val report = generator.generateReport(today)

            assertEquals(1, report.ticketEntries.size)
            assertEquals(1, report.noTicketEntries.size)
            assertEquals("DPD-2456", report.ticketEntries[0].ticket)
            assertEquals("Code review PR #42", report.noTicketEntries[0].taskTitle)
        }

        @Test
        fun `tasks with zero duration are excluded`() = runTest {
            val task = Task(
                title = "DPD-999 Empty task",
                plannedDate = today,
            )

            coEvery { taskRepository.findByDate(today) } returns listOf(task)
            coEvery { sessionRepository.findByDate(today) } returns emptyList()

            val report = generator.generateReport(today)

            assertTrue(report.ticketEntries.isEmpty())
            assertTrue(report.noTicketEntries.isEmpty())
        }

        @Test
        fun `multiple tickets in one task title each get an entry`() = runTest {
            val task = Task(
                title = "DPD-100 DPD-200 Multi-ticket work",
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

            coEvery { taskRepository.findByDate(today) } returns listOf(task)
            coEvery { sessionRepository.findByDate(today) } returns listOf(session)
            coEvery { eventRepository.findBySessionId(session.id) } returns events

            val report = generator.generateReport(today)

            assertEquals(2, report.ticketEntries.size)
            assertEquals("DPD-100", report.ticketEntries[0].ticket)
            assertEquals("DPD-200", report.ticketEntries[1].ticket)
        }

        @Test
        fun `total duration sums all tasks`() = runTest {
            val task1 = Task(title = "DPD-1 Task1", plannedDate = today)
            val task2 = Task(title = "Standup", category = TaskCategory.MEETING, plannedDate = today)
            val now = Instant.now()

            val s1 = WorkSession(taskId = task1.id, date = today, startTime = now.minusSeconds(3600), endTime = now)
            val s2 = WorkSession(taskId = task2.id, date = today, startTime = now.minusSeconds(1800), endTime = now)
            val e1 = listOf(
                SessionEvent(sessionId = s1.id, type = EventType.START, timestamp = now.minusSeconds(3600)),
                SessionEvent(sessionId = s1.id, type = EventType.END, timestamp = now),
            )
            val e2 = listOf(
                SessionEvent(sessionId = s2.id, type = EventType.START, timestamp = now.minusSeconds(1800)),
                SessionEvent(sessionId = s2.id, type = EventType.END, timestamp = now),
            )

            coEvery { taskRepository.findByDate(today) } returns listOf(task1, task2)
            coEvery { sessionRepository.findByDate(today) } returns listOf(s1, s2)
            coEvery { eventRepository.findBySessionId(s1.id) } returns e1
            coEvery { eventRepository.findBySessionId(s2.id) } returns e2

            val report = generator.generateReport(today)

            // ~1h + ~30m = ~90m
            assertTrue(report.totalDuration >= Duration.ofMinutes(89))
        }
    }

    @Nested
    inner class FormatDurationTests {

        @Test
        fun `zero duration formats as 0h00m`() {
            assertEquals("0h00m", DailyReportGenerator.formatDuration(Duration.ZERO))
        }

        @Test
        fun `30 minutes formats as 0h30m`() {
            assertEquals("0h30m", DailyReportGenerator.formatDuration(Duration.ofMinutes(30)))
        }

        @Test
        fun `2h15m formats correctly`() {
            assertEquals("2h15m", DailyReportGenerator.formatDuration(Duration.ofMinutes(135)))
        }

        @Test
        fun `5h00m formats correctly`() {
            assertEquals("5h00m", DailyReportGenerator.formatDuration(Duration.ofHours(5)))
        }

        @Test
        fun `single digit minutes get padded`() {
            assertEquals("1h05m", DailyReportGenerator.formatDuration(Duration.ofMinutes(65)))
        }
    }

    @Nested
    inner class RenderMarkdownTests {

        @Test
        fun `empty report renders header and zero total`() {
            val report = DailyReport(
                date = today,
                ticketEntries = emptyList(),
                noTicketEntries = emptyList(),
                totalDuration = Duration.ZERO,
            )

            val md = generator.renderMarkdown(report)

            assertTrue(md.contains("# Rapport du 09/03/2026"))
            assertTrue(md.contains("**Total : 0h00m**"))
            assertFalse(md.contains("## Tickets Jira"))
            assertFalse(md.contains("## Taches hors-ticket"))
        }

        @Test
        fun `report with ticket entries renders Jira table`() {
            val report = DailyReport(
                date = today,
                ticketEntries = listOf(
                    DailyReport.TicketEntry("DPD-1423", "Fix pagination", Duration.ofMinutes(135)),
                ),
                noTicketEntries = emptyList(),
                totalDuration = Duration.ofMinutes(135),
            )

            val md = generator.renderMarkdown(report)

            assertTrue(md.contains("## Tickets Jira"))
            assertTrue(md.contains("DPD-1423"))
            assertTrue(md.contains("Fix pagination"))
            assertTrue(md.contains("2h15m"))
            assertTrue(md.contains("**Total : 2h15m**"))
        }

        @Test
        fun `report with non-ticket entries renders non-ticket table`() {
            val report = DailyReport(
                date = today,
                ticketEntries = emptyList(),
                noTicketEntries = listOf(
                    DailyReport.NoTicketEntry("Daily standup", TaskCategory.MEETING, Duration.ofMinutes(30)),
                ),
                totalDuration = Duration.ofMinutes(30),
            )

            val md = generator.renderMarkdown(report)

            assertTrue(md.contains("## Taches hors-ticket"))
            assertTrue(md.contains("Daily standup"))
            assertTrue(md.contains("Reunion"))
            assertTrue(md.contains("0h30m"))
        }

        @Test
        fun `full report matches PRD format structure`() {
            val report = DailyReport(
                date = today,
                ticketEntries = listOf(
                    DailyReport.TicketEntry("DPD-1423", "Fix pagination", Duration.ofMinutes(135)),
                    DailyReport.TicketEntry("DPD-2456", "Analyse cache", Duration.ofMinutes(90)),
                ),
                noTicketEntries = listOf(
                    DailyReport.NoTicketEntry("Daily standup", TaskCategory.MEETING, Duration.ofMinutes(30)),
                    DailyReport.NoTicketEntry("Code review PR #42", TaskCategory.REVIEW, Duration.ofMinutes(45)),
                ),
                totalDuration = Duration.ofMinutes(300),
            )

            val md = generator.renderMarkdown(report)

            // Structure checks
            assertTrue(md.startsWith("# Rapport du 09/03/2026"))
            assertTrue(md.contains("## Tickets Jira"))
            assertTrue(md.contains("| Ticket"))
            assertTrue(md.contains("| Description"))
            assertTrue(md.contains("| Temps"))
            assertTrue(md.contains("## Taches hors-ticket"))
            assertTrue(md.contains("| Tache"))
            assertTrue(md.contains("| Categorie"))
            assertTrue(md.contains("**Total : 5h00m**"))

            // Content checks
            assertTrue(md.contains("DPD-1423"))
            assertTrue(md.contains("DPD-2456"))
            assertTrue(md.contains("Fix pagination"))
            assertTrue(md.contains("Analyse cache"))
            assertTrue(md.contains("Daily standup"))
            assertTrue(md.contains("Reunion"))
            assertTrue(md.contains("Code review PR #42"))
            assertTrue(md.contains("Review"))

            // Ends with newline
            assertTrue(md.endsWith("\n"))
        }
    }

    @Nested
    inner class CategoryLabelTests {

        @Test
        fun `all categories have French labels`() {
            assertEquals("Developpement", DailyReportGenerator.categoryLabel(TaskCategory.DEVELOPMENT))
            assertEquals("Correction", DailyReportGenerator.categoryLabel(TaskCategory.BUGFIX))
            assertEquals("Reunion", DailyReportGenerator.categoryLabel(TaskCategory.MEETING))
            assertEquals("Review", DailyReportGenerator.categoryLabel(TaskCategory.REVIEW))
            assertEquals("Documentation", DailyReportGenerator.categoryLabel(TaskCategory.DOCUMENTATION))
            assertEquals("Apprentissage", DailyReportGenerator.categoryLabel(TaskCategory.LEARNING))
            assertEquals("Maintenance", DailyReportGenerator.categoryLabel(TaskCategory.MAINTENANCE))
            assertEquals("Support", DailyReportGenerator.categoryLabel(TaskCategory.SUPPORT))
        }
    }
}
