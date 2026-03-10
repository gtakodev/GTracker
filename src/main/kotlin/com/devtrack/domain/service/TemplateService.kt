package com.devtrack.domain.service

import com.devtrack.data.repository.TemplateTaskRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TemplateTask
import com.devtrack.infrastructure.logging.AuditLogger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

/**
 * Business service for template operations (P4.2.1).
 * CRUD on templates and instantiation into real tasks for a given day.
 */
class TemplateService(
    private val templateRepository: TemplateTaskRepository,
    private val taskService: TaskService,
    private val auditLogger: AuditLogger,
) {
    private val logger = LoggerFactory.getLogger(TemplateService::class.java)

    /** Get all templates. */
    suspend fun getAllTemplates(): List<TemplateTask> {
        return templateRepository.findAll()
    }

    /** Find a template by its ID. */
    suspend fun findById(id: UUID): TemplateTask? {
        return templateRepository.findById(id)
    }

    /** Find templates by name (case-insensitive partial match). */
    suspend fun findByName(name: String): List<TemplateTask> {
        val lowerName = name.lowercase()
        return templateRepository.findAll().filter {
            it.title.lowercase().contains(lowerName)
        }
    }

    /** Create a new template. */
    suspend fun createTemplate(
        title: String,
        category: TaskCategory,
        defaultDurationMin: Int? = null,
    ): TemplateTask {
        val template = TemplateTask(
            title = title,
            category = category,
            defaultDurationMin = defaultDurationMin,
        )
        templateRepository.insert(template)
        auditLogger.logUserAction("CREATE", "TEMPLATE", template.id.toString(),
            mapOf("title" to title, "category" to category.name))
        return template
    }

    /** Update an existing template. */
    suspend fun updateTemplate(template: TemplateTask): TemplateTask {
        templateRepository.update(template)
        auditLogger.logUserAction("UPDATE", "TEMPLATE", template.id.toString())
        return template
    }

    /** Delete a template. */
    suspend fun deleteTemplate(id: UUID) {
        templateRepository.delete(id)
        auditLogger.logUserAction("DELETE", "TEMPLATE", id.toString())
    }

    /**
     * Instantiate a template for a given date.
     * Creates a real Task with the template's title and category, planned for [date].
     */
    suspend fun instantiate(templateId: UUID, date: LocalDate): Task {
        val template = templateRepository.findById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        return instantiate(template, date)
    }

    /**
     * Instantiate a template for a given date.
     * Creates a real Task with the template's title and category, planned for [date].
     */
    suspend fun instantiate(template: TemplateTask, date: LocalDate): Task {
        val task = taskService.createTask(template.title, plannedDate = date)
        // If the category should be overridden (createTask auto-detects, but template has an explicit one)
        val updated = task.copy(category = template.category)
        taskService.updateTask(updated)
        auditLogger.logUserAction("INSTANTIATE", "TEMPLATE", template.id.toString(),
            mapOf("taskId" to task.id.toString(), "date" to date.toString()))
        logger.info("Instantiated template '{}' as task {} for {}", template.title, task.id, date)
        return updated
    }

    /**
     * Instantiate a template found by name for today.
     * Returns null if no matching template is found.
     */
    suspend fun instantiateByName(name: String, date: LocalDate = LocalDate.now()): Task? {
        val templates = findByName(name)
        val template = templates.firstOrNull() ?: return null
        return instantiate(template, date)
    }

    /**
     * Seed default templates if the template table is empty (P4.2.3).
     * Called once at application startup.
     */
    suspend fun seedDefaultTemplates() {
        val existing = templateRepository.findAll()
        if (existing.isNotEmpty()) return

        val defaults = listOf(
            TemplateTask(title = "Daily standup", category = TaskCategory.MEETING, defaultDurationMin = 15),
            TemplateTask(title = "Code review", category = TaskCategory.REVIEW, defaultDurationMin = 30),
            TemplateTask(title = "Weekly report", category = TaskCategory.DOCUMENTATION, defaultDurationMin = 30),
        )
        defaults.forEach { templateRepository.insert(it) }
        logger.info("Seeded {} default templates", defaults.size)
    }
}
