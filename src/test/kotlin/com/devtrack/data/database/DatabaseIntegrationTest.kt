package com.devtrack.data.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {

    private lateinit var databaseFactory: DatabaseFactory

    @BeforeAll
    fun setup() {
        databaseFactory = DatabaseFactory()
        databaseFactory.initInMemory()

        // Run migrations to create tables
        val migrationManager = MigrationManager(databaseFactory)
        migrationManager.migrate()
    }

    @AfterAll
    fun teardown() {
        databaseFactory.close()
    }

    @Test
    fun `should connect to in-memory database`() {
        val db = databaseFactory.getDatabase()
        assertNotNull(db)
    }

    @Test
    fun `should create all tables via migration`() {
        transaction(databaseFactory.getDatabase()) {
            val taskCount = Tables.TasksTable.selectAll().count()
            assertEquals(0, taskCount)

            val sessionCount = Tables.WorkSessionsTable.selectAll().count()
            assertEquals(0, sessionCount)

            val eventCount = Tables.SessionEventsTable.selectAll().count()
            assertEquals(0, eventCount)

            val templateCount = Tables.TemplateTasksTable.selectAll().count()
            assertEquals(0, templateCount)

            val settingsCount = Tables.UserSettingsTable.selectAll().count()
            assertEquals(0, settingsCount)
        }
    }

    @Test
    fun `should track schema version after migration`() {
        transaction(databaseFactory.getDatabase()) {
            val versions = SchemaVersionTable.selectAll().toList()
            assertTrue(versions.isNotEmpty())
            assertEquals(3, versions.maxOf { it[SchemaVersionTable.version] })
        }
    }

    @Test
    fun `should insert and query a task`() {
        val id = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()

        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.insert {
                it[Tables.TasksTable.id] = id
                it[Tables.TasksTable.title] = "Test task"
                it[Tables.TasksTable.category] = "DEVELOPMENT"
                it[Tables.TasksTable.status] = "TODO"
                it[Tables.TasksTable.jiraTickets] = "[\"TEST-123\"]"
                it[Tables.TasksTable.createdAt] = now
                it[Tables.TasksTable.updatedAt] = now
            }
        }

        transaction(databaseFactory.getDatabase()) {
            val task = Tables.TasksTable.selectAll()
                .where { Tables.TasksTable.id eq id }
                .single()

            assertEquals("Test task", task[Tables.TasksTable.title])
            assertEquals("DEVELOPMENT", task[Tables.TasksTable.category])
            assertEquals("TODO", task[Tables.TasksTable.status])
            assertEquals("[\"TEST-123\"]", task[Tables.TasksTable.jiraTickets])
            assertNull(task[Tables.TasksTable.parentId])
            assertNull(task[Tables.TasksTable.plannedDate])
        }

        // Cleanup
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.deleteWhere { Tables.TasksTable.id eq id }
        }
    }

    @Test
    fun `should enforce foreign key constraint on work sessions`() {
        assertThrows<Exception> {
            transaction(databaseFactory.getDatabase()) {
                Tables.WorkSessionsTable.insert {
                    it[Tables.WorkSessionsTable.id] = UUID.randomUUID().toString()
                    it[Tables.WorkSessionsTable.taskId] = "nonexistent-task-id"
                    it[Tables.WorkSessionsTable.date] = "2026-03-09"
                    it[Tables.WorkSessionsTable.startTime] = java.time.Instant.now().toString()
                    it[Tables.WorkSessionsTable.sessionSource] = "TIMER"
                }
            }
        }
    }

    @Test
    fun `should cascade delete sessions when task is deleted`() {
        val tId = UUID.randomUUID().toString()
        val sId = UUID.randomUUID().toString()
        val eId = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()

        // Create task
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.insert {
                it[Tables.TasksTable.id] = tId
                it[Tables.TasksTable.title] = "Task to delete"
                it[Tables.TasksTable.category] = "BUGFIX"
                it[Tables.TasksTable.status] = "IN_PROGRESS"
                it[Tables.TasksTable.jiraTickets] = "[]"
                it[Tables.TasksTable.createdAt] = now
                it[Tables.TasksTable.updatedAt] = now
            }
        }

        // Create session for the task
        transaction(databaseFactory.getDatabase()) {
            Tables.WorkSessionsTable.insert {
                it[Tables.WorkSessionsTable.id] = sId
                it[Tables.WorkSessionsTable.taskId] = tId
                it[Tables.WorkSessionsTable.date] = "2026-03-09"
                it[Tables.WorkSessionsTable.startTime] = now
                it[Tables.WorkSessionsTable.sessionSource] = "TIMER"
            }
        }

        // Create event for the session
        transaction(databaseFactory.getDatabase()) {
            Tables.SessionEventsTable.insert {
                it[Tables.SessionEventsTable.id] = eId
                it[Tables.SessionEventsTable.sessionId] = sId
                it[Tables.SessionEventsTable.type] = "START"
                it[Tables.SessionEventsTable.timestamp] = now
            }
        }

        // Verify all exist
        transaction(databaseFactory.getDatabase()) {
            assertEquals(1, Tables.WorkSessionsTable.selectAll()
                .where { Tables.WorkSessionsTable.id eq sId }.count())
            assertEquals(1, Tables.SessionEventsTable.selectAll()
                .where { Tables.SessionEventsTable.id eq eId }.count())
        }

        // Delete the task
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.deleteWhere { Tables.TasksTable.id eq tId }
        }

        // Verify cascade delete
        transaction(databaseFactory.getDatabase()) {
            assertEquals(0, Tables.WorkSessionsTable.selectAll()
                .where { Tables.WorkSessionsTable.id eq sId }.count())
            assertEquals(0, Tables.SessionEventsTable.selectAll()
                .where { Tables.SessionEventsTable.id eq eId }.count())
        }
    }

    @Test
    fun `should insert and query user settings`() {
        val id = UUID.randomUUID().toString()

        transaction(databaseFactory.getDatabase()) {
            Tables.UserSettingsTable.insert {
                it[Tables.UserSettingsTable.id] = id
                it[Tables.UserSettingsTable.locale] = "en"
                it[Tables.UserSettingsTable.theme] = "DARK"
                it[Tables.UserSettingsTable.inactivityThresholdMin] = 45
                it[Tables.UserSettingsTable.hoursPerDay] = 7.5
                it[Tables.UserSettingsTable.halfDayThreshold] = 3.5
            }
        }

        transaction(databaseFactory.getDatabase()) {
            val settings = Tables.UserSettingsTable.selectAll()
                .where { Tables.UserSettingsTable.id eq id }
                .single()

            assertEquals("en", settings[Tables.UserSettingsTable.locale])
            assertEquals("DARK", settings[Tables.UserSettingsTable.theme])
            assertEquals(45, settings[Tables.UserSettingsTable.inactivityThresholdMin])
            assertEquals(7.5, settings[Tables.UserSettingsTable.hoursPerDay])
            assertEquals(3.5, settings[Tables.UserSettingsTable.halfDayThreshold])
            assertEquals(25, settings[Tables.UserSettingsTable.pomodoroWorkMin])
            assertEquals(5, settings[Tables.UserSettingsTable.pomodoroBreakMin])
        }

        // Cleanup
        transaction(databaseFactory.getDatabase()) {
            Tables.UserSettingsTable.deleteWhere { Tables.UserSettingsTable.id eq id }
        }
    }

    @Test
    fun `should support parent-child task relationship`() {
        val pId = UUID.randomUUID().toString()
        val cId = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()

        // Create parent task
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.insert {
                it[Tables.TasksTable.id] = pId
                it[Tables.TasksTable.title] = "Parent task"
                it[Tables.TasksTable.category] = "DEVELOPMENT"
                it[Tables.TasksTable.status] = "TODO"
                it[Tables.TasksTable.jiraTickets] = "[]"
                it[Tables.TasksTable.createdAt] = now
                it[Tables.TasksTable.updatedAt] = now
            }
        }

        // Create child task
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.insert {
                it[Tables.TasksTable.id] = cId
                it[Tables.TasksTable.parentId] = pId
                it[Tables.TasksTable.title] = "Child task"
                it[Tables.TasksTable.category] = "DEVELOPMENT"
                it[Tables.TasksTable.status] = "TODO"
                it[Tables.TasksTable.jiraTickets] = "[]"
                it[Tables.TasksTable.createdAt] = now
                it[Tables.TasksTable.updatedAt] = now
            }
        }

        // Verify relationship
        transaction(databaseFactory.getDatabase()) {
            val child = Tables.TasksTable.selectAll()
                .where { Tables.TasksTable.id eq cId }
                .single()
            assertEquals(pId, child[Tables.TasksTable.parentId])
        }

        // Delete parent should cascade delete child
        transaction(databaseFactory.getDatabase()) {
            Tables.TasksTable.deleteWhere { Tables.TasksTable.id eq pId }
        }

        transaction(databaseFactory.getDatabase()) {
            assertEquals(0, Tables.TasksTable.selectAll()
                .where { Tables.TasksTable.id eq cId }.count())
        }
    }

    @Test
    fun `should run migrations idempotently`() {
        val migrationManager = MigrationManager(databaseFactory)
        assertDoesNotThrow {
            migrationManager.migrate()
        }
    }
}
