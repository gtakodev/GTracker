package com.devtrack.domain.service

import com.devtrack.data.repository.TemplateTaskRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TemplateTask
import com.devtrack.infrastructure.logging.AuditLogger
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for TemplateService (P4.2.5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateServiceTest {

    private val templateRepository = mockk<TemplateTaskRepository>(relaxed = true)
    private val taskService = mockk<TaskService>(relaxed = true)
    private val auditLogger = mockk<AuditLogger>(relaxed = true)

    private lateinit var service: TemplateService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service = TemplateService(templateRepository, taskService, auditLogger)
    }

    @Test
    fun `getAllTemplates returns all templates`() = runTest {
        val templates = listOf(
            TemplateTask(title = "Daily standup", category = TaskCategory.MEETING),
            TemplateTask(title = "Code review", category = TaskCategory.REVIEW),
        )
        coEvery { templateRepository.findAll() } returns templates

        val result = service.getAllTemplates()

        assertEquals(2, result.size)
        assertEquals("Daily standup", result[0].title)
    }

    @Test
    fun `createTemplate inserts and returns template`() = runTest {
        val slot = slot<TemplateTask>()
        coEvery { templateRepository.insert(capture(slot)) } just runs

        val result = service.createTemplate("Sprint planning", TaskCategory.MEETING, 60)

        assertEquals("Sprint planning", result.title)
        assertEquals(TaskCategory.MEETING, result.category)
        assertEquals(60, result.defaultDurationMin)
        assertEquals(result, slot.captured)
    }

    @Test
    fun `updateTemplate calls repository update`() = runTest {
        val template = TemplateTask(title = "Updated", category = TaskCategory.DEVELOPMENT)
        coEvery { templateRepository.update(template) } just runs

        val result = service.updateTemplate(template)

        assertEquals(template, result)
        coVerify { templateRepository.update(template) }
    }

    @Test
    fun `deleteTemplate calls repository delete`() = runTest {
        val id = UUID.randomUUID()
        coEvery { templateRepository.delete(id) } just runs

        service.deleteTemplate(id)

        coVerify { templateRepository.delete(id) }
    }

    @Test
    fun `findByName returns matching templates case-insensitively`() = runTest {
        val templates = listOf(
            TemplateTask(title = "Daily standup", category = TaskCategory.MEETING),
            TemplateTask(title = "Code review", category = TaskCategory.REVIEW),
            TemplateTask(title = "Daily report", category = TaskCategory.DOCUMENTATION),
        )
        coEvery { templateRepository.findAll() } returns templates

        val result = service.findByName("daily")

        assertEquals(2, result.size)
        assertTrue(result.all { it.title.lowercase().contains("daily") })
    }

    @Test
    fun `instantiate creates task from template`() = runTest {
        val template = TemplateTask(
            title = "Daily standup",
            category = TaskCategory.MEETING,
            defaultDurationMin = 15,
        )
        val createdTask = Task(title = "Daily standup", category = TaskCategory.DEVELOPMENT)
        val updatedTask = createdTask.copy(category = TaskCategory.MEETING)

        coEvery { templateRepository.findById(template.id) } returns template
        coEvery { taskService.createTask("Daily standup", plannedDate = LocalDate.now()) } returns createdTask
        coEvery { taskService.updateTask(any()) } returns updatedTask

        val result = service.instantiate(template.id, LocalDate.now())

        assertEquals(TaskCategory.MEETING, result.category)
        coVerify { taskService.createTask("Daily standup", plannedDate = LocalDate.now()) }
        coVerify { taskService.updateTask(match { it.category == TaskCategory.MEETING }) }
    }

    @Test
    fun `instantiate throws for unknown template ID`() = runTest {
        val unknownId = UUID.randomUUID()
        coEvery { templateRepository.findById(unknownId) } returns null

        assertThrows<IllegalArgumentException> {
            service.instantiate(unknownId, LocalDate.now())
        }
    }

    @Test
    fun `instantiateByName returns null when no matching template`() = runTest {
        coEvery { templateRepository.findAll() } returns emptyList()

        val result = service.instantiateByName("nonexistent")

        assertNull(result)
    }

    @Test
    fun `instantiateByName creates task from first matching template`() = runTest {
        val template = TemplateTask(title = "Code review", category = TaskCategory.REVIEW, defaultDurationMin = 30)
        val createdTask = Task(title = "Code review", category = TaskCategory.DEVELOPMENT)
        val updatedTask = createdTask.copy(category = TaskCategory.REVIEW)

        coEvery { templateRepository.findAll() } returns listOf(template)
        coEvery { templateRepository.findById(template.id) } returns template
        coEvery { taskService.createTask("Code review", plannedDate = LocalDate.now()) } returns createdTask
        coEvery { taskService.updateTask(any()) } returns updatedTask

        val result = service.instantiateByName("code review")

        assertNotNull(result)
        assertEquals(TaskCategory.REVIEW, result!!.category)
    }

    @Test
    fun `seedDefaultTemplates does nothing when templates exist`() = runTest {
        coEvery { templateRepository.findAll() } returns listOf(
            TemplateTask(title = "Existing", category = TaskCategory.DEVELOPMENT),
        )

        service.seedDefaultTemplates()

        coVerify(exactly = 0) { templateRepository.insert(any()) }
    }

    @Test
    fun `seedDefaultTemplates inserts 3 defaults when empty`() = runTest {
        coEvery { templateRepository.findAll() } returns emptyList()

        service.seedDefaultTemplates()

        coVerify(exactly = 3) { templateRepository.insert(any()) }
        coVerify { templateRepository.insert(match { it.title == "Daily standup" && it.category == TaskCategory.MEETING }) }
        coVerify { templateRepository.insert(match { it.title == "Code review" && it.category == TaskCategory.REVIEW }) }
        coVerify { templateRepository.insert(match { it.title == "Weekly report" && it.category == TaskCategory.DOCUMENTATION }) }
    }
}
