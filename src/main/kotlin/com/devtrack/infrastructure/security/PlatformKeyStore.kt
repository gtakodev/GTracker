package com.devtrack.infrastructure.security

import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * Linux key storage implementation using a file-based approach.
 * 
 * Note: In a production application, this would use libsecret (GNOME Keyring / KDE Wallet)
 * via DBus. For Phase 0, we use a simple file-based storage with restricted file permissions
 * as a placeholder. The KeyStore interface allows swapping implementations later.
 */
class LinuxKeyStore : KeyStore {
    private val logger = LoggerFactory.getLogger(LinuxKeyStore::class.java)
    private val keyDir = File(System.getProperty("user.home"), ".devtrack/keys")

    init {
        if (!keyDir.exists()) {
            keyDir.mkdirs()
            // Set directory permissions to owner-only (700)
            keyDir.setReadable(false, false)
            keyDir.setReadable(true, true)
            keyDir.setWritable(false, false)
            keyDir.setWritable(true, true)
            keyDir.setExecutable(false, false)
            keyDir.setExecutable(true, true)
        }
    }

    override fun storeKey(alias: String, key: ByteArray) {
        val keyFile = File(keyDir, "$alias.key")
        val encoded = Base64.getEncoder().encodeToString(key)
        keyFile.writeText(encoded)
        // Set file permissions to owner-only (600)
        keyFile.setReadable(false, false)
        keyFile.setReadable(true, true)
        keyFile.setWritable(false, false)
        keyFile.setWritable(true, true)
        logger.info("Key stored: {}", alias)
    }

    override fun retrieveKey(alias: String): ByteArray? {
        val keyFile = File(keyDir, "$alias.key")
        if (!keyFile.exists()) return null
        val encoded = keyFile.readText().trim()
        return Base64.getDecoder().decode(encoded)
    }

    override fun deleteKey(alias: String) {
        val keyFile = File(keyDir, "$alias.key")
        if (keyFile.exists()) {
            keyFile.delete()
            logger.info("Key deleted: {}", alias)
        }
    }

    override fun hasKey(alias: String): Boolean {
        return File(keyDir, "$alias.key").exists()
    }
}

/**
 * Windows key storage implementation placeholder.
 * 
 * Note: In a production application, this would use Windows DPAPI
 * via the Windows Credential Manager. For Phase 0, we use the same
 * file-based approach as Linux.
 */
class WindowsKeyStore : KeyStore {
    private val delegate = LinuxKeyStore() // Placeholder - same file-based approach

    override fun storeKey(alias: String, key: ByteArray) = delegate.storeKey(alias, key)
    override fun retrieveKey(alias: String) = delegate.retrieveKey(alias)
    override fun deleteKey(alias: String) = delegate.deleteKey(alias)
    override fun hasKey(alias: String) = delegate.hasKey(alias)
}

/**
 * Factory for creating platform-specific KeyStore instances.
 */
object KeyStoreFactory {
    private val logger = LoggerFactory.getLogger(KeyStoreFactory::class.java)

    fun create(): KeyStore {
        val os = System.getProperty("os.name").lowercase()
        val keyStore = when {
            os.contains("windows") -> WindowsKeyStore()
            else -> LinuxKeyStore()
        }
        logger.info("Using key store for platform: {}", os)
        return keyStore
    }

    /**
     * Generate a new AES-256 encryption key.
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(32) // 256 bits
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Get or create the database encryption key.
     */
    fun getOrCreateDbKey(keyStore: KeyStore): ByteArray {
        val alias = "devtrack-db-key"
        return keyStore.retrieveKey(alias) ?: run {
            val newKey = generateKey()
            keyStore.storeKey(alias, newKey)
            logger.info("Generated new database encryption key")
            newKey
        }
    }
}
