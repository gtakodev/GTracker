package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.Tables.TasksTable
import com.devtrack.data.repository.TaskRepository
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TaskStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Exposed-based implementation of [TaskRepository] (P1.2.2).
 * All operations run inside suspended transactions.
 */
class TaskRepositoryImpl(
    private val databaseFactory: DatabaseFactory,
) : TaskRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    private fun ResultRow.toTask(): Task = Task(
        id = UUID.fromString(this[TasksTable.id]),
        parentId = this[TasksTable.parentId]?.let { UUID.fromString(it) },
        title = this[TasksTable.title],
        description = this[TasksTable.description],
        category = TaskCategory.valueOf(this[TasksTable.category]),
        jiraTickets = json.decodeFromString(stringListSerializer, this[TasksTable.jiraTickets]),
        status = TaskStatus.valueOf(this[TasksTable.status]),
        plannedDate = this[TasksTable.plannedDate]?.let { LocalDate.parse(it) },
        isTemplate = this[TasksTable.isTemplate],
        createdAt = Instant.parse(this[TasksTable.createdAt]),
        updatedAt = Instant.parse(this[TasksTable.updatedAt]),
        displayOrder = this[TasksTable.displayOrder],
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(db = databaseFactory.getDatabase()) { block() }

    override suspend fun findById(id: UUID): Task? = dbQuery {
        TasksTable.selectAll()
            .where { TasksTable.id eq id.toString() }
            .singleOrNull()
            ?.toTask()
    }

    override suspend fun findByDate(date: LocalDate): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where { (TasksTable.plannedDate eq date.toString()) and TasksTable.parentId.isNull() }
            .orderBy(TasksTable.displayOrder)
            .map { it.toTask() }
    }

    override suspend fun findByDateRange(startDate: LocalDate, endDate: LocalDate): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where {
                TasksTable.plannedDate.isNotNull() and
                    (TasksTable.plannedDate greaterEq startDate.toString()) and
                    (TasksTable.plannedDate lessEq endDate.toString()) and
                    TasksTable.parentId.isNull()
            }
            .orderBy(TasksTable.displayOrder)
            .map { it.toTask() }
    }

    override suspend fun findBacklog(): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where {
                TasksTable.plannedDate.isNull() and
                    (TasksTable.status neq TaskStatus.ARCHIVED.name) and
                    TasksTable.parentId.isNull()
            }
            .map { it.toTask() }
    }

    override suspend fun findByJiraTicket(ticket: String): List<Task> = dbQuery {
        // Search within the JSON array string for the ticket
        TasksTable.selectAll()
            .where { TasksTable.jiraTickets like "%\"$ticket\"%" }
            .map { it.toTask() }
    }

    override suspend fun findByParentId(parentId: UUID): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where { TasksTable.parentId eq parentId.toString() }
            .map { it.toTask() }
    }

    override suspend fun findByStatus(status: TaskStatus): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where { TasksTable.status eq status.name }
            .map { it.toTask() }
    }

    override suspend fun findAll(): List<Task> = dbQuery {
        TasksTable.selectAll()
            .where { (TasksTable.status neq TaskStatus.ARCHIVED.name) and TasksTable.parentId.isNull() }
            .map { it.toTask() }
    }

    override suspend fun search(query: String): List<Task> = dbQuery {
        val pattern = "%${query.lowercase()}%"
        TasksTable.selectAll()
            .where {
                (TasksTable.title.lowerCase() like pattern) or
                    (TasksTable.jiraTickets.lowerCase() like pattern) or
                    (TasksTable.description.lowerCase() like pattern)
            }
            .map { it.toTask() }
    }

    override suspend fun insert(task: Task): Unit = dbQuery {
        TasksTable.insert {
            it[id] = task.id.toString()
            it[parentId] = task.parentId?.toString()
            it[title] = task.title
            it[description] = task.description
            it[category] = task.category.name
            it[jiraTickets] = json.encodeToString(stringListSerializer, task.jiraTickets)
            it[status] = task.status.name
            it[plannedDate] = task.plannedDate?.toString()
            it[isTemplate] = task.isTemplate
            it[createdAt] = task.createdAt.toString()
            it[updatedAt] = task.updatedAt.toString()
            it[displayOrder] = task.displayOrder
        }
    }

    override suspend fun update(task: Task): Unit = dbQuery {
        TasksTable.update({ TasksTable.id eq task.id.toString() }) {
            it[parentId] = task.parentId?.toString()
            it[title] = task.title
            it[description] = task.description
            it[category] = task.category.name
            it[jiraTickets] = json.encodeToString(stringListSerializer, task.jiraTickets)
            it[status] = task.status.name
            it[plannedDate] = task.plannedDate?.toString()
            it[isTemplate] = task.isTemplate
            it[updatedAt] = task.updatedAt.toString()
            it[displayOrder] = task.displayOrder
        }
    }

    override suspend fun delete(id: UUID): Unit = dbQuery {
        TasksTable.deleteWhere { TasksTable.id eq id.toString() }
    }

    override suspend fun updateDisplayOrders(orderedIds: List<UUID>): Unit = dbQuery {
        orderedIds.forEachIndexed { index, taskId ->
            TasksTable.update({ TasksTable.id eq taskId.toString() }) {
                it[displayOrder] = index
            }
        }
    }
}
