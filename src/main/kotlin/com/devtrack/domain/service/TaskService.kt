package com.devtrack.domain.service

import com.devtrack.data.repository.TaskRepository
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TaskLevel
import com.devtrack.domain.model.TaskStatus
import com.devtrack.infrastructure.logging.AuditLogger
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Business service for task operations (P1.3.3).
 * Coordinates between JiraTicketParser, repositories, and audit logging.
 */
class TaskService(
    private val taskRepository: TaskRepository,
    private val sessionService: SessionService,
    private val jiraTicketParser: JiraTicketParser,
    private val auditLogger: AuditLogger,
) {

    /**
     * Create a new task with automatic Jira ticket extraction and category detection.
     */
    suspend fun createTask(title: String, plannedDate: LocalDate? = null): Task {
        val tickets = jiraTicketParser.extractTickets(title)
        val detectedCategory = jiraTicketParser.extractCategory(title)
        val cleanedTitle = jiraTicketParser.cleanTitle(title)

        val task = Task(
            title = cleanedTitle,
            jiraTickets = tickets,
            category = detectedCategory ?: TaskCategory.DEVELOPMENT,
            plannedDate = plannedDate,
        )

        taskRepository.insert(task)
        auditLogger.logUserAction("CREATE", "TASK", task.id.toString(),
            mapOf("tickets" to tickets.joinToString(","), "category" to task.category.name))
        return task
    }

    /**
     * Update an existing task. Re-parses Jira tickets if the title changed.
     */
    suspend fun updateTask(task: Task): Task {
        val existing = taskRepository.findById(task.id)
        val updatedTask = if (existing != null && existing.title != task.title) {
            // Re-parse tickets and category from new title
            val tickets = jiraTicketParser.extractTickets(task.title)
            val detectedCategory = jiraTicketParser.extractCategory(task.title)
            val cleanedTitle = jiraTicketParser.cleanTitle(task.title)
            task.copy(
                title = cleanedTitle,
                jiraTickets = tickets,
                category = detectedCategory ?: task.category,
                updatedAt = Instant.now(),
            )
        } else {
            task.copy(updatedAt = Instant.now())
        }

        taskRepository.update(updatedTask)
        auditLogger.logUserAction("UPDATE", "TASK", task.id.toString())
        return updatedTask
    }

    /**
     * Delete a task. Stops the active session if it's linked to this task.
     */
    suspend fun deleteTask(id: UUID) {
        // Stop active session if linked to this task
        val active = sessionService.getActiveSession()
        if (active != null && active.task.id == id) {
            sessionService.stopSession(active.session.id)
        }

        taskRepository.delete(id) // cascade deletes sessions + events
        auditLogger.logUserAction("DELETE", "TASK", id.toString())
    }

    /**
     * Change the status of a task.
     */
    suspend fun changeStatus(id: UUID, status: TaskStatus) {
        val task = taskRepository.findById(id) ?: return
        val updated = task.copy(status = status, updatedAt = Instant.now())
        taskRepository.update(updated)
        auditLogger.logUserAction("CHANGE_STATUS", "TASK", id.toString(),
            mapOf("status" to status.name))
    }

    /**
     * Plan a task for a specific date.
     */
    suspend fun planTask(id: UUID, date: LocalDate) {
        val task = taskRepository.findById(id) ?: return
        val updated = task.copy(plannedDate = date, updatedAt = Instant.now())
        taskRepository.update(updated)
        auditLogger.logUserAction("PLAN", "TASK", id.toString(),
            mapOf("date" to date.toString()))
    }

    /**
     * Remove a task from any planned date (send back to backlog).
     */
    suspend fun unplanTask(id: UUID) {
        val task = taskRepository.findById(id) ?: return
        val updated = task.copy(plannedDate = null, updatedAt = Instant.now())
        taskRepository.update(updated)
        auditLogger.logUserAction("UNPLAN", "TASK", id.toString())
    }

    /**
     * Update the display order of tasks (P4.1.1).
     * Persists the order from the given list of task IDs.
     */
    suspend fun reorderTasks(orderedIds: List<UUID>) {
        taskRepository.updateDisplayOrders(orderedIds)
        auditLogger.logUserAction("REORDER", "TASK", "",
            mapOf("count" to orderedIds.size.toString()))
    }

    /**
     * Move a task to a different date (for Calendar drag & drop) (P4.1.2).
     */
    suspend fun moveTaskToDate(taskId: UUID, targetDate: LocalDate) {
        val task = taskRepository.findById(taskId) ?: return
        val updated = task.copy(plannedDate = targetDate, updatedAt = Instant.now())
        taskRepository.update(updated)
        auditLogger.logUserAction("MOVE_TO_DATE", "TASK", taskId.toString(),
            mapOf("date" to targetDate.toString()))
    }

    // -- Sub-task operations (P2.5) --

    /**
     * Create a sub-task under the given parent.
     * Depth is limited to 1: the parent must NOT itself be a sub-task.
     * Inherits the parent's plannedDate if none is specified.
     */
    suspend fun createSubTask(parentId: UUID, title: String): Task {
        val parent = taskRepository.findById(parentId)
            ?: throw IllegalArgumentException("Parent task not found: $parentId")

        if (parent.parentId != null) {
            throw IllegalArgumentException("Cannot create a sub-task of a sub-task (max depth is 1)")
        }

        val tickets = jiraTicketParser.extractTickets(title)
        val detectedCategory = jiraTicketParser.extractCategory(title)
        val cleanedTitle = jiraTicketParser.cleanTitle(title)

        val subTask = Task(
            parentId = parentId,
            title = cleanedTitle,
            jiraTickets = tickets,
            category = detectedCategory ?: parent.category,
            plannedDate = parent.plannedDate,
        )

        taskRepository.insert(subTask)
        auditLogger.logUserAction("CREATE_SUBTASK", "TASK", subTask.id.toString(),
            mapOf("parentId" to parentId.toString(), "tickets" to tickets.joinToString(",")))
        return subTask
    }

    /**
     * Get all sub-tasks for the given parent task.
     */
    suspend fun getSubTasks(parentId: UUID): List<Task> {
        return taskRepository.findByParentId(parentId)
    }

    /**
     * Determine the level of a task based on its state (P2.2.1).
     * - ACTIVE: status is IN_PROGRESS (has a running timer)
     * - PLANNED: has a planned date
     * - BACKLOG: no planned date and not archived
     */
    fun getTaskLevel(task: Task): TaskLevel {
        return when {
            task.status == TaskStatus.IN_PROGRESS -> TaskLevel.ACTIVE
            task.plannedDate != null -> TaskLevel.PLANNED
            else -> TaskLevel.BACKLOG
        }
    }

    /**
     * Determine the level of a task based on its state,
     * also considering whether a session is currently active for this task.
     */
    fun getTaskLevel(task: Task, activeTaskId: UUID?): TaskLevel {
        return when {
            task.id == activeTaskId -> TaskLevel.ACTIVE
            task.status == TaskStatus.IN_PROGRESS -> TaskLevel.ACTIVE
            task.plannedDate != null -> TaskLevel.PLANNED
            else -> TaskLevel.BACKLOG
        }
    }
}
