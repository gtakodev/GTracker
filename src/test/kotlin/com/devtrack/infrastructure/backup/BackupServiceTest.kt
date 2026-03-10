package com.devtrack.infrastructure.backup

import com.devtrack.data.database.DatabaseFactory
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for BackupService (P4.7.3).
 *
 * Tests cover:
 * - Export creates a valid ZIP with db + metadata
 * - Re-import of exported backup succeeds
 * - Corrupted/invalid backup returns graceful error
 * - Incompatible schema version returns error
 * - Missing database file returns error on export
 * - readMetadata works standalone
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class BackupServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var databaseFactory: DatabaseFactory
    private lateinit var backupService: BackupService
    private lateinit var fakeDbFile: File

    @BeforeEach
    fun setup() {
        databaseFactory = mockk(relaxed = true)

        // Create a fake database file at the default path
        fakeDbFile = createFakeDbFile()

        backupService = BackupService(databaseFactory)
    }

    /**
     * Creates a fake database file at the path returned by DatabaseFactory.defaultDbPath().
     * We use mockkObject to override the companion function.
     */
    private fun createFakeDbFile(): File {
        val dbDir = tempDir.resolve("data")
        Files.createDirectories(dbDir)
        val dbFile = dbDir.resolve("devtrack.db").toFile()
        // Write some recognizable content
        dbFile.writeText("FAKE_SQLITE_DB_CONTENT_12345")

        // Mock the static defaultDbPath to point to our temp file
        mockkObject(DatabaseFactory.Companion)
        every { DatabaseFactory.defaultDbPath() } returns dbFile.absolutePath

        return dbFile
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(DatabaseFactory.Companion)
    }

    // --- Export tests ---

    @Test
    fun `exportBackup creates valid ZIP with db and metadata`() {
        val dest = tempDir.resolve("test-backup.devtrack-backup")

        val result = backupService.exportBackup(dest)

        assertTrue(result.success, "Export should succeed")
        assertNotNull(result.filePath)
        assertTrue(Files.exists(result.filePath!!), "Backup file should exist")
        assertTrue(result.fileSizeBytes > 0, "Backup should have non-zero size")

        // Verify ZIP contents
        val zipFile = java.util.zip.ZipFile(result.filePath!!.toFile())
        zipFile.use { zip ->
            val dbEntry = zip.getEntry(BackupService.DB_ENTRY_NAME)
            assertNotNull(dbEntry, "ZIP should contain devtrack.db")

            val metadataEntry = zip.getEntry(BackupService.METADATA_ENTRY_NAME)
            assertNotNull(metadataEntry, "ZIP should contain metadata.json")

            // Verify database content matches
            val dbContent = zip.getInputStream(dbEntry).bufferedReader().readText()
            assertEquals("FAKE_SQLITE_DB_CONTENT_12345", dbContent)

            // Verify metadata is valid JSON with expected fields
            val metadataJson = zip.getInputStream(metadataEntry).bufferedReader().readText()
            assertTrue(metadataJson.contains("\"appVersion\""), "Metadata should contain appVersion")
            assertTrue(metadataJson.contains("\"schemaVersion\""), "Metadata should contain schemaVersion")
            assertTrue(metadataJson.contains("\"exportDate\""), "Metadata should contain exportDate")
        }
    }

    @Test
    fun `exportBackup appends extension if missing`() {
        val dest = tempDir.resolve("test-backup")

        val result = backupService.exportBackup(dest)

        assertTrue(result.success)
        assertTrue(result.filePath.toString().endsWith(BackupService.BACKUP_EXTENSION))
    }

    @Test
    fun `exportBackup fails when database file does not exist`() {
        // Remove the fake db file
        fakeDbFile.delete()

        val dest = tempDir.resolve("test-backup.devtrack-backup")
        val result = backupService.exportBackup(dest)

        assertFalse(result.success, "Export should fail when DB is missing")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not found"), "Error should mention file not found")
    }

    // --- Import tests ---

    @Test
    fun `importBackup succeeds with valid backup and counts records`() {
        // First export a backup
        val backupPath = tempDir.resolve("valid-backup.devtrack-backup")
        val exportResult = backupService.exportBackup(backupPath)
        assertTrue(exportResult.success, "Export prerequisite should succeed")

        // Mock databaseFactory.init to simulate reopening
        every { databaseFactory.init(any()) } returns mockk()

        // Mock the transaction-based record counting
        // Since importBackup calls countRecords() which uses Exposed transactions,
        // and we don't have a real DB in this unit test, the import will succeed
        // but countRecords will return 0 (the catch block handles the exception)
        val result = backupService.importBackup(backupPath)

        assertTrue(result.success, "Import should succeed: ${result.error}")
        verify { databaseFactory.close() }
        verify { databaseFactory.init(any()) }
    }

    @Test
    fun `importBackup fails when file does not exist`() {
        val nonExistent = tempDir.resolve("nonexistent.devtrack-backup")

        val result = backupService.importBackup(nonExistent)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not found"))
    }

    @Test
    fun `importBackup fails with corrupted ZIP file`() {
        val corruptedFile = tempDir.resolve("corrupted.devtrack-backup")
        Files.writeString(corruptedFile, "THIS IS NOT A VALID ZIP FILE")

        val result = backupService.importBackup(corruptedFile)

        assertFalse(result.success, "Import of corrupted file should fail")
        assertNotNull(result.error)
    }

    @Test
    fun `importBackup fails when ZIP is missing metadata`() {
        // Create a ZIP with only a db file but no metadata
        val noMetadataBackup = tempDir.resolve("no-metadata.devtrack-backup")
        ZipOutputStream(Files.newOutputStream(noMetadataBackup)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupService.DB_ENTRY_NAME))
            zip.write("fake db content".toByteArray())
            zip.closeEntry()
        }

        val result = backupService.importBackup(noMetadataBackup)

        assertFalse(result.success, "Import should fail without metadata")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("metadata"), "Error should mention missing metadata")
    }

    @Test
    fun `importBackup fails with incompatible schema version`() {
        // Create a backup with a future schema version
        val futureBackup = tempDir.resolve("future-backup.devtrack-backup")
        ZipOutputStream(Files.newOutputStream(futureBackup)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupService.DB_ENTRY_NAME))
            zip.write("fake db content".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(BackupService.METADATA_ENTRY_NAME))
            val metadata = """
                {
                    "appVersion": "99.0.0",
                    "schemaVersion": 999,
                    "exportDate": "2026-01-01T00:00:00Z",
                    "description": "Future backup"
                }
            """.trimIndent()
            zip.write(metadata.toByteArray())
            zip.closeEntry()
        }

        val result = backupService.importBackup(futureBackup)

        assertFalse(result.success, "Import should fail with incompatible schema")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Incompatible"), "Error should mention incompatibility")
        assertTrue(result.error!!.contains("999"), "Error should mention the schema version")
    }

    // --- readMetadata tests ---

    @Test
    fun `readMetadata returns correct metadata from valid backup`() {
        val backupPath = tempDir.resolve("meta-test.devtrack-backup")
        backupService.exportBackup(backupPath)

        val metadata = backupService.readMetadata(backupPath)

        assertNotNull(metadata)
        assertEquals(BackupService.APP_VERSION, metadata!!.appVersion)
        assertEquals(BackupService.SCHEMA_VERSION, metadata.schemaVersion)
        assertTrue(metadata.exportDate.isNotBlank())
        assertEquals("DevTrack backup", metadata.description)
    }

    @Test
    fun `readMetadata returns null for invalid file`() {
        val invalidFile = tempDir.resolve("invalid.devtrack-backup")
        Files.writeString(invalidFile, "not a zip")

        val metadata = backupService.readMetadata(invalidFile)

        assertNull(metadata)
    }

    @Test
    fun `readMetadata returns null when metadata entry is missing`() {
        val noMetadataZip = tempDir.resolve("no-meta.devtrack-backup")
        ZipOutputStream(Files.newOutputStream(noMetadataZip)).use { zip ->
            zip.putNextEntry(ZipEntry("other-file.txt"))
            zip.write("random content".toByteArray())
            zip.closeEntry()
        }

        val metadata = backupService.readMetadata(noMetadataZip)

        assertNull(metadata)
    }

    // --- Export + re-import round-trip ---

    @Test
    fun `export then reimport preserves database content`() {
        val backupPath = tempDir.resolve("roundtrip.devtrack-backup")

        // Export
        val exportResult = backupService.exportBackup(backupPath)
        assertTrue(exportResult.success)

        // Modify the "current" database to simulate different data
        fakeDbFile.writeText("MODIFIED_AFTER_EXPORT")

        // Mock databaseFactory.init
        every { databaseFactory.init(any()) } returns mockk()

        // Import the backup (should restore the original content)
        val importResult = backupService.importBackup(backupPath)
        assertTrue(importResult.success, "Re-import should succeed: ${importResult.error}")

        // Verify the database file was replaced with the backup content
        val restoredContent = fakeDbFile.readText()
        assertEquals("FAKE_SQLITE_DB_CONTENT_12345", restoredContent,
            "Database should be restored to original content")
    }
}
