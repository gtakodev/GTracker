package com.devtrack.data.database

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Schema version tracking table.
 */
object SchemaVersionTable : Table("schema_version") {
    val version = integer("version")
    val appliedAt = varchar("applied_at", 50)
    val description = varchar("description", 255)
}

/**
 * Represents a single database migration.
 */
data class Migration(
    val version: Int,
    val description: String,
    val migrate: () -> Unit,
)

/**
 * Manages database schema migrations.
 * Executes migrations sequentially, tracking the current version.
 */
class MigrationManager(private val databaseFactory: DatabaseFactory) {
    private val logger = LoggerFactory.getLogger(MigrationManager::class.java)
    private val migrations = mutableListOf<Migration>()

    init {
        // Register all migrations
        registerMigrations()
    }

    private fun registerMigrations() {
        migrations.add(Migration(1, "Create initial schema") {
            transaction(databaseFactory.getDatabase()) {
                SchemaUtils.create(
                    Tables.TasksTable,
                    Tables.WorkSessionsTable,
                    Tables.SessionEventsTable,
                    Tables.TemplateTasksTable,
                    Tables.UserSettingsTable,
                )

                // Create indexes
                exec("CREATE INDEX IF NOT EXISTS idx_tasks_planned_date ON tasks(planned_date);")
                exec("CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);")
                exec("CREATE INDEX IF NOT EXISTS idx_tasks_parent_id ON tasks(parent_id);")
                exec("CREATE INDEX IF NOT EXISTS idx_work_sessions_task_id ON work_sessions(task_id);")
                exec("CREATE INDEX IF NOT EXISTS idx_work_sessions_date ON work_sessions(date);")
                exec("CREATE INDEX IF NOT EXISTS idx_session_events_session_id ON session_events(session_id);")
            }
        })

        migrations.add(Migration(2, "Add display_order column to tasks") {
            transaction(databaseFactory.getDatabase()) {
                // Check if column already exists (SchemaUtils.create in V1 may have created it)
                val columnExists = try {
                    exec("SELECT display_order FROM tasks LIMIT 1")
                    true
                } catch (_: Exception) {
                    false
                }
                if (!columnExists) {
                    exec("ALTER TABLE tasks ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;")
                }
                exec("CREATE INDEX IF NOT EXISTS idx_tasks_display_order ON tasks(display_order);")
            }
        })

        migrations.add(Migration(3, "Add close_to_tray column to user_settings") {
            transaction(databaseFactory.getDatabase()) {
                val columnExists = try {
                    exec("SELECT close_to_tray FROM user_settings LIMIT 1")
                    true
                } catch (_: Exception) {
                    false
                }
                if (!columnExists) {
                    exec("ALTER TABLE user_settings ADD COLUMN close_to_tray BOOLEAN NOT NULL DEFAULT 0;")
                }
            }
        })
    }

    /**
     * Run all pending migrations.
     */
    fun migrate() {
        val db = databaseFactory.getDatabase()

        transaction(db) {
            // Create schema_version table if it doesn't exist
            SchemaUtils.create(SchemaVersionTable)
        }

        val currentVersion = getCurrentVersion()
        logger.info("Current schema version: {}", currentVersion)

        val pendingMigrations = migrations.filter { it.version > currentVersion }
            .sortedBy { it.version }

        if (pendingMigrations.isEmpty()) {
            logger.info("Database schema is up to date")
            return
        }

        logger.info("Applying {} pending migration(s)...", pendingMigrations.size)

        for (migration in pendingMigrations) {
            logger.info("Applying migration V{}: {}", migration.version, migration.description)
            try {
                migration.migrate()
                recordVersion(migration.version, migration.description)
                logger.info("Migration V{} applied successfully", migration.version)
            } catch (e: Exception) {
                logger.error("Failed to apply migration V{}: {}", migration.version, e.message, e)
                throw RuntimeException("Migration V${migration.version} failed", e)
            }
        }

        logger.info("All migrations applied. Current version: {}", migrations.last().version)
    }

    private fun getCurrentVersion(): Int {
        return transaction(databaseFactory.getDatabase()) {
            try {
                SchemaVersionTable.selectAll()
                    .maxByOrNull { it[SchemaVersionTable.version] }
                    ?.get(SchemaVersionTable.version)
                    ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }

    private fun recordVersion(version: Int, description: String) {
        transaction(databaseFactory.getDatabase()) {
            SchemaVersionTable.insert {
                it[SchemaVersionTable.version] = version
                it[SchemaVersionTable.appliedAt] = java.time.Instant.now().toString()
                it[SchemaVersionTable.description] = description
            }
        }
    }
}
