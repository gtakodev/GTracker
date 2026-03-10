package com.devtrack.infrastructure.backup

import com.devtrack.data.database.DatabaseFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Metadata embedded in a DevTrack backup file.
 */
@Serializable
data class BackupMetadata(
    val appVersion: String,
    val schemaVersion: Int,
    val exportDate: String,
    val description: String = "DevTrack backup",
)

/**
 * Result of an export operation.
 */
data class BackupResult(
    val success: Boolean,
    val filePath: Path? = null,
    val fileSizeBytes: Long = 0,
    val error: String? = null,
)

/**
 * Result of an import operation.
 */
data class ImportResult(
    val success: Boolean,
    val taskCount: Long = 0,
    val sessionCount: Long = 0,
    val error: String? = null,
)

/**
 * Service for exporting and importing DevTrack database backups (P4.7.1).
 *
 * Backup format: `.devtrack-backup` (ZIP file containing):
 * - `devtrack.db` — SQLite database file
 * - `metadata.json` — Backup metadata (app version, schema version, date)
 *
 * The service handles:
 * - Copying the active database to a ZIP archive
 * - Verifying backup integrity on import
 * - Replacing the database with the imported one
 * - Counting restored records for user feedback
 */
class BackupService(
    private val databaseFactory: DatabaseFactory,
) {
    private val logger = LoggerFactory.getLogger(BackupService::class.java)
    private val json = Json { prettyPrint = true }

    companion object {
        const val APP_VERSION = "1.0.0"
        const val SCHEMA_VERSION = 2
        const val DB_ENTRY_NAME = "devtrack.db"
        const val METADATA_ENTRY_NAME = "metadata.json"
        const val BACKUP_EXTENSION = ".devtrack-backup"
    }

    /**
     * Export the current database to a backup file.
     *
     * @param destination Path where the backup file will be written.
     *                    If it doesn't end with `.devtrack-backup`, the extension is appended.
     * @return [BackupResult] indicating success or failure.
     */
    fun exportBackup(destination: Path): BackupResult {
        return try {
            val destFile = ensureExtension(destination)
            val dbPath = DatabaseFactory.defaultDbPath()
            val dbFile = File(dbPath)

            if (!dbFile.exists()) {
                return BackupResult(success = false, error = "Database file not found: $dbPath")
            }

            // Ensure parent directory exists
            destFile.parent?.let { Files.createDirectories(it) }

            // Create ZIP containing db + metadata
            ZipOutputStream(Files.newOutputStream(destFile)).use { zip ->
                // Add database file
                zip.putNextEntry(ZipEntry(DB_ENTRY_NAME))
                Files.copy(dbFile.toPath(), zip)
                zip.closeEntry()

                // Add metadata
                val metadata = BackupMetadata(
                    appVersion = APP_VERSION,
                    schemaVersion = SCHEMA_VERSION,
                    exportDate = Instant.now().toString(),
                )
                zip.putNextEntry(ZipEntry(METADATA_ENTRY_NAME))
                zip.write(json.encodeToString(metadata).toByteArray())
                zip.closeEntry()
            }

            val fileSize = Files.size(destFile)
            logger.info("Backup exported to {} ({} bytes)", destFile, fileSize)

            BackupResult(
                success = true,
                filePath = destFile,
                fileSizeBytes = fileSize,
            )
        } catch (e: Exception) {
            logger.error("Failed to export backup", e)
            BackupResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Import a backup file, replacing the current database.
     *
     * Steps:
     * 1. Verify the backup file exists and is a valid ZIP
     * 2. Read and validate metadata (compatible schema version)
     * 3. Close the current database connection
     * 4. Replace the database file with the one from the backup
     * 5. Re-open the database connection
     * 6. Count restored records for user feedback
     *
     * @param source Path to the backup file.
     * @return [ImportResult] indicating success or failure.
     */
    fun importBackup(source: Path): ImportResult {
        return try {
            val sourceFile = source.toFile()
            if (!sourceFile.exists()) {
                return ImportResult(success = false, error = "Backup file not found: $source")
            }

            // Validate ZIP and read metadata
            val metadata = readMetadata(source)
                ?: return ImportResult(success = false, error = "Invalid backup: missing metadata.json")

            // Check schema version compatibility
            if (metadata.schemaVersion > SCHEMA_VERSION) {
                return ImportResult(
                    success = false,
                    error = "Incompatible backup: schema version ${metadata.schemaVersion} > current $SCHEMA_VERSION",
                )
            }

            val dbPath = DatabaseFactory.defaultDbPath()
            val dbFile = File(dbPath)

            // Close current database connection
            databaseFactory.close()

            // Extract database file from ZIP
            try {
                ZipFile(sourceFile).use { zip ->
                    val dbEntry = zip.getEntry(DB_ENTRY_NAME)
                        ?: return ImportResult(success = false, error = "Invalid backup: missing $DB_ENTRY_NAME")

                    // Backup the existing DB as .bak before overwriting
                    if (dbFile.exists()) {
                        val bakFile = File("$dbPath.bak")
                        Files.copy(dbFile.toPath(), bakFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    // Extract the DB
                    zip.getInputStream(dbEntry).use { input ->
                        Files.copy(input, dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            } catch (e: Exception) {
                // Try to restore the backup if extraction failed
                val bakFile = File("$dbPath.bak")
                if (bakFile.exists()) {
                    Files.copy(bakFile.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                // Re-open the database
                databaseFactory.init(dbPath)
                throw e
            }

            // Re-open the database connection
            databaseFactory.init(dbPath)

            // Count restored records
            val (taskCount, sessionCount) = countRecords()

            logger.info("Backup imported from {} (schema v{}, {} tasks, {} sessions)",
                source, metadata.schemaVersion, taskCount, sessionCount)

            ImportResult(
                success = true,
                taskCount = taskCount,
                sessionCount = sessionCount,
            )
        } catch (e: Exception) {
            logger.error("Failed to import backup", e)
            ImportResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Read metadata from a backup file without importing it.
     */
    fun readMetadata(source: Path): BackupMetadata? {
        return try {
            ZipFile(source.toFile()).use { zip ->
                val metadataEntry = zip.getEntry(METADATA_ENTRY_NAME) ?: return null
                val metadataJson = zip.getInputStream(metadataEntry).bufferedReader().readText()
                json.decodeFromString<BackupMetadata>(metadataJson)
            }
        } catch (e: Exception) {
            logger.error("Failed to read backup metadata", e)
            null
        }
    }

    /**
     * Count the number of tasks and sessions in the current database.
     */
    private fun countRecords(): Pair<Long, Long> {
        return try {
            org.jetbrains.exposed.sql.transactions.transaction {
                val taskCount = exec("SELECT COUNT(*) FROM tasks") { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                } ?: 0L

                val sessionCount = exec("SELECT COUNT(*) FROM work_sessions") { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                } ?: 0L

                Pair(taskCount, sessionCount)
            }
        } catch (e: Exception) {
            logger.error("Failed to count records", e)
            Pair(0L, 0L)
        }
    }

    /**
     * Ensure the file path has the correct extension.
     */
    private fun ensureExtension(path: Path): Path {
        return if (path.toString().endsWith(BACKUP_EXTENSION)) {
            path
        } else {
            Path.of("$path$BACKUP_EXTENSION")
        }
    }
}
