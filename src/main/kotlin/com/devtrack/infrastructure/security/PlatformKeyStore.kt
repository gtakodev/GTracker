package com.devtrack.infrastructure.security

import com.sun.jna.platform.win32.Crypt32Util
import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Linux — libsecret via `secret-tool` CLI
// ---------------------------------------------------------------------------

/**
 * Linux key storage using the GNOME/KDE secret service via the `secret-tool`
 * command-line interface (part of the `libsecret-tools` package).
 *
 * Falls back to [FallbackFileKeyStore] with a warning when `secret-tool` is
 * not available or when the secret service daemon is not running.
 *
 * `secret-tool` attributes used:
 *   - `application` = "devtrack"
 *   - `alias`       = <alias>
 */
class LinuxKeyStore : KeyStore {
    private val logger = LoggerFactory.getLogger(LinuxKeyStore::class.java)
    private val fallback by lazy { FallbackFileKeyStore() }

    /** True when `secret-tool` can be found on PATH and the service responds. */
    private val secretToolAvailable: Boolean by lazy {
        try {
            val process = ProcessBuilder("secret-tool", "--version")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(SECRET_TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                logger.warn("secret-tool --version timed out; treating as unavailable")
                return@lazy false
            }
            process.exitValue() == 0
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Waits for [process] to exit within [SECRET_TOOL_TIMEOUT_MS] milliseconds.
     * Returns the exit code on success, or `null` when the timeout elapses or
     * the current thread is interrupted (in which case the interrupted flag is
     * restored and the process is force-killed).
     */
    private fun awaitProcess(process: Process, context: String): Int? {
        return try {
            if (!process.waitFor(SECRET_TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                logger.error("secret-tool {} timed out after {}ms", context, SECRET_TOOL_TIMEOUT_MS)
                null
            } else {
                process.exitValue()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            logger.error("Interrupted while waiting for secret-tool {}", context)
            null
        }
    }

    override fun storeKey(alias: String, key: ByteArray) {
        if (!secretToolAvailable) {
            logger.warn(
                "secret-tool is not available. Storing key for '{}' in fallback file store. " +
                    "Install libsecret-tools for secure OS credential storage.",
                alias,
            )
            fallback.storeKey(alias, key)
            return
        }
        val encoded = Base64.getEncoder().encodeToString(key)
        val process = ProcessBuilder(
            "secret-tool", "store",
            "--label", "DevTrack — $alias",
            "application", "devtrack",
            "alias", alias,
        )
            .redirectErrorStream(true)
            .start()

        process.outputStream.bufferedWriter().use { it.write(encoded) }
        val exitCode = awaitProcess(process, "store") ?: run {
            logger.warn("Falling back to file-based key store for alias '{}' (timeout/interrupt)", alias)
            fallback.storeKey(alias, key)
            return
        }
        if (exitCode != 0) {
            val err = process.inputStream.bufferedReader().readText()
            logger.error("secret-tool store failed (exit {}): {}", exitCode, err)
            logger.warn("Falling back to file-based key store for alias '{}'", alias)
            fallback.storeKey(alias, key)
        } else {
            logger.info("Key stored via libsecret: {}", alias)
        }
    }

    override fun retrieveKey(alias: String): ByteArray? {
        if (!secretToolAvailable) return fallback.retrieveKey(alias)

        val process = ProcessBuilder(
            "secret-tool", "lookup",
            "application", "devtrack",
            "alias", alias,
        )
            .redirectErrorStream(false)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = awaitProcess(process, "lookup") ?: return fallback.retrieveKey(alias)

        return if (exitCode == 0 && output.isNotEmpty()) {
            try {
                Base64.getDecoder().decode(output)
            } catch (_: Exception) {
                logger.warn("Invalid base64 for alias '{}' in secret service, trying fallback", alias)
                fallback.retrieveKey(alias)
            }
        } else {
            // Not found in secret service — check fallback (migration path)
            fallback.retrieveKey(alias)
        }
    }

    override fun deleteKey(alias: String) {
        if (!secretToolAvailable) {
            fallback.deleteKey(alias)
            return
        }
        val process = ProcessBuilder(
            "secret-tool", "clear",
            "application", "devtrack",
            "alias", alias,
        )
            .redirectErrorStream(true)
            .start()
        awaitProcess(process, "clear") // exit code is ignored; always clean up fallback too
        fallback.deleteKey(alias)
        logger.info("Key deleted: {}", alias)
    }

    override fun hasKey(alias: String): Boolean {
        if (!secretToolAvailable) return fallback.hasKey(alias)
        return retrieveKey(alias) != null
    }

    companion object {
        /** Maximum time in milliseconds to wait for any single `secret-tool` invocation. */
        private const val SECRET_TOOL_TIMEOUT_MS = 5_000L
    }
}

// ---------------------------------------------------------------------------
// Windows — DPAPI via JNA-platform (Crypt32Util)
// ---------------------------------------------------------------------------

/**
 * Windows key storage using DPAPI (Data Protection API) via
 * [Crypt32Util] from `jna-platform`.
 *
 * [Crypt32Util.cryptProtectData] / [Crypt32Util.cryptUnprotectData] accept and
 * return plain `ByteArray` and handle `LocalFree` for the native output buffer
 * internally, so there is no native-memory leak.
 *
 * The protected bytes are stored as a base64-encoded file. Even if the file is
 * read by a different OS user account, it cannot be decrypted.
 *
 * Falls back to [FallbackFileKeyStore] with a warning if DPAPI is unavailable
 * (e.g. in CI / container environments).
 */
class WindowsKeyStore : KeyStore {
    private val logger = LoggerFactory.getLogger(WindowsKeyStore::class.java)
    private val keyDir = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "devtrack\\keys",
    )
    private val fallback by lazy { FallbackFileKeyStore() }

    init {
        if (!keyDir.exists()) keyDir.mkdirs()
    }

    override fun storeKey(alias: String, key: ByteArray) {
        val encrypted = try {
            Crypt32Util.cryptProtectData(key, "DevTrack-$alias")
        } catch (e: Exception) {
            logger.error("CryptProtectData failed for alias '{}'. Using fallback. Error: {}", alias, e.message)
            fallback.storeKey(alias, key)
            return
        }

        val keyFile = File(keyDir, "$alias.dpapi")
        keyFile.writeText(Base64.getEncoder().encodeToString(encrypted))
        keyFile.setReadable(false, false); keyFile.setReadable(true, true)
        keyFile.setWritable(false, false); keyFile.setWritable(true, true)
        logger.info("Key stored via DPAPI: {}", alias)
    }

    override fun retrieveKey(alias: String): ByteArray? {
        val keyFile = File(keyDir, "$alias.dpapi")
        if (!keyFile.exists()) return fallback.retrieveKey(alias)

        val encrypted = try {
            Base64.getDecoder().decode(keyFile.readText().trim())
        } catch (e: Exception) {
            logger.error("Corrupt DPAPI key file for alias '{}': {}", alias, e.message)
            return null
        }

        return try {
            Crypt32Util.cryptUnprotectData(encrypted)
        } catch (e: Exception) {
            logger.error("CryptUnprotectData failed for alias '{}': {}", alias, e.message)
            null
        }
    }

    override fun deleteKey(alias: String) {
        File(keyDir, "$alias.dpapi").delete()
        fallback.deleteKey(alias)
        logger.info("Key deleted: {}", alias)
    }

    override fun hasKey(alias: String): Boolean =
        File(keyDir, "$alias.dpapi").exists() || fallback.hasKey(alias)
}

// ---------------------------------------------------------------------------
// Fallback — file-based (restricted permissions, warns user)
// ---------------------------------------------------------------------------

/**
 * File-based key store used as a fallback when the OS credential store is
 * unavailable. Keys are stored as base64-encoded files with owner-only
 * permissions under `~/.devtrack/keys/`.
 *
 * **Security warning:** This store does NOT provide OS-level encryption.
 * Its use is logged as a warning at startup.
 */
class FallbackFileKeyStore : KeyStore {
    private val logger = LoggerFactory.getLogger(FallbackFileKeyStore::class.java)
    private val keyDir = File(System.getProperty("user.home"), ".devtrack/keys")

    init {
        if (!keyDir.exists()) {
            keyDir.mkdirs()
            keyDir.setReadable(false, false); keyDir.setReadable(true, true)
            keyDir.setWritable(false, false); keyDir.setWritable(true, true)
            keyDir.setExecutable(false, false); keyDir.setExecutable(true, true)
        }
    }

    override fun storeKey(alias: String, key: ByteArray) {
        val keyFile = File(keyDir, "$alias.key")
        keyFile.writeText(Base64.getEncoder().encodeToString(key))
        keyFile.setReadable(false, false); keyFile.setReadable(true, true)
        keyFile.setWritable(false, false); keyFile.setWritable(true, true)
        logger.warn(
            "Key '{}' stored in plain file ({}). " +
                "Consider installing libsecret-tools (Linux) or running on Windows for DPAPI protection.",
            alias,
            keyFile.absolutePath,
        )
    }

    override fun retrieveKey(alias: String): ByteArray? {
        val keyFile = File(keyDir, "$alias.key")
        if (!keyFile.exists()) return null
        return try {
            Base64.getDecoder().decode(keyFile.readText().trim())
        } catch (_: Exception) {
            logger.error("Corrupt key file for alias '{}'", alias)
            null
        }
    }

    override fun deleteKey(alias: String) {
        val keyFile = File(keyDir, "$alias.key")
        if (keyFile.exists()) {
            keyFile.delete()
            logger.info("Key deleted (fallback): {}", alias)
        }
    }

    override fun hasKey(alias: String): Boolean =
        File(keyDir, "$alias.key").exists()
}

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

/**
 * Factory that selects the appropriate [KeyStore] implementation for the
 * current OS platform.
 */
object KeyStoreFactory {
    private val logger = LoggerFactory.getLogger(KeyStoreFactory::class.java)

    fun create(): KeyStore {
        val os = System.getProperty("os.name").lowercase()
        val keyStore: KeyStore = when {
            os.contains("windows") -> WindowsKeyStore()
            else -> LinuxKeyStore()
        }
        logger.info("Using key store for platform: {}", os)
        return keyStore
    }

    /** Generate a cryptographically random AES-256 key (32 bytes = 256 bits). */
    fun generateKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Retrieve the database encryption key from [keyStore], generating and
     * persisting a new one only when the alias is truly absent (first launch).
     *
     * Distinguishes three cases:
     * 1. Key exists **and** is readable → return it.
     * 2. Key is absent → generate, persist, and return a fresh key.
     * 3. Key exists but [KeyStore.retrieveKey] returned `null` → the credential
     *    store entry is present but could not be decrypted (corruption, key
     *    rotation, wrong user account, etc.).  Generating a new key here would
     *    silently discard the old one and make the existing database permanently
     *    unreadable, so we throw instead and let the caller decide.
     *
     * The check-then-create sequence is guarded by a `synchronized` block on
     * this object to prevent two threads in the same process from racing to
     * generate duplicate keys.
     */
    fun getOrCreateDbKey(keyStore: KeyStore): ByteArray {
        val alias = "devtrack-db-key"
        synchronized(this) {
            val existing = keyStore.retrieveKey(alias)
            if (existing != null) return existing

            if (keyStore.hasKey(alias)) {
                // Entry is present in the credential store but could not be read —
                // decryption failure, corruption, or a permission problem.
                throw IllegalStateException(
                    "Database encryption key '$alias' exists in the credential store " +
                        "but could not be retrieved. " +
                        "Resolve the keystore issue and restart, or manually delete the entry " +
                        "to allow a new key to be generated (this will make existing data unreadable).",
                )
            }

            // Alias is genuinely absent — safe to create.
            val newKey = generateKey()
            keyStore.storeKey(alias, newKey)
            logger.info("Generated new database encryption key")
            return newKey
        }
    }
}
