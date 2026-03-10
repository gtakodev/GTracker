package com.devtrack.data.repository

import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskStatus
import java.time.LocalDate
import java.util.UUID

/**
 * Repository for Task CRUD and queries (P1.2.1).
 */
interface TaskRepository {
    suspend fun findById(id: UUID): Task?
    suspend fun findByDate(date: LocalDate): List<Task>
    suspend fun findByDateRange(startDate: LocalDate, endDate: LocalDate): List<Task>
    suspend fun findBacklog(): List<Task>
    suspend fun findByJiraTicket(ticket: String): List<Task>
    suspend fun findByParentId(parentId: UUID): List<Task>
    suspend fun findByStatus(status: TaskStatus): List<Task>
    suspend fun findAll(): List<Task>
    suspend fun search(query: String): List<Task>
    suspend fun insert(task: Task)
    suspend fun update(task: Task)
    suspend fun delete(id: UUID)
    suspend fun updateDisplayOrders(orderedIds: List<UUID>)
}
