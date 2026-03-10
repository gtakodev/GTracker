package com.devtrack.data.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection

/**
 * Factory for creating and managing the SQLite database connection.
 * For Phase 0, we use plain SQLite. SQLCipher encryption will be layered
 * on top once the KeyStore infrastructure is in place.
 */
class DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    private var database: Database? = null
    private var keepAliveConnection: Connection? = null

    /**
     * Execute a PRAGMA statement using the raw JDBC connection.
     * Exposed's `exec()` expects result-returning queries, but PRAGMAs
     * don't return a ResultSet, so we must use the underlying JDBC connection.
     */
    private fun execPragma(pragma: String) {
        val conn = TransactionManager.current().connection.connection as Connection
        conn.createStatement().use { stmt ->
            stmt.execute(pragma)
        }
    }

    /**
     * Initialize the database connection.
     * Creates the data directory and database file if they don't exist.
     */
    fun init(dbPath: String = defaultDbPath()): Database {
        logger.info("Initializing database at: {}", dbPath)

        // Ensure parent directory exists
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        // Configure SQLite to enforce foreign keys on every connection.
        // The `enforce_foreign_keys` property is handled by the SQLite JDBC driver.
        val config = org.sqlite.SQLiteConfig().apply {
            enforceForeignKeys(true)
            setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL)
        }

        val db = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                config.apply(connection as org.sqlite.SQLiteConnection)
            },
        )

        database = db
        logger.info("Database initialized successfully")
        return db
    }

    /**
     * Initialize an in-memory database (for testing).
     *
     * Uses a named shared in-memory database so that all connections within
     * the same process access the same data. A keep-alive connection is held
     * open to prevent the database from being destroyed when Exposed closes
     * its connections between transactions.
     */
    fun initInMemory(): Database {
        logger.info("Initializing in-memory database")

        val url = "jdbc:sqlite:file:devtrack-test?mode=memory&cache=shared"

        // Hold a keep-alive connection so the shared in-memory DB is not destroyed
        keepAliveConnection = java.sql.DriverManager.getConnection(url)

        val config = org.sqlite.SQLiteConfig().apply {
            enforceForeignKeys(true)
        }

        val db = Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                config.apply(connection as org.sqlite.SQLiteConnection)
            },
        )

        database = db
        return db
    }

    /**
     * Get the current database instance.
     */
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call init() first.")
    }

    /**
     * Close the database and release resources.
     */
    fun close() {
        keepAliveConnection?.close()
        keepAliveConnection = null
        database = null
    }

    companion object {
        fun defaultDbPath(): String {
            val userHome = System.getProperty("user.home")
            return "$userHome/.devtrack/data/devtrack.db"
        }
    }
}
