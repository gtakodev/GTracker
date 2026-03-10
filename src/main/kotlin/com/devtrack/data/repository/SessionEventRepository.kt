package com.devtrack.data.repository

import com.devtrack.domain.model.SessionEvent
import java.util.UUID

/**
 * Repository for SessionEvent CRUD and queries (P1.2.1).
 */
interface SessionEventRepository {
    suspend fun findById(id: UUID): SessionEvent?
    suspend fun findBySessionId(sessionId: UUID): List<SessionEvent>
    suspend fun insert(event: SessionEvent)
    suspend fun update(event: SessionEvent)
    suspend fun delete(id: UUID)
    suspend fun deleteBySessionId(sessionId: UUID)
}
