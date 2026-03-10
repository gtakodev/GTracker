package com.devtrack.domain.service

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.MigrationManager
import com.devtrack.data.repository.impl.SessionEventRepositoryImpl
import com.devtrack.data.repository.impl.TaskRepositoryImpl
import com.devtrack.data.repository.impl.WorkSessionRepositoryImpl
import com.devtrack.domain.model.*
import com.devtrack.infrastructure.logging.AuditLogger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import java.util.UUID

/**
 * Integration tests for drag & drop reordering logic (P4.1.4).
 * Tests TaskService.reorderTasks(), moveTaskToDate(), and
 * TaskRepository.updateDisplayOrders().
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DragDropIntegrationTest {

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var taskRepo: TaskRepositoryImpl
    private lateinit var sessionRepo: WorkSessionRepositoryImpl
    private lateinit var eventRepo: SessionEventRepositoryImpl
    private lateinit var taskService: TaskService

    @BeforeAll
    fun setup() {
        databaseFactory = DatabaseFactory()
        databaseFactory.initInMemory()
        MigrationManager(databaseFactory).migrate()

        taskRepo = TaskRepositoryImpl(databaseFactory)
        sessionRepo = WorkSessionRepositoryImpl(databaseFactory)
        eventRepo = SessionEventRepositoryImpl(databaseFactory)

        val auditLogger = AuditLogger()
        val timeCalculator = TimeCalculator()
        val jiraTicketParser = JiraTicketParser()

        val sessionService = SessionService(
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

    @Test
    fun `new tasks have displayOrder 0 by default`() = runTest {
        val task = taskService.createTask("Default order task", LocalDate.of(2026, 1, 1))
        assertEquals(0, task.displayOrder)

        val fetched = taskRepo.findById(task.id)
        assertNotNull(fetched)
        assertEquals(0, fetched!!.displayOrder)

        // Cleanup
        taskRepo.delete(task.id)
    }

    @Test
    fun `reorderTasks persists display order`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val task1 = taskService.createTask("Reorder task A", today)
        val task2 = taskService.createTask("Reorder task B", today)
        val task3 = taskService.createTask("Reorder task C", today)

        // All start at 0
        assertEquals(0, taskRepo.findById(task1.id)!!.displayOrder)
        assertEquals(0, taskRepo.findById(task2.id)!!.displayOrder)
        assertEquals(0, taskRepo.findById(task3.id)!!.displayOrder)

        // Reorder: C, A, B
        taskService.reorderTasks(listOf(task3.id, task1.id, task2.id))

        assertEquals(0, taskRepo.findById(task3.id)!!.displayOrder)
        assertEquals(1, taskRepo.findById(task1.id)!!.displayOrder)
        assertEquals(2, taskRepo.findById(task2.id)!!.displayOrder)

        // Cleanup
        taskRepo.delete(task1.id)
        taskRepo.delete(task2.id)
        taskRepo.delete(task3.id)
    }

    @Test
    fun `reorderTasks preserves order after second reorder`() = runTest {
        val today = LocalDate.of(2026, 3, 11)
        val task1 = taskService.createTask("Reorder2 A", today)
        val task2 = taskService.createTask("Reorder2 B", today)
        val task3 = taskService.createTask("Reorder2 C", today)

        // First reorder: B, C, A
        taskService.reorderTasks(listOf(task2.id, task3.id, task1.id))
        assertEquals(0, taskRepo.findById(task2.id)!!.displayOrder)
        assertEquals(1, taskRepo.findById(task3.id)!!.displayOrder)
        assertEquals(2, taskRepo.findById(task1.id)!!.displayOrder)

        // Second reorder: A, B, C
        taskService.reorderTasks(listOf(task1.id, task2.id, task3.id))
        assertEquals(0, taskRepo.findById(task1.id)!!.displayOrder)
        assertEquals(1, taskRepo.findById(task2.id)!!.displayOrder)
        assertEquals(2, taskRepo.findById(task3.id)!!.displayOrder)

        // Cleanup
        taskRepo.delete(task1.id)
        taskRepo.delete(task2.id)
        taskRepo.delete(task3.id)
    }

    @Test
    fun `findByDate returns tasks sorted by displayOrder`() = runTest {
        val today = LocalDate.of(2026, 3, 12)
        val task1 = taskService.createTask("Sort A", today)
        val task2 = taskService.createTask("Sort B", today)
        val task3 = taskService.createTask("Sort C", today)

        // Reorder: C=0, B=1, A=2
        taskService.reorderTasks(listOf(task3.id, task2.id, task1.id))

        val tasks = taskRepo.findByDate(today)
        assertTrue(tasks.size >= 3)

        val relevantTasks = tasks.filter { it.id in listOf(task1.id, task2.id, task3.id) }
        assertEquals(3, relevantTasks.size)
        assertEquals(task3.id, relevantTasks[0].id)
        assertEquals(task2.id, relevantTasks[1].id)
        assertEquals(task1.id, relevantTasks[2].id)

        // Cleanup
        taskRepo.delete(task1.id)
        taskRepo.delete(task2.id)
        taskRepo.delete(task3.id)
    }

    @Test
    fun `findByDateRange returns tasks sorted by displayOrder`() = runTest {
        val date = LocalDate.of(2026, 3, 13)
        val task1 = taskService.createTask("Range A", date)
        val task2 = taskService.createTask("Range B", date)

        taskService.reorderTasks(listOf(task2.id, task1.id))

        val tasks = taskRepo.findByDateRange(date, date)
        val relevantTasks = tasks.filter { it.id in listOf(task1.id, task2.id) }
        assertEquals(2, relevantTasks.size)
        assertEquals(task2.id, relevantTasks[0].id)
        assertEquals(task1.id, relevantTasks[1].id)

        // Cleanup
        taskRepo.delete(task1.id)
        taskRepo.delete(task2.id)
    }

    @Test
    fun `moveTaskToDate changes planned date`() = runTest {
        val originalDate = LocalDate.of(2026, 3, 14)
        val targetDate = LocalDate.of(2026, 3, 15)

        val task = taskService.createTask("Move task", originalDate)
        assertEquals(originalDate, taskRepo.findById(task.id)!!.plannedDate)

        taskService.moveTaskToDate(task.id, targetDate)
        assertEquals(targetDate, taskRepo.findById(task.id)!!.plannedDate)

        // Task no longer appears on original date
        val originalTasks = taskRepo.findByDate(originalDate)
        assertFalse(originalTasks.any { it.id == task.id })

        // Task appears on target date
        val targetTasks = taskRepo.findByDate(targetDate)
        assertTrue(targetTasks.any { it.id == task.id })

        // Cleanup
        taskRepo.delete(task.id)
    }

    @Test
    fun `moveTaskToDate on non-existent task does nothing`() = runTest {
        val fakeId = UUID.randomUUID()
        // Should not throw
        taskService.moveTaskToDate(fakeId, LocalDate.of(2026, 3, 16))
    }

    @Test
    fun `reorderTasks with single task sets order to 0`() = runTest {
        val date = LocalDate.of(2026, 3, 17)
        val task = taskService.createTask("Single task", date)

        taskService.reorderTasks(listOf(task.id))
        assertEquals(0, taskRepo.findById(task.id)!!.displayOrder)

        // Cleanup
        taskRepo.delete(task.id)
    }

    @Test
    fun `reorderTasks with empty list does not throw`() = runTest {
        // Should not throw
        taskService.reorderTasks(emptyList())
    }

    @Test
    fun `updateDisplayOrders updates only specified tasks`() = runTest {
        val date = LocalDate.of(2026, 3, 18)
        val task1 = taskService.createTask("Partial A", date)
        val task2 = taskService.createTask("Partial B", date)
        val task3 = taskService.createTask("Partial C", date)

        // Only reorder task1 and task3, leaving task2 untouched
        taskRepo.updateDisplayOrders(listOf(task3.id, task1.id))

        assertEquals(0, taskRepo.findById(task3.id)!!.displayOrder)
        assertEquals(1, taskRepo.findById(task1.id)!!.displayOrder)
        // task2 was not included, so it keeps its default order (0)
        assertEquals(0, taskRepo.findById(task2.id)!!.displayOrder)

        // Cleanup
        taskRepo.delete(task1.id)
        taskRepo.delete(task2.id)
        taskRepo.delete(task3.id)
    }
}
