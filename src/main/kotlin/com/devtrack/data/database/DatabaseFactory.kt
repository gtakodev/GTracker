package com.devtrack.data.database

import com.devtrack.infrastructure.security.KeyStore
import com.devtrack.infrastructure.security.KeyStoreFactory
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Factory for creating and managing the SQLite database connection with
 * SQLCipher-compatible encryption via the `io.github.willena:sqlite-jdbc`
 * driver (SQLite Multiple Ciphers).
 *
 * The encryption key is retrieved (or generated on first launch) from the OS
 * credential store via [KeyStoreFactory] / [KeyStore], then passed to the
 * driver at connection-creation time via [SQLiteDataSource] — the only correct
 * approach with this driver. Passing the key via `PRAGMA key` or
 * `SQLiteMCConfig.apply(connection)` inside `setupConnection` is too late
 * because the driver already opens the file before `setupConnection` runs.
 *
 * The in-memory variant used by tests skips encryption entirely so that tests
 * don't depend on a keyring daemon.
 */
class DatabaseFactory(
    private val keyStore: KeyStore = KeyStoreFactory.create(),
) {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    private var database: Database? = null
    private var keepAliveConnection: Connection? = null

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Initialize the encrypted on-disk database.
     *
     * 1. Retrieves (or generates) the 32-byte AES key from [keyStore].
     * 2. Builds a [SQLiteMCSqlCipherConfig] with that key via [withHexKey].
     * 3. Wraps it in an [SQLiteDataSource] so the JDBC driver receives the key
     *    properties **at connection-open time** (before any statement is run).
     * 4. WAL mode and foreign-key enforcement are applied in [setupConnection].
     *
     * If a plain (unencrypted) database file already exists it cannot be
     * opened with a key — the existing file is renamed to `.bak` and a fresh
     * encrypted database is created.
     */
    fun init(dbPath: String = defaultDbPath()): Database {
        logger.info("Initializing encrypted database at: {}", dbPath)

        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        val encKey = KeyStoreFactory.getOrCreateDbKey(keyStore)

        // Build SQLiteMCConfig: SQLCipher-compatible defaults + raw 32-byte key
        val sqliteMCConfig = SQLiteMCSqlCipherConfig.getDefault()
            .withHexKey(encKey)
            .build()

        // If the file exists but is plain SQLite (pre-encryption migration),
        // back it up and let a fresh encrypted database be created.
        // We also move any WAL/SHM sidecar files so that uncheckpointed data
        // is not lost and no orphaned sidecars remain next to the new DB.
        if (dbFile.exists() && isPlainSQLite(dbFile)) {
            val backupMain = File("$dbPath.bak")
            val walFile    = File("$dbPath-wal")
            val shmFile    = File("$dbPath-shm")
            val backupWal  = File("$dbPath-wal.bak")
            val backupShm  = File("$dbPath-shm.bak")

            // Abort if any backup already exists to avoid silently overwriting
            // a previous backup that may still be needed.
            for (existing in listOf(backupMain, backupWal, backupShm)) {
                if (existing.exists()) {
                    logger.error(
                        "Cannot back up unencrypted database: backup file {} already exists. " +
                            "Remove or rename the existing backup and restart.",
                        existing.absolutePath,
                    )
                    throw IllegalStateException(
                        "Backup file already exists: ${existing.absolutePath}",
                    )
                }
            }

            logger.warn(
                "Existing database at {} is unencrypted. " +
                    "Moving it (and any WAL/SHM sidecars) to *.bak and creating a new " +
                    "encrypted database. Your previous data will not be migrated automatically.",
                dbPath,
            )

            // Move the main file first; if it fails we have not touched anything.
            try {
                Files.move(
                    dbFile.toPath(),
                    backupMain.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: UnsupportedOperationException) {
                // ATOMIC_MOVE is not supported on this filesystem; fall back to
                // a plain move (still raises an exception on failure).
                Files.move(dbFile.toPath(), backupMain.toPath())
            } catch (e: Exception) {
                logger.error(
                    "Failed to move unencrypted database {} to {}: {}",
                    dbPath,
                    backupMain.absolutePath,
                    e.message,
                )
                throw IllegalStateException(
                    "Could not back up unencrypted database: ${e.message}",
                    e,
                )
            }

            // Move sidecar files only if they exist; a missing sidecar is not
            // an error (the DB may already be checkpointed / closed cleanly).
            for ((sidecar, backupSidecar) in listOf(walFile to backupWal, shmFile to backupShm)) {
                if (!sidecar.exists()) continue
                try {
                    Files.move(
                        sidecar.toPath(),
                        backupSidecar.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: UnsupportedOperationException) {
                    Files.move(sidecar.toPath(), backupSidecar.toPath())
                } catch (e: Exception) {
                    logger.error(
                        "Failed to move sidecar {} to {}: {}",
                        sidecar.absolutePath,
                        backupSidecar.absolutePath,
                        e.message,
                    )
                    throw IllegalStateException(
                        "Could not back up sidecar file ${sidecar.name}: ${e.message}",
                        e,
                    )
                }
            }
        }

        // Wrap the cipher config in a DataSource so that every connection
        // opened by Exposed carries the encryption properties from the start.
        val dataSource = SQLiteDataSource(sqliteMCConfig).apply {
            url = "jdbc:sqlite:$dbPath"
        }

        val db = Database.connect(
            datasource = dataSource,
            setupConnection = { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode = WAL")
                    stmt.execute("PRAGMA foreign_keys = ON")
                }
            },
        )

        database = db
        logger.info("Encrypted database initialized successfully")
        return db
    }

    /**
     * Initialize an **unencrypted** in-memory database for testing.
     *
     * A keep-alive [Connection] is held open to prevent SQLite from destroying
     * the shared in-memory database between Exposed transaction blocks.
     * No cipher config is applied — tests must not depend on a keyring daemon.
     *
     * [dbName] scopes the shared-memory namespace. Each call that uses the
     * default generates a fresh UUID, so separate [DatabaseFactory] instances
     * (e.g. different test classes) never share state accidentally.
     */
    fun initInMemory(dbName: String = "devtrack-test-${UUID.randomUUID()}"): Database {
        logger.info("Initializing in-memory database (unencrypted, for tests, name={})", dbName)

        val url = "jdbc:sqlite:file:$dbName?mode=memory&cache=shared"
        keepAliveConnection = DriverManager.getConnection(url)

        val db = Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("PRAGMA foreign_keys = ON")
                }
            },
        )

        database = db
        return db
    }

    fun getDatabase(): Database =
        database ?: throw IllegalStateException("Database not initialized. Call init() first.")

    fun close() {
        keepAliveConnection?.close()
        keepAliveConnection = null
        database = null
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns true if [file] starts with the SQLite3 magic header bytes
     * (`53 51 4C 69 74 65 20 66 6F 72 6D 61 74 20 33 00` — "SQLite format 3\000").
     * An encrypted database has a random-looking header and will not match.
     */
    private fun isPlainSQLite(file: File): Boolean {
        if (file.length() < 16) return false
        val magic = byteArrayOf(
            0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
            0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00,
        )
        val header = file.inputStream().use { it.readNBytes(16) }
        return header.contentEquals(magic)
    }

    companion object {
        fun defaultDbPath(): String {
            val userHome = System.getProperty("user.home")
            return "$userHome/.devtrack/data/devtrack.db"
        }
    }
}
