package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.Tables.WorkSessionsTable
import com.devtrack.data.repository.WorkSessionRepository
import com.devtrack.domain.model.SessionSource
import com.devtrack.domain.model.WorkSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Exposed-based implementation of [WorkSessionRepository] (P1.2.3).
 */
class WorkSessionRepositoryImpl(
    private val databaseFactory: DatabaseFactory,
) : WorkSessionRepository {

    private fun ResultRow.toWorkSession(): WorkSession = WorkSession(
        id = UUID.fromString(this[WorkSessionsTable.id]),
        taskId = UUID.fromString(this[WorkSessionsTable.taskId]),
        date = LocalDate.parse(this[WorkSessionsTable.date]),
        startTime = Instant.parse(this[WorkSessionsTable.startTime]),
        endTime = this[WorkSessionsTable.endTime]?.let { Instant.parse(it) },
        source = SessionSource.valueOf(this[WorkSessionsTable.sessionSource]),
        notes = this[WorkSessionsTable.notes],
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(db = databaseFactory.getDatabase()) { block() }

    override suspend fun findById(id: UUID): WorkSession? = dbQuery {
        WorkSessionsTable.selectAll()
            .where { WorkSessionsTable.id eq id.toString() }
            .singleOrNull()
            ?.toWorkSession()
    }

    override suspend fun findByTaskId(taskId: UUID): List<WorkSession> = dbQuery {
        WorkSessionsTable.selectAll()
            .where { WorkSessionsTable.taskId eq taskId.toString() }
            .map { it.toWorkSession() }
    }

    override suspend fun findByDate(date: LocalDate): List<WorkSession> = dbQuery {
        WorkSessionsTable.selectAll()
            .where { WorkSessionsTable.date eq date.toString() }
            .map { it.toWorkSession() }
    }

    override suspend fun findByDateRange(startDate: LocalDate, endDate: LocalDate): List<WorkSession> = dbQuery {
        WorkSessionsTable.selectAll()
            .where {
                (WorkSessionsTable.date greaterEq startDate.toString()) and
                    (WorkSessionsTable.date lessEq endDate.toString())
            }
            .map { it.toWorkSession() }
    }

    override suspend fun findOrphans(): List<WorkSession> = dbQuery {
        WorkSessionsTable.selectAll()
            .where { WorkSessionsTable.endTime.isNull() }
            .map { it.toWorkSession() }
    }

    override suspend fun insert(session: WorkSession): Unit = dbQuery {
        WorkSessionsTable.insert {
            it[id] = session.id.toString()
            it[taskId] = session.taskId.toString()
            it[date] = session.date.toString()
            it[startTime] = session.startTime.toString()
            it[endTime] = session.endTime?.toString()
            it[sessionSource] = session.source.name
            it[notes] = session.notes
        }
    }

    override suspend fun update(session: WorkSession): Unit = dbQuery {
        WorkSessionsTable.update({ WorkSessionsTable.id eq session.id.toString() }) {
            it[taskId] = session.taskId.toString()
            it[date] = session.date.toString()
            it[startTime] = session.startTime.toString()
            it[endTime] = session.endTime?.toString()
            it[sessionSource] = session.source.name
            it[notes] = session.notes
        }
    }

    override suspend fun delete(id: UUID): Unit = dbQuery {
        WorkSessionsTable.deleteWhere { WorkSessionsTable.id eq id.toString() }
    }
}
