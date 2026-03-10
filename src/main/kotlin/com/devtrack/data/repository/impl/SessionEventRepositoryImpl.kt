package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.Tables.SessionEventsTable
import com.devtrack.data.repository.SessionEventRepository
import com.devtrack.domain.model.EventType
import com.devtrack.domain.model.SessionEvent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

/**
 * Exposed-based implementation of [SessionEventRepository] (P1.2.4).
 * Events are always returned ordered by timestamp ASC.
 */
class SessionEventRepositoryImpl(
    private val databaseFactory: DatabaseFactory,
) : SessionEventRepository {

    private fun ResultRow.toSessionEvent(): SessionEvent = SessionEvent(
        id = UUID.fromString(this[SessionEventsTable.id]),
        sessionId = UUID.fromString(this[SessionEventsTable.sessionId]),
        type = EventType.valueOf(this[SessionEventsTable.type]),
        timestamp = Instant.parse(this[SessionEventsTable.timestamp]),
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(db = databaseFactory.getDatabase()) { block() }

    override suspend fun findById(id: UUID): SessionEvent? = dbQuery {
        SessionEventsTable.selectAll()
            .where { SessionEventsTable.id eq id.toString() }
            .singleOrNull()
            ?.toSessionEvent()
    }

    override suspend fun findBySessionId(sessionId: UUID): List<SessionEvent> = dbQuery {
        SessionEventsTable.selectAll()
            .where { SessionEventsTable.sessionId eq sessionId.toString() }
            .orderBy(SessionEventsTable.timestamp to SortOrder.ASC)
            .map { it.toSessionEvent() }
    }

    override suspend fun insert(event: SessionEvent): Unit = dbQuery {
        SessionEventsTable.insert {
            it[id] = event.id.toString()
            it[sessionId] = event.sessionId.toString()
            it[type] = event.type.name
            it[timestamp] = event.timestamp.toString()
        }
    }

    override suspend fun update(event: SessionEvent): Unit = dbQuery {
        SessionEventsTable.update({ SessionEventsTable.id eq event.id.toString() }) {
            it[type] = event.type.name
            it[timestamp] = event.timestamp.toString()
        }
    }

    override suspend fun delete(id: UUID): Unit = dbQuery {
        SessionEventsTable.deleteWhere { SessionEventsTable.id eq id.toString() }
    }

    override suspend fun deleteBySessionId(sessionId: UUID): Unit = dbQuery {
        SessionEventsTable.deleteWhere { SessionEventsTable.sessionId eq sessionId.toString() }
    }
}
