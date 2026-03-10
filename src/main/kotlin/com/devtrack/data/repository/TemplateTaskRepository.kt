package com.devtrack.data.repository

import com.devtrack.domain.model.TemplateTask
import java.util.UUID

/**
 * Repository for TemplateTask CRUD and queries (P1.2.1).
 */
interface TemplateTaskRepository {
    suspend fun findById(id: UUID): TemplateTask?
    suspend fun findAll(): List<TemplateTask>
    suspend fun insert(template: TemplateTask)
    suspend fun update(template: TemplateTask)
    suspend fun delete(id: UUID)
}
