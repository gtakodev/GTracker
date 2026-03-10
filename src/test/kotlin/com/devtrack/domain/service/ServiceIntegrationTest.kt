package com.devtrack.domain.service

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.MigrationManager
import com.devtrack.data.repository.impl.*
import com.devtrack.domain.model.*
import com.devtrack.infrastructure.logging.AuditLogger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * Integration tests for TaskService and SessionService (P1.3).
 * Uses real in-memory SQLite database with all repositories.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceIntegrationTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var taskRepo: TaskRepositoryImpl
    private lateinit var sessionRepo: WorkSessionRepositoryImpl
    private lateinit var eventRepo: SessionEventRepositoryImpl

    private lateinit var jiraTicketParser: JiraTicketParser
    private lateinit var timeCalculator: TimeCalculator
    private lateinit var auditLogger: AuditLogger

    private lateinit var sessionService: SessionService
    private lateinit var taskService: TaskService

    @BeforeAll
    fun setup() {
        databaseFactory = DatabaseFactory()
        databaseFactory.initInMemory()
        MigrationManager(databaseFactory).migrate()

        taskRepo = TaskRepositoryImpl(databaseFactory)
        sessionRepo = WorkSessionRepositoryImpl(databaseFactory)
        eventRepo = SessionEventRepositoryImpl(databaseFactory)

        jiraTicketParser = JiraTicketParser()
        timeCalculator = TimeCalculator()
        auditLogger = AuditLogger()

        sessionService = SessionService(
            sessionRepository = sessionRepo,
            eventRepository = eventRepo,
            taskRepository = taskRepo,
            timeCalculator = timeCalculator,
            auditLogger = auditLogger,
        )

        taskService = TaskService(
            taskRepository = taskRepo,
            sessionService = sessionService,
            jiraTicketParser = jiraTicketParser,
            auditLogger = auditLogger,
        )
    }

    @AfterAll
    fun teardown() {
        databaseFactory.close()
    }

    // -----------------------------------------------------------------------
    // TaskService tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TaskService")
    inner class TaskServiceTests {

        @Test
        fun `createTask extracts Jira tickets from title`() = runTest {
            val task = taskService.createTask("PROJ-123 Fix login bug")

            assertEquals(listOf("PROJ-123"), task.jiraTickets)
            assertTrue(task.title.contains("PROJ-123"))

            taskRepo.delete(task.id)
        }

        @Test
        fun `deleteTask removes task from database`() = runTest {
            val task = taskService.createTask("Task to delete")
            assertNotNull(taskRepo.findById(task.id))

            taskService.deleteTask(task.id)
            assertNull(taskRepo.findById(task.id))
        }

        @Test
        fun `deleteTask stops active session linked to task`() = runTest {
            val task = taskService.createTask("Task with session")
            sessionService.startSession(task.id)

            val active = sessionService.getActiveSession()
            assertNotNull(active)
            assertEquals(task.id, active!!.task.id)

            taskService.deleteTask(task.id)

            // Session should be stopped (task is gone via cascade)
            val activeAfter = sessionService.getActiveSession()
            assertNull(activeAfter)
        }

        @Test
        fun `changeStatus updates task status`() = runTest {
            val task = taskService.createTask("Status test")
            assertEquals(TaskStatus.TODO, task.status)

            taskService.changeStatus(task.id, TaskStatus.DONE)
            val found = taskRepo.findById(task.id)!!
            assertEquals(TaskStatus.DONE, found.status)

            taskRepo.delete(task.id)
        }

        @Test
        fun `changeStatus does nothing for nonexistent task`() = runTest {
            // Should not throw
            taskService.changeStatus(UUID.randomUUID(), TaskStatus.DONE)
        }

        @Test
        fun `planTask sets planned date`() = runTest {
            val task = taskService.createTask("Backlog task")
            assertNull(task.plannedDate)

            val date = LocalDate.of(2026, 7, 1)
            taskService.planTask(task.id, date)

            val found = taskRepo.findById(task.id)!!
            assertEquals(date, found.plannedDate)

            taskRepo.delete(task.id)
        }

        @Test
        fun `unplanTask removes planned date`() = runTest {
            val date = LocalDate.of(2026, 7, 1)
            val task = taskService.createTask("Planned task", date)
            assertEquals(date, task.plannedDate)

            taskService.unplanTask(task.id)

            val found = taskRepo.findById(task.id)!!
            assertNull(found.plannedDate)

            taskRepo.delete(task.id)
        }
    }

    // -----------------------------------------------------------------------
    // SessionService tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SessionService")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class SessionServiceTests {

        @Test
        @Order(1)
        fun `startSession creates session with START event`() = runTest {
            val task = taskService.createTask("Session test task")

            val session = sessionService.startSession(task.id)

            assertNotNull(session)
            assertEquals(task.id, session.taskId)
            assertNull(session.endTime)

            // Should have a START event
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(1, events.size)
            assertEquals(EventType.START, events[0].type)

            // Task should be IN_PROGRESS
            val updatedTask = taskRepo.findById(task.id)!!
            assertEquals(TaskStatus.IN_PROGRESS, updatedTask.status)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(2)
        fun `startSession auto-stops previous active session`() = runTest {
            val task1 = taskService.createTask("Task 1")
            val task2 = taskService.createTask("Task 2")

            val session1 = sessionService.startSession(task1.id)
            val session2 = sessionService.startSession(task2.id)

            // Session 1 should be closed
            val found1 = sessionRepo.findById(session1.id)!!
            assertNotNull(found1.endTime)

            // Session 2 should be open
            val found2 = sessionRepo.findById(session2.id)!!
            assertNull(found2.endTime)

            // Cleanup
            sessionService.stopSession(session2.id)
            taskRepo.delete(task1.id)
            taskRepo.delete(task2.id)
        }

        @Test
        @Order(3)
        fun `getActiveSession returns current session state`() = runTest {
            val task = taskService.createTask("Active session test")
            val session = sessionService.startSession(task.id)

            val active = sessionService.getActiveSession()

            assertNotNull(active)
            assertEquals(session.id, active!!.session.id)
            assertEquals(task.id, active.task.id)
            assertFalse(active.isPaused)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(4)
        fun `getActiveSession returns null when no session is active`() = runTest {
            // Make sure no session is active
            val active = sessionService.getActiveSession()
            if (active != null) {
                sessionService.stopSession(active.session.id)
            }

            val result = sessionService.getActiveSession()
            assertNull(result)
        }

        @Test
        @Order(5)
        fun `pauseSession creates PAUSE event and sets task to PAUSED`() = runTest {
            val task = taskService.createTask("Pause test")
            val session = sessionService.startSession(task.id)

            sessionService.pauseSession(session.id)

            // Should have START and PAUSE events
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size)
            assertEquals(EventType.START, events[0].type)
            assertEquals(EventType.PAUSE, events[1].type)

            // Task status should be PAUSED
            val updatedTask = taskRepo.findById(task.id)!!
            assertEquals(TaskStatus.PAUSED, updatedTask.status)

            // Active session should be paused
            val active = sessionService.getActiveSession()
            assertNotNull(active)
            assertTrue(active!!.isPaused)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(6)
        fun `resumeSession creates RESUME event and sets task to IN_PROGRESS`() = runTest {
            val task = taskService.createTask("Resume test")
            val session = sessionService.startSession(task.id)
            sessionService.pauseSession(session.id)

            sessionService.resumeSession(session.id)

            // Should have START, PAUSE, RESUME events
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(3, events.size)
            assertEquals(EventType.START, events[0].type)
            assertEquals(EventType.PAUSE, events[1].type)
            assertEquals(EventType.RESUME, events[2].type)

            // Task status should be back to IN_PROGRESS
            val updatedTask = taskRepo.findById(task.id)!!
            assertEquals(TaskStatus.IN_PROGRESS, updatedTask.status)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(7)
        fun `stopSession creates END event and closes session`() = runTest {
            val task = taskService.createTask("Stop test")
            val session = sessionService.startSession(task.id)

            sessionService.stopSession(session.id)

            // Session should have endTime
            val found = sessionRepo.findById(session.id)!!
            assertNotNull(found.endTime)

            // Should have START and END events
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size)
            assertEquals(EventType.START, events[0].type)
            assertEquals(EventType.END, events[1].type)

            // No active session
            val active = sessionService.getActiveSession()
            assertNull(active)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(8)
        fun `stopSession is idempotent for already-closed session`() = runTest {
            val task = taskService.createTask("Idempotent stop test")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            // Stopping again should not throw or add events
            sessionService.stopSession(session.id)

            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size) // Still just START + END

            taskRepo.delete(task.id)
        }

        @Test
        @Order(9)
        fun `pauseSession is no-op for closed session`() = runTest {
            val task = taskService.createTask("Pause closed test")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            // Pausing a closed session should be no-op
            sessionService.pauseSession(session.id)

            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size) // Still just START + END

            taskRepo.delete(task.id)
        }

        @Test
        @Order(10)
        fun `resumeSession is no-op for closed session`() = runTest {
            val task = taskService.createTask("Resume closed test")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            // Resuming a closed session should be no-op
            sessionService.resumeSession(session.id)

            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size) // Still just START + END

            taskRepo.delete(task.id)
        }

        @Test
        @Order(11)
        fun `switchToTask stops current and starts new session`() = runTest {
            val task1 = taskService.createTask("Switch from task")
            val task2 = taskService.createTask("Switch to task")

            val session1 = sessionService.startSession(task1.id)
            val session2 = sessionService.switchToTask(task2.id)

            // Session 1 should be closed
            val found1 = sessionRepo.findById(session1.id)!!
            assertNotNull(found1.endTime)

            // Session 2 should be active
            val found2 = sessionRepo.findById(session2.id)!!
            assertNull(found2.endTime)

            val active = sessionService.getActiveSession()
            assertNotNull(active)
            assertEquals(task2.id, active!!.task.id)

            // Cleanup
            sessionService.stopSession(session2.id)
            taskRepo.delete(task1.id)
            taskRepo.delete(task2.id)
        }

        @Test
        @Order(12)
        fun `full session lifecycle - start, pause, resume, stop`() = runTest {
            val task = taskService.createTask("Full lifecycle test")

            // Start
            val session = sessionService.startSession(task.id)
            var active = sessionService.getActiveSession()
            assertNotNull(active)
            assertFalse(active!!.isPaused)

            // Pause
            sessionService.pauseSession(session.id)
            active = sessionService.getActiveSession()
            assertNotNull(active)
            assertTrue(active!!.isPaused)

            // Resume
            sessionService.resumeSession(session.id)
            active = sessionService.getActiveSession()
            assertNotNull(active)
            assertFalse(active!!.isPaused)

            // Stop
            sessionService.stopSession(session.id)
            active = sessionService.getActiveSession()
            assertNull(active)

            // Verify complete event sequence
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(4, events.size)
            assertEquals(EventType.START, events[0].type)
            assertEquals(EventType.PAUSE, events[1].type)
            assertEquals(EventType.RESUME, events[2].type)
            assertEquals(EventType.END, events[3].type)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(13)
        fun `session source is preserved`() = runTest {
            val task = taskService.createTask("Source test")

            val session = sessionService.startSession(task.id, SessionSource.MANUAL)
            val found = sessionRepo.findById(session.id)!!
            assertEquals(SessionSource.MANUAL, found.source)

            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }
    }

    // -----------------------------------------------------------------------
    // Orphan Session Detection tests (P2.4)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("OrphanSessionDetection")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class OrphanSessionDetectionTests {

        @Test
        @Order(1)
        fun `detectOrphanSessions returns empty when no orphans`() = runTest {
            // Ensure no sessions are active
            val active = sessionService.getActiveSession()
            if (active != null) {
                sessionService.stopSession(active.session.id)
            }

            val orphans = sessionService.detectOrphanSessions()
            assertTrue(orphans.isEmpty())
        }

        @Test
        @Order(2)
        fun `detectOrphanSessions finds sessions with no endTime`() = runTest {
            val task = taskService.createTask("Orphan test task")
            val session = sessionService.startSession(task.id)

            // Session is open (no endTime) — should be detected as orphan
            val orphans = sessionService.detectOrphanSessions()
            assertTrue(orphans.isNotEmpty())

            val orphan = orphans.find { it.session.id == session.id }
            assertNotNull(orphan)
            assertEquals(task.id, orphan!!.task.id)
            assertEquals(task.title, orphan.task.title)
            assertNotNull(orphan.lastEventTimestamp)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(3)
        fun `detectOrphanSessions returns task and last event timestamp`() = runTest {
            val task = taskService.createTask("Orphan with events")
            val session = sessionService.startSession(task.id)
            sessionService.pauseSession(session.id)
            sessionService.resumeSession(session.id)

            val orphans = sessionService.detectOrphanSessions()
            val orphan = orphans.find { it.session.id == session.id }
            assertNotNull(orphan)

            // Last event should be RESUME (the most recent)
            val events = eventRepo.findBySessionId(session.id)
            val lastEvent = events.maxByOrNull { it.timestamp }!!
            assertEquals(EventType.RESUME, lastEvent.type)
            assertEquals(lastEvent.timestamp, orphan!!.lastEventTimestamp)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(4)
        fun `closeOrphanAtLastActivity sets endTime to last event timestamp`() = runTest {
            val task = taskService.createTask("Close at last activity")
            val session = sessionService.startSession(task.id)
            sessionService.pauseSession(session.id)

            // Get last event timestamp before closing
            val events = eventRepo.findBySessionId(session.id)
            val lastTimestamp = events.maxByOrNull { it.timestamp }!!.timestamp

            sessionService.closeOrphanAtLastActivity(session.id)

            val closed = sessionRepo.findById(session.id)!!
            assertNotNull(closed.endTime)
            assertEquals(lastTimestamp, closed.endTime)

            // Should no longer appear as orphan
            val orphans = sessionService.detectOrphanSessions()
            assertTrue(orphans.none { it.session.id == session.id })

            taskRepo.delete(task.id)
        }

        @Test
        @Order(5)
        fun `closeOrphanNow sets endTime to current time`() = runTest {
            val task = taskService.createTask("Close now test")
            val session = sessionService.startSession(task.id)

            sessionService.closeOrphanNow(session.id)

            val closed = sessionRepo.findById(session.id)!!
            assertNotNull(closed.endTime)

            // Should no longer appear as orphan
            val orphans = sessionService.detectOrphanSessions()
            assertTrue(orphans.none { it.session.id == session.id })

            taskRepo.delete(task.id)
        }

        @Test
        @Order(6)
        fun `closeOrphanAtLastActivity is no-op for already closed session`() = runTest {
            val task = taskService.createTask("Already closed")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            val closedBefore = sessionRepo.findById(session.id)!!
            val endTimeBefore = closedBefore.endTime

            // Should not change anything
            sessionService.closeOrphanAtLastActivity(session.id)

            val closedAfter = sessionRepo.findById(session.id)!!
            assertEquals(endTimeBefore, closedAfter.endTime)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(7)
        fun `autoPauseForInactivity inserts retroactive PAUSE event`() = runTest {
            val task = taskService.createTask("Inactivity test")
            val session = sessionService.startSession(task.id)

            sessionService.autoPauseForInactivity(session.id, 30)

            val events = eventRepo.findBySessionId(session.id)
            // Should have START and PAUSE events
            assertTrue(events.size >= 2)

            val pauseEvent = events.find { it.type == EventType.PAUSE }
            assertNotNull(pauseEvent)

            // Pause timestamp should be ~30 minutes before now (retroactive)
            val now = java.time.Instant.now()
            val expectedPauseTime = now.minusSeconds(30 * 60)
            val diffSeconds = kotlin.math.abs(java.time.Duration.between(expectedPauseTime, pauseEvent!!.timestamp).seconds)
            assertTrue(diffSeconds < 5, "Pause timestamp should be ~30min before now, diff was ${diffSeconds}s")

            // Task should be PAUSED
            val updatedTask = taskRepo.findById(task.id)!!
            assertEquals(TaskStatus.PAUSED, updatedTask.status)

            // Cleanup
            sessionService.stopSession(session.id)
            taskRepo.delete(task.id)
        }

        @Test
        @Order(8)
        fun `autoPauseForInactivity is no-op for closed session`() = runTest {
            val task = taskService.createTask("Closed inactivity")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            val eventsBefore = eventRepo.findBySessionId(session.id)

            sessionService.autoPauseForInactivity(session.id, 30)

            val eventsAfter = eventRepo.findBySessionId(session.id)
            assertEquals(eventsBefore.size, eventsAfter.size)

            taskRepo.delete(task.id)
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class SubTaskTests {

        @Test
        @Order(1)
        fun `createSubTask creates child task with parentId`() = runTest {
            val parent = taskService.createTask("Parent task")
            val subTask = taskService.createSubTask(parent.id, "Sub-task 1")

            assertEquals(parent.id, subTask.parentId)
            assertEquals("Sub-task 1", subTask.title)

            taskRepo.delete(parent.id) // cascade deletes sub-task
        }

        @Test
        @Order(2)
        fun `createSubTask inherits parent plannedDate`() = runTest {
            val today = java.time.LocalDate.now()
            val parent = taskService.createTask("Parent with date", today)
            val subTask = taskService.createSubTask(parent.id, "Sub-task")

            assertEquals(today, subTask.plannedDate)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(3)
        fun `createSubTask inherits parent category when none detected`() = runTest {
            val parent = taskService.createTask("Parent task #review")
            assertEquals(TaskCategory.REVIEW, parent.category)

            val subTask = taskService.createSubTask(parent.id, "Sub-task without category")
            assertEquals(TaskCategory.REVIEW, subTask.category)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(4)
        fun `createSubTask detects category from sub-task title`() = runTest {
            val parent = taskService.createTask("Parent task")
            val subTask = taskService.createSubTask(parent.id, "Fix the crash #bug")

            assertEquals(TaskCategory.BUGFIX, subTask.category)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(5)
        fun `createSubTask fails for sub-task of sub-task (depth limit)`() = runTest {
            val parent = taskService.createTask("Grandparent")
            val child = taskService.createSubTask(parent.id, "Child")

            val exception = assertThrows<IllegalArgumentException> {
                taskService.createSubTask(child.id, "Grandchild")
            }
            assertTrue(exception.message!!.contains("max depth"))

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(6)
        fun `createSubTask fails for non-existent parent`() = runTest {
            val fakeId = java.util.UUID.randomUUID()
            val exception = assertThrows<IllegalArgumentException> {
                taskService.createSubTask(fakeId, "Orphan sub-task")
            }
            assertTrue(exception.message!!.contains("not found"))
        }

        @Test
        @Order(7)
        fun `getSubTasks returns all children of parent`() = runTest {
            val parent = taskService.createTask("Parent")
            taskService.createSubTask(parent.id, "Sub 1")
            taskService.createSubTask(parent.id, "Sub 2")
            taskService.createSubTask(parent.id, "Sub 3")

            val subTasks = taskService.getSubTasks(parent.id)
            assertEquals(3, subTasks.size)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(8)
        fun `cascade delete removes sub-tasks when parent deleted`() = runTest {
            val parent = taskService.createTask("Parent to delete")
            val sub1 = taskService.createSubTask(parent.id, "Sub 1")
            val sub2 = taskService.createSubTask(parent.id, "Sub 2")

            taskService.deleteTask(parent.id)

            assertNull(taskRepo.findById(parent.id))
            assertNull(taskRepo.findById(sub1.id))
            assertNull(taskRepo.findById(sub2.id))
        }

        @Test
        @Order(9)
        fun `findByDate excludes sub-tasks from top-level list`() = runTest {
            val today = java.time.LocalDate.now()
            val parent = taskService.createTask("Parent for today", today)
            taskService.createSubTask(parent.id, "Sub for today")

            val todayTasks = taskRepo.findByDate(today)
            assertEquals(1, todayTasks.size)
            assertEquals(parent.id, todayTasks.first().id)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(10)
        fun `findBacklog excludes sub-tasks from top-level list`() = runTest {
            val parent = taskService.createTask("Backlog parent")
            taskService.createSubTask(parent.id, "Backlog sub")

            val backlogTasks = taskRepo.findBacklog()
            val parentInBacklog = backlogTasks.find { it.id == parent.id }
            val subInBacklog = backlogTasks.find { it.title == "Backlog sub" }

            assertNotNull(parentInBacklog)
            assertNull(subInBacklog)

            taskRepo.delete(parent.id)
        }

        @Test
        @Order(11)
        fun `findAll excludes sub-tasks from top-level list`() = runTest {
            val parent = taskService.createTask("All parent")
            taskService.createSubTask(parent.id, "All sub")

            val allTasks = taskRepo.findAll()
            val parentInAll = allTasks.find { it.id == parent.id }
            val subInAll = allTasks.find { it.title == "All sub" }

            assertNotNull(parentInAll)
            assertNull(subInAll)

            taskRepo.delete(parent.id)
        }
    }

    // -----------------------------------------------------------------------
    // Manual Session Editing tests (P2.7)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ManualSessionEditing")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ManualSessionEditingTests {

        @Test
        @Order(1)
        fun `createManualSession creates closed session with START and END events`() = runTest {
            val task = taskService.createTask("Manual session task")
            val date = LocalDate.now()
            val start = java.time.Instant.now().minusSeconds(3600)
            val end = java.time.Instant.now()

            val session = sessionService.createManualSession(task.id, date, start, end, "Test notes")

            assertNotNull(session)
            assertEquals(task.id, session.taskId)
            assertEquals(date, session.date)
            assertEquals(start, session.startTime)
            assertEquals(end, session.endTime)
            assertEquals(SessionSource.MANUAL, session.source)
            assertEquals("Test notes", session.notes)

            // Should have START and END events
            val events = eventRepo.findBySessionId(session.id)
            assertEquals(2, events.size)
            assertEquals(EventType.START, events[0].type)
            assertEquals(start, events[0].timestamp)
            assertEquals(EventType.END, events[1].type)
            assertEquals(end, events[1].timestamp)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(2)
        fun `createManualSession throws when start time is after end time`() = runTest {
            val task = taskService.createTask("Invalid session task")
            val date = LocalDate.now()
            val start = java.time.Instant.now()
            val end = start.minusSeconds(3600) // end before start

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.createManualSession(task.id, date, start, end)
            }
            assertTrue(exception.message!!.contains("before"))

            taskRepo.delete(task.id)
        }

        @Test
        @Order(3)
        fun `createManualSession throws for non-existent task`() = runTest {
            val fakeId = UUID.randomUUID()
            val start = java.time.Instant.now().minusSeconds(3600)
            val end = java.time.Instant.now()

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.createManualSession(fakeId, LocalDate.now(), start, end)
            }
            assertTrue(exception.message!!.contains("not found"))
        }

        @Test
        @Order(4)
        fun `createManualSession without notes sets notes to null`() = runTest {
            val task = taskService.createTask("No notes task")
            val start = java.time.Instant.now().minusSeconds(3600)
            val end = java.time.Instant.now()

            val session = sessionService.createManualSession(task.id, LocalDate.now(), start, end)

            assertNull(session.notes)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(5)
        fun `validateEventSequence accepts valid START-END sequence`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            // Should not throw
            sessionService.validateEventSequence(events)
        }

        @Test
        @Order(6)
        fun `validateEventSequence accepts START-PAUSE-RESUME-END sequence`() {
            val now = java.time.Instant.now()
            val sessionId = UUID.randomUUID()
            val events = listOf(
                SessionEvent(sessionId = sessionId, type = EventType.START, timestamp = now),
                SessionEvent(sessionId = sessionId, type = EventType.PAUSE, timestamp = now.plusSeconds(1800)),
                SessionEvent(sessionId = sessionId, type = EventType.RESUME, timestamp = now.plusSeconds(2400)),
                SessionEvent(sessionId = sessionId, type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            sessionService.validateEventSequence(events)
        }

        @Test
        @Order(7)
        fun `validateEventSequence accepts START-PAUSE-END sequence`() {
            val now = java.time.Instant.now()
            val sessionId = UUID.randomUUID()
            val events = listOf(
                SessionEvent(sessionId = sessionId, type = EventType.START, timestamp = now),
                SessionEvent(sessionId = sessionId, type = EventType.PAUSE, timestamp = now.plusSeconds(1800)),
                SessionEvent(sessionId = sessionId, type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            sessionService.validateEventSequence(events)
        }

        @Test
        @Order(8)
        fun `validateEventSequence rejects less than 2 events`() {
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = java.time.Instant.now()),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("at least"))
        }

        @Test
        @Order(9)
        fun `validateEventSequence rejects first event not START`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.PAUSE, timestamp = now),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("First"))
        }

        @Test
        @Order(10)
        fun `validateEventSequence rejects last event not END`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.PAUSE, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("Last"))
        }

        @Test
        @Order(11)
        fun `validateEventSequence rejects events out of chronological order`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now.plusSeconds(3600)),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.END, timestamp = now),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("chronological"))
        }

        @Test
        @Order(12)
        fun `validateEventSequence rejects invalid transition START-START`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now.plusSeconds(1800)),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("START"))
        }

        @Test
        @Order(13)
        fun `validateEventSequence rejects invalid transition PAUSE-PAUSE`() {
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.START, timestamp = now),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.PAUSE, timestamp = now.plusSeconds(600)),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.PAUSE, timestamp = now.plusSeconds(1200)),
                SessionEvent(sessionId = UUID.randomUUID(), type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.validateEventSequence(events)
            }
            assertTrue(exception.message!!.contains("PAUSE"))
        }

        @Test
        @Order(14)
        fun `updateSessionEvents replaces events and updates session times`() = runTest {
            val task = taskService.createTask("Update events task")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            val now = java.time.Instant.now()
            val newStart = now.minusSeconds(7200)
            val newPause = now.minusSeconds(5400)
            val newResume = now.minusSeconds(3600)
            val newEnd = now

            val newEvents = listOf(
                SessionEvent(sessionId = session.id, type = EventType.START, timestamp = newStart),
                SessionEvent(sessionId = session.id, type = EventType.PAUSE, timestamp = newPause),
                SessionEvent(sessionId = session.id, type = EventType.RESUME, timestamp = newResume),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = newEnd),
            )

            val result = sessionService.updateSessionEvents(session.id, newEvents)

            assertEquals(4, result.size)
            assertEquals(EventType.START, result[0].type)
            assertEquals(newStart, result[0].timestamp)
            assertEquals(EventType.END, result[3].type)
            assertEquals(newEnd, result[3].timestamp)

            // Session start/end should be updated
            val updatedSession = sessionRepo.findById(session.id)!!
            assertEquals(newStart, updatedSession.startTime)
            assertEquals(newEnd, updatedSession.endTime)

            // Events in DB should match
            val dbEvents = eventRepo.findBySessionId(session.id)
            assertEquals(4, dbEvents.size)

            taskRepo.delete(task.id)
        }

        @Test
        @Order(15)
        fun `updateSessionEvents rejects invalid sequence`() = runTest {
            val task = taskService.createTask("Invalid update task")
            val session = sessionService.startSession(task.id)
            sessionService.stopSession(session.id)

            val now = java.time.Instant.now()
            val invalidEvents = listOf(
                SessionEvent(sessionId = session.id, type = EventType.PAUSE, timestamp = now),
                SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.updateSessionEvents(session.id, invalidEvents)
            }
            assertTrue(exception.message!!.contains("First"))

            taskRepo.delete(task.id)
        }

        @Test
        @Order(16)
        fun `updateSessionEvents throws for non-existent session`() = runTest {
            val fakeId = UUID.randomUUID()
            val now = java.time.Instant.now()
            val events = listOf(
                SessionEvent(sessionId = fakeId, type = EventType.START, timestamp = now),
                SessionEvent(sessionId = fakeId, type = EventType.END, timestamp = now.plusSeconds(3600)),
            )

            val exception = assertThrows<IllegalArgumentException> {
                sessionService.updateSessionEvents(fakeId, events)
            }
            assertTrue(exception.message!!.contains("not found"))
        }

        @Test
        @Order(17)
        fun `getSessionsForTask returns sessions with events and durations`() = runTest {
            val task = taskService.createTask("Sessions for task")

            // Create two sessions for this task
            val now = java.time.Instant.now()
            val session1 = sessionService.createManualSession(
                task.id, LocalDate.now(),
                now.minusSeconds(7200), now.minusSeconds(3600)
            )
            val session2 = sessionService.createManualSession(
                task.id, LocalDate.now(),
                now.minusSeconds(3600), now
            )

            val result = sessionService.getSessionsForTask(task.id)

            assertEquals(2, result.size)
            // Should be sorted by startTime descending
            assertTrue(result[0].session.startTime.isAfter(result[1].session.startTime) ||
                result[0].session.startTime == result[1].session.startTime)
            // Each should have events
            result.forEach { swe ->
                assertTrue(swe.events.isNotEmpty())
                assertTrue(swe.effectiveDuration > Duration.ZERO)
            }

            taskRepo.delete(task.id)
        }

        @Test
        @Order(18)
        fun `getSessionsForTask returns empty for task with no sessions`() = runTest {
            val task = taskService.createTask("Task without sessions")

            val result = sessionService.getSessionsForTask(task.id)
            assertTrue(result.isEmpty())

            taskRepo.delete(task.id)
        }
    }
}
