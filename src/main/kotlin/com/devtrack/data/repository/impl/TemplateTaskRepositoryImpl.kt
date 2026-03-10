package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.Tables.TemplateTasksTable
import com.devtrack.data.repository.TemplateTaskRepository
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TemplateTask
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/**
 * Exposed-based implementation of [TemplateTaskRepository] (P1.2.5).
 */
class TemplateTaskRepositoryImpl(
    private val databaseFactory: DatabaseFactory,
) : TemplateTaskRepository {

    private fun ResultRow.toTemplateTask(): TemplateTask = TemplateTask(
        id = UUID.fromString(this[TemplateTasksTable.id]),
        title = this[TemplateTasksTable.title],
        category = TaskCategory.valueOf(this[TemplateTasksTable.category]),
        defaultDurationMin = this[TemplateTasksTable.defaultDurationMin],
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(db = databaseFactory.getDatabase()) { block() }

    override suspend fun findById(id: UUID): TemplateTask? = dbQuery {
        TemplateTasksTable.selectAll()
            .where { TemplateTasksTable.id eq id.toString() }
            .singleOrNull()
            ?.toTemplateTask()
    }

    override suspend fun findAll(): List<TemplateTask> = dbQuery {
        TemplateTasksTable.selectAll()
            .map { it.toTemplateTask() }
    }

    override suspend fun insert(template: TemplateTask): Unit = dbQuery {
        TemplateTasksTable.insert {
            it[id] = template.id.toString()
            it[title] = template.title
            it[category] = template.category.name
            it[defaultDurationMin] = template.defaultDurationMin
        }
    }

    override suspend fun update(template: TemplateTask): Unit = dbQuery {
        TemplateTasksTable.update({ TemplateTasksTable.id eq template.id.toString() }) {
            it[title] = template.title
            it[category] = template.category.name
            it[defaultDurationMin] = template.defaultDurationMin
        }
    }

    override suspend fun delete(id: UUID): Unit = dbQuery {
        TemplateTasksTable.deleteWhere { TemplateTasksTable.id eq id.toString() }
    }
}
