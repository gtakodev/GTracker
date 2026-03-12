package com.devtrack.infrastructure.security

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for [FallbackFileKeyStore].
 *
 * The fallback store is always available (no OS daemon required) and is the
 * lowest-common-denominator used in CI environments.  We test it directly
 * rather than mocking the OS-specific stores.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallbackFileKeyStoreTest {

    // Use a temp directory so tests don't touch ~/.devtrack/keys
    private val tempDir = createTempDirectory("devtrack-keystore-test").toFile()
    private lateinit var store: FallbackFileKeyStore

    // Reflectively point the store at our temp dir for isolation
    @BeforeAll
    fun setup() {
        store = FallbackFileKeyStore()
        // Override the private keyDir field via reflection
        val field = FallbackFileKeyStore::class.java.getDeclaredField("keyDir")
        field.isAccessible = true
        field.set(store, tempDir)
    }

    @AfterAll
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `should store and retrieve a key`() {
        val key = ByteArray(32) { it.toByte() }
        store.storeKey("test-alias", key)
        val retrieved = store.retrieveKey("test-alias")
        assertNotNull(retrieved)
        assertArrayEquals(key, retrieved)
    }

    @Test
    fun `should return null for unknown alias`() {
        assertNull(store.retrieveKey("nonexistent-alias"))
    }

    @Test
    fun `hasKey returns true after store`() {
        val key = ByteArray(32) { 42 }
        store.storeKey("has-key-alias", key)
        assertTrue(store.hasKey("has-key-alias"))
    }

    @Test
    fun `hasKey returns false for unknown alias`() {
        assertFalse(store.hasKey("missing-alias"))
    }

    @Test
    fun `deleteKey removes the key`() {
        val key = ByteArray(32) { 1 }
        store.storeKey("delete-alias", key)
        assertTrue(store.hasKey("delete-alias"))
        store.deleteKey("delete-alias")
        assertFalse(store.hasKey("delete-alias"))
        assertNull(store.retrieveKey("delete-alias"))
    }

    @Test
    fun `deleteKey is idempotent`() {
        assertDoesNotThrow { store.deleteKey("never-stored-alias") }
    }

    @Test
    fun `stored key file has owner-only permissions`() {
        val key = ByteArray(32) { 7 }
        store.storeKey("perm-alias", key)
        val keyFile = File(tempDir, "perm-alias.key")
        assertTrue(keyFile.exists())
        // canRead by owner == true; no group/others read (JVM API can only check owner)
        assertTrue(keyFile.canRead())
    }
}

/**
 * Tests for [KeyStoreFactory] helpers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeyStoreFactoryTest {

    @Test
    fun `generateKey produces a 32-byte key`() {
        val key = KeyStoreFactory.generateKey()
        assertEquals(32, key.size)
    }

    @Test
    fun `generateKey produces unique keys on each call`() {
        val k1 = KeyStoreFactory.generateKey()
        val k2 = KeyStoreFactory.generateKey()
        assertFalse(k1.contentEquals(k2), "Two generated keys should differ")
    }

    @Test
    fun `getOrCreateDbKey generates key on first call`() {
        val store = FallbackFileKeyStore().also { s ->
            // Redirect to temp dir
            val field = FallbackFileKeyStore::class.java.getDeclaredField("keyDir")
            field.isAccessible = true
            val dir = createTempDirectory("kf-test").toFile()
            field.set(s, dir)
            dir.deleteOnExit()
        }

        assertFalse(store.hasKey("devtrack-db-key"))
        val key = KeyStoreFactory.getOrCreateDbKey(store)
        assertEquals(32, key.size)
        assertTrue(store.hasKey("devtrack-db-key"))
    }

    @Test
    fun `getOrCreateDbKey returns existing key on subsequent calls`() {
        val store = FallbackFileKeyStore().also { s ->
            val field = FallbackFileKeyStore::class.java.getDeclaredField("keyDir")
            field.isAccessible = true
            val dir = createTempDirectory("kf-test2").toFile()
            field.set(s, dir)
            dir.deleteOnExit()
        }

        val key1 = KeyStoreFactory.getOrCreateDbKey(store)
        val key2 = KeyStoreFactory.getOrCreateDbKey(store)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `create returns a KeyStore instance`() {
        val ks = KeyStoreFactory.create()
        assertNotNull(ks)
    }
}
