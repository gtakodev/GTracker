package com.devtrack.data.repository

import com.devtrack.domain.model.WorkSession
import java.time.LocalDate
import java.util.UUID

/**
 * Repository for WorkSession CRUD and queries (P1.2.1).
 */
interface WorkSessionRepository {
    suspend fun findById(id: UUID): WorkSession?
    suspend fun findByTaskId(taskId: UUID): List<WorkSession>
    suspend fun findByDate(date: LocalDate): List<WorkSession>
    suspend fun findByDateRange(startDate: LocalDate, endDate: LocalDate): List<WorkSession>
    suspend fun findOrphans(): List<WorkSession>
    suspend fun insert(session: WorkSession)
    suspend fun update(session: WorkSession)
    suspend fun delete(id: UUID)
}
