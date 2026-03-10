package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.MigrationManager
import com.devtrack.data.repository.*
import com.devtrack.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration tests for all repository implementations (P1.2.7).
 * Uses an in-memory SQLite database with shared cache.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryIntegrationTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var taskRepo: TaskRepository
    private lateinit var sessionRepo: WorkSessionRepository
    private lateinit var eventRepo: SessionEventRepository
    private lateinit var templateRepo: TemplateTaskRepository
    private lateinit var settingsRepo: UserSettingsRepository

    @BeforeAll
    fun setup() {
        databaseFactory = DatabaseFactory()
        databaseFactory.initInMemory()
        MigrationManager(databaseFactory).migrate()

        taskRepo = TaskRepositoryImpl(databaseFactory)
        sessionRepo = WorkSessionRepositoryImpl(databaseFactory)
        eventRepo = SessionEventRepositoryImpl(databaseFactory)
        templateRepo = TemplateTaskRepositoryImpl(databaseFactory)
        settingsRepo = UserSettingsRepositoryImpl(databaseFactory)
    }

    @AfterAll
    fun teardown() {
        databaseFactory.close()
    }

    // -----------------------------------------------------------------------
    // TaskRepository tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TaskRepository")
    inner class TaskRepositoryTests {

        @Test
        fun `insert and findById`() = runTest {
            val task = Task(
                title = "PROJ-123 Fix login bug #bug",
                category = TaskCategory.BUGFIX,
                jiraTickets = listOf("PROJ-123"),
                plannedDate = LocalDate.of(2026, 3, 9),
            )

            taskRepo.insert(task)
            val found = taskRepo.findById(task.id)

            assertNotNull(found)
            assertEquals(task.id, found!!.id)
            assertEquals("PROJ-123 Fix login bug #bug", found.title)
            assertEquals(TaskCategory.BUGFIX, found.category)
            assertEquals(listOf("PROJ-123"), found.jiraTickets)
            assertEquals(LocalDate.of(2026, 3, 9), found.plannedDate)
            assertEquals(TaskStatus.TODO, found.status)

            // cleanup
            taskRepo.delete(task.id)
        }

        @Test
        fun `findById returns null for nonexistent id`() = runTest {
            val result = taskRepo.findById(UUID.randomUUID())
            assertNull(result)
        }

        @Test
        fun `findByDate returns tasks planned for given date`() = runTest {
            val date = LocalDate.of(2026, 6, 15)
            val t1 = Task(title = "Task A", plannedDate = date)
            val t2 = Task(title = "Task B", plannedDate = date)
            val t3 = Task(title = "Task C", plannedDate = date.plusDays(1))

            taskRepo.insert(t1)
            taskRepo.insert(t2)
            taskRepo.insert(t3)

            val found = taskRepo.findByDate(date)
            assertEquals(2, found.size)
            assertTrue(found.all { it.plannedDate == date })

            taskRepo.delete(t1.id)
            taskRepo.delete(t2.id)
            taskRepo.delete(t3.id)
        }

        @Test
        fun `findBacklog returns tasks with no planned date excluding archived`() = runTest {
            val backlog = Task(title = "Backlog item")
            val planned = Task(title = "Planned item", plannedDate = LocalDate.of(2026, 1, 1))
            val archived = Task(title = "Archived item", status = TaskStatus.ARCHIVED)

            taskRepo.insert(backlog)
            taskRepo.insert(planned)
            taskRepo.insert(archived)

            val found = taskRepo.findBacklog()
            assertTrue(found.any { it.id == backlog.id })
            assertFalse(found.any { it.id == planned.id })
            assertFalse(found.any { it.id == archived.id })

            taskRepo.delete(backlog.id)
            taskRepo.delete(planned.id)
            taskRepo.delete(archived.id)
        }

        @Test
        fun `findByJiraTicket matches ticket in JSON array`() = runTest {
            val t1 = Task(title = "Ticket task", jiraTickets = listOf("ABC-100", "ABC-200"))
            val t2 = Task(title = "Other task", jiraTickets = listOf("XYZ-999"))

            taskRepo.insert(t1)
            taskRepo.insert(t2)

            val found = taskRepo.findByJiraTicket("ABC-100")
            assertEquals(1, found.size)
            assertEquals(t1.id, found[0].id)

            taskRepo.delete(t1.id)
            taskRepo.delete(t2.id)
        }

        @Test
        fun `findByParentId returns child tasks`() = runTest {
            val parent = Task(title = "Parent task")
            val child1 = Task(title = "Child 1", parentId = parent.id)
            val child2 = Task(title = "Child 2", parentId = parent.id)

            taskRepo.insert(parent)
            taskRepo.insert(child1)
            taskRepo.insert(child2)

            val children = taskRepo.findByParentId(parent.id)
            assertEquals(2, children.size)
            assertTrue(children.all { it.parentId == parent.id })

            // cascade delete cleans up children
            taskRepo.delete(parent.id)
            assertNull(taskRepo.findById(child1.id))
        }

        @Test
        fun `findByStatus returns matching tasks`() = runTest {
            val todo = Task(title = "Todo task", status = TaskStatus.TODO)
            val done = Task(title = "Done task", status = TaskStatus.DONE)

            taskRepo.insert(todo)
            taskRepo.insert(done)

            val foundTodo = taskRepo.findByStatus(TaskStatus.TODO)
            assertTrue(foundTodo.any { it.id == todo.id })
            assertFalse(foundTodo.any { it.id == done.id })

            taskRepo.delete(todo.id)
            taskRepo.delete(done.id)
        }

        @Test
        fun `update modifies task fields`() = runTest {
            val task = Task(title = "Original title", category = TaskCategory.DEVELOPMENT)
            taskRepo.insert(task)

            val updated = task.copy(
                title = "Updated title",
                category = TaskCategory.BUGFIX,
                status = TaskStatus.IN_PROGRESS,
                updatedAt = Instant.now(),
            )
            taskRepo.update(updated)

            val found = taskRepo.findById(task.id)!!
            assertEquals("Updated title", found.title)
            assertEquals(TaskCategory.BUGFIX, found.category)
            assertEquals(TaskStatus.IN_PROGRESS, found.status)

            taskRepo.delete(task.id)
        }

        @Test
        fun `delete removes task`() = runTest {
            val task = Task(title = "To be deleted")
            taskRepo.insert(task)
            assertNotNull(taskRepo.findById(task.id))

            taskRepo.delete(task.id)
            assertNull(taskRepo.findById(task.id))
        }
    }

    // -----------------------------------------------------------------------
    // WorkSessionRepository tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("WorkSessionRepository")
    inner class WorkSessionRepositoryTests {

        private lateinit var task: Task

        @BeforeEach
        fun createTask() = runTest {
            task = Task(title = "Session test task")
            taskRepo.insert(task)
        }

        @AfterEach
        fun cleanup() = runTest {
            taskRepo.delete(task.id)
        }

        @Test
        fun `insert and findById`() = runTest {
            val now = Instant.now()
            val session = WorkSession(
                taskId = task.id,
                date = LocalDate.of(2026, 3, 9),
                startTime = now,
                endTime = now.plus(Duration.ofHours(1)),
                source = SessionSource.TIMER,
                notes = "Test notes",
            )

            sessionRepo.insert(session)
            val found = sessionRepo.findById(session.id)

            assertNotNull(found)
            assertEquals(session.id, found!!.id)
            assertEquals(task.id, found.taskId)
            assertEquals("Test notes", found.notes)
            assertEquals(SessionSource.TIMER, found.source)
            assertNotNull(found.endTime)
        }

        @Test
        fun `findByTaskId returns sessions for given task`() = runTest {
            val s1 = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            val s2 = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())

            sessionRepo.insert(s1)
            sessionRepo.insert(s2)

            val found = sessionRepo.findByTaskId(task.id)
            assertEquals(2, found.size)
        }

        @Test
        fun `findByDate returns sessions for given date`() = runTest {
            val date = LocalDate.of(2026, 7, 1)
            val s1 = WorkSession(taskId = task.id, date = date, startTime = Instant.now())
            val s2 = WorkSession(taskId = task.id, date = date.plusDays(1), startTime = Instant.now())

            sessionRepo.insert(s1)
            sessionRepo.insert(s2)

            val found = sessionRepo.findByDate(date)
            assertEquals(1, found.size)
            assertEquals(s1.id, found[0].id)
        }

        @Test
        fun `findByDateRange returns sessions within range`() = runTest {
            val d1 = LocalDate.of(2026, 8, 1)
            val d2 = LocalDate.of(2026, 8, 3)
            val d3 = LocalDate.of(2026, 8, 5)

            val s1 = WorkSession(taskId = task.id, date = d1, startTime = Instant.now())
            val s2 = WorkSession(taskId = task.id, date = d2, startTime = Instant.now())
            val s3 = WorkSession(taskId = task.id, date = d3, startTime = Instant.now())

            sessionRepo.insert(s1)
            sessionRepo.insert(s2)
            sessionRepo.insert(s3)

            val found = sessionRepo.findByDateRange(d1, d2)
            assertEquals(2, found.size)
            assertTrue(found.none { it.id == s3.id })
        }

        @Test
        fun `findOrphans returns sessions with null endTime`() = runTest {
            val closed = WorkSession(
                taskId = task.id,
                date = LocalDate.of(2026, 3, 9),
                startTime = Instant.now(),
                endTime = Instant.now().plus(Duration.ofHours(1)),
            )
            val orphan = WorkSession(
                taskId = task.id,
                date = LocalDate.of(2026, 3, 9),
                startTime = Instant.now(),
                endTime = null,
            )

            sessionRepo.insert(closed)
            sessionRepo.insert(orphan)

            val orphans = sessionRepo.findOrphans()
            assertTrue(orphans.any { it.id == orphan.id })
            assertFalse(orphans.any { it.id == closed.id })
        }

        @Test
        fun `update modifies session fields`() = runTest {
            val session = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            sessionRepo.insert(session)

            val updated = session.copy(
                endTime = Instant.now().plus(Duration.ofMinutes(30)),
                notes = "Updated notes",
            )
            sessionRepo.update(updated)

            val found = sessionRepo.findById(session.id)!!
            assertNotNull(found.endTime)
            assertEquals("Updated notes", found.notes)
        }

        @Test
        fun `delete removes session`() = runTest {
            val session = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            sessionRepo.insert(session)

            sessionRepo.delete(session.id)
            assertNull(sessionRepo.findById(session.id))
        }
    }

    // -----------------------------------------------------------------------
    // SessionEventRepository tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SessionEventRepository")
    inner class SessionEventRepositoryTests {

        private lateinit var task: Task
        private lateinit var session: WorkSession

        @BeforeEach
        fun createTaskAndSession() = runTest {
            task = Task(title = "Event test task")
            taskRepo.insert(task)
            session = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            sessionRepo.insert(session)
        }

        @AfterEach
        fun cleanup() = runTest {
            taskRepo.delete(task.id) // cascades to sessions and events
        }

        @Test
        fun `insert and findById`() = runTest {
            val event = SessionEvent(
                sessionId = session.id,
                type = EventType.START,
                timestamp = Instant.now(),
            )

            eventRepo.insert(event)
            val found = eventRepo.findById(event.id)

            assertNotNull(found)
            assertEquals(event.id, found!!.id)
            assertEquals(session.id, found.sessionId)
            assertEquals(EventType.START, found.type)
        }

        @Test
        fun `findBySessionId returns events ordered by timestamp ASC`() = runTest {
            val now = Instant.now()
            val e1 = SessionEvent(sessionId = session.id, type = EventType.START, timestamp = now)
            val e2 = SessionEvent(sessionId = session.id, type = EventType.PAUSE, timestamp = now.plusSeconds(600))
            val e3 = SessionEvent(sessionId = session.id, type = EventType.RESUME, timestamp = now.plusSeconds(900))
            val e4 = SessionEvent(sessionId = session.id, type = EventType.END, timestamp = now.plusSeconds(1800))

            // Insert in reverse order to verify sorting
            eventRepo.insert(e4)
            eventRepo.insert(e2)
            eventRepo.insert(e1)
            eventRepo.insert(e3)

            val found = eventRepo.findBySessionId(session.id)
            assertEquals(4, found.size)
            assertEquals(EventType.START, found[0].type)
            assertEquals(EventType.PAUSE, found[1].type)
            assertEquals(EventType.RESUME, found[2].type)
            assertEquals(EventType.END, found[3].type)
        }

        @Test
        fun `delete removes event`() = runTest {
            val event = SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now())
            eventRepo.insert(event)

            eventRepo.delete(event.id)
            assertNull(eventRepo.findById(event.id))
        }
    }

    // -----------------------------------------------------------------------
    // TemplateTaskRepository tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TemplateTaskRepository")
    inner class TemplateTaskRepositoryTests {

        @Test
        fun `insert and findById`() = runTest {
            val template = TemplateTask(
                title = "Daily standup",
                category = TaskCategory.MEETING,
                defaultDurationMin = 15,
            )

            templateRepo.insert(template)
            val found = templateRepo.findById(template.id)

            assertNotNull(found)
            assertEquals("Daily standup", found!!.title)
            assertEquals(TaskCategory.MEETING, found.category)
            assertEquals(15, found.defaultDurationMin)

            templateRepo.delete(template.id)
        }

        @Test
        fun `findAll returns all templates`() = runTest {
            val t1 = TemplateTask(title = "Template 1", category = TaskCategory.DEVELOPMENT)
            val t2 = TemplateTask(title = "Template 2", category = TaskCategory.REVIEW)

            templateRepo.insert(t1)
            templateRepo.insert(t2)

            val all = templateRepo.findAll()
            assertTrue(all.size >= 2)
            assertTrue(all.any { it.id == t1.id })
            assertTrue(all.any { it.id == t2.id })

            templateRepo.delete(t1.id)
            templateRepo.delete(t2.id)
        }

        @Test
        fun `update modifies template fields`() = runTest {
            val template = TemplateTask(title = "Original", category = TaskCategory.DEVELOPMENT)
            templateRepo.insert(template)

            val updated = template.copy(title = "Updated", category = TaskCategory.BUGFIX, defaultDurationMin = 60)
            templateRepo.update(updated)

            val found = templateRepo.findById(template.id)!!
            assertEquals("Updated", found.title)
            assertEquals(TaskCategory.BUGFIX, found.category)
            assertEquals(60, found.defaultDurationMin)

            templateRepo.delete(template.id)
        }

        @Test
        fun `delete removes template`() = runTest {
            val template = TemplateTask(title = "To delete", category = TaskCategory.MEETING)
            templateRepo.insert(template)

            templateRepo.delete(template.id)
            assertNull(templateRepo.findById(template.id))
        }
    }

    // -----------------------------------------------------------------------
    // UserSettingsRepository tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("UserSettingsRepository")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UserSettingsRepositoryTests {

        @Test
        @Order(1)
        fun `get returns default settings when none exist`() = runTest {
            val settings = settingsRepo.get()

            assertEquals("fr", settings.locale)
            assertEquals("SYSTEM", settings.theme)
            assertEquals(30, settings.inactivityThresholdMin)
            assertEquals(8.0, settings.hoursPerDay)
            assertEquals(4.0, settings.halfDayThreshold)
            assertEquals(25, settings.pomodoroWorkMin)
            assertEquals(5, settings.pomodoroBreakMin)
            assertEquals(15, settings.pomodoroLongBreakMin)
            assertEquals(4, settings.pomodoroSessionsBeforeLong)
        }

        @Test
        @Order(2)
        fun `get returns same settings on subsequent calls`() = runTest {
            val first = settingsRepo.get()
            val second = settingsRepo.get()
            assertEquals(first.id, second.id)
        }

        @Test
        @Order(3)
        fun `save updates existing settings`() = runTest {
            val current = settingsRepo.get()
            val updated = current.copy(
                locale = "en",
                theme = "DARK",
                hoursPerDay = 7.5,
                pomodoroWorkMin = 50,
            )

            settingsRepo.save(updated)
            val found = settingsRepo.get()

            assertEquals("en", found.locale)
            assertEquals("DARK", found.theme)
            assertEquals(7.5, found.hoursPerDay)
            assertEquals(50, found.pomodoroWorkMin)
        }
    }

    // -----------------------------------------------------------------------
    // Cascade delete tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cascade deletes")
    inner class CascadeDeleteTests {

        @Test
        fun `deleting a task cascades to sessions and events`() = runTest {
            val task = Task(title = "Cascade test task")
            taskRepo.insert(task)

            val session = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            sessionRepo.insert(session)

            val event = SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now())
            eventRepo.insert(event)

            // Verify they exist
            assertNotNull(sessionRepo.findById(session.id))
            assertNotNull(eventRepo.findById(event.id))

            // Delete the task
            taskRepo.delete(task.id)

            // Verify cascade
            assertNull(taskRepo.findById(task.id))
            assertNull(sessionRepo.findById(session.id))
            assertNull(eventRepo.findById(event.id))
        }

        @Test
        fun `deleting a session cascades to events`() = runTest {
            val task = Task(title = "Session cascade test")
            taskRepo.insert(task)

            val session = WorkSession(taskId = task.id, date = LocalDate.of(2026, 3, 9), startTime = Instant.now())
            sessionRepo.insert(session)

            val e1 = SessionEvent(sessionId = session.id, type = EventType.START, timestamp = Instant.now())
            val e2 = SessionEvent(sessionId = session.id, type = EventType.END, timestamp = Instant.now().plusSeconds(60))
            eventRepo.insert(e1)
            eventRepo.insert(e2)

            // Delete the session
            sessionRepo.delete(session.id)

            // Events should be gone
            assertNull(eventRepo.findById(e1.id))
            assertNull(eventRepo.findById(e2.id))

            // Task should still exist
            assertNotNull(taskRepo.findById(task.id))

            taskRepo.delete(task.id)
        }

        @Test
        fun `deleting a parent task cascades to child tasks`() = runTest {
            val parent = Task(title = "Parent")
            val child = Task(title = "Child", parentId = parent.id)

            taskRepo.insert(parent)
            taskRepo.insert(child)

            taskRepo.delete(parent.id)

            assertNull(taskRepo.findById(parent.id))
            assertNull(taskRepo.findById(child.id))
        }
    }
}
