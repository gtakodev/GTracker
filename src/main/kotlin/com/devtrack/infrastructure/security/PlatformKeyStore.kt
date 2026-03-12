package com.devtrack.infrastructure.security

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom
import java.util.Base64

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
            val result = ProcessBuilder("secret-tool", "--version")
                .redirectErrorStream(true)
                .start()
                .apply { waitFor() }
            result.exitValue() == 0
        } catch (_: Exception) {
            false
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
        val exitCode = process.waitFor()
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
        val exitCode = process.waitFor()

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
        process.waitFor()
        fallback.deleteKey(alias) // also clean up any legacy file
        logger.info("Key deleted: {}", alias)
    }

    override fun hasKey(alias: String): Boolean {
        if (!secretToolAvailable) return fallback.hasKey(alias)
        return retrieveKey(alias) != null
    }
}

// ---------------------------------------------------------------------------
// Windows — DPAPI via JNA (Crypt32.dll)
// ---------------------------------------------------------------------------

/**
 * JNA binding to the Windows DPAPI functions in `Crypt32.dll`.
 */
private interface Crypt32 : Library {
    @Structure.FieldOrder("cbData", "pbData")
    class DataBlob(pointer: Pointer? = null) : Structure(pointer) {
        @JvmField var cbData: Int = 0
        @JvmField var pbData: Pointer? = null

        init {
            if (pointer != null) read()
        }
    }

    fun CryptProtectData(
        pDataIn: DataBlob,
        szDataDescr: String?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob,
    ): Boolean

    fun CryptUnprotectData(
        pDataIn: DataBlob,
        ppszDataDescr: PointerByReference?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob,
    ): Boolean
}

/**
 * Windows key storage using DPAPI (Data Protection API) via JNA.
 *
 * The encryption key bytes are protected with `CryptProtectData` (scoped to
 * the current user) and stored as a base64-encoded file. Even if the file is
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

    private val crypt32: Crypt32? by lazy {
        try {
            Native.load("Crypt32", Crypt32::class.java) as Crypt32
        } catch (_: Exception) {
            logger.warn("DPAPI (Crypt32.dll) not available. Falling back to file-based key store.")
            null
        }
    }

    init {
        if (!keyDir.exists()) keyDir.mkdirs()
    }

    override fun storeKey(alias: String, key: ByteArray) {
        val lib = crypt32 ?: run { fallback.storeKey(alias, key); return }

        val mem = Memory(key.size.toLong()).apply { write(0, key, 0, key.size) }
        val dataIn = Crypt32.DataBlob().apply { cbData = key.size; pbData = mem }
        val dataOut = Crypt32.DataBlob()

        if (!lib.CryptProtectData(dataIn, "DevTrack-$alias", null, null, null, 0, dataOut)) {
            logger.error("CryptProtectData failed for alias '{}'. Using fallback.", alias)
            fallback.storeKey(alias, key)
            return
        }

        val encrypted = ByteArray(dataOut.cbData)
        dataOut.pbData!!.read(0, encrypted, 0, dataOut.cbData)
        val keyFile = File(keyDir, "$alias.dpapi")
        keyFile.writeText(Base64.getEncoder().encodeToString(encrypted))
        keyFile.setReadable(false, false); keyFile.setReadable(true, true)
        keyFile.setWritable(false, false); keyFile.setWritable(true, true)
        logger.info("Key stored via DPAPI: {}", alias)
    }

    override fun retrieveKey(alias: String): ByteArray? {
        val lib = crypt32 ?: return fallback.retrieveKey(alias)
        val keyFile = File(keyDir, "$alias.dpapi")
        if (!keyFile.exists()) return fallback.retrieveKey(alias)

        val encrypted = Base64.getDecoder().decode(keyFile.readText().trim())
        val mem = Memory(encrypted.size.toLong()).apply { write(0, encrypted, 0, encrypted.size) }
        val dataIn = Crypt32.DataBlob().apply { cbData = encrypted.size; pbData = mem }
        val dataOut = Crypt32.DataBlob()

        if (!lib.CryptUnprotectData(dataIn, null, null, null, null, 0, dataOut)) {
            logger.error("CryptUnprotectData failed for alias '{}'", alias)
            return null
        }

        val key = ByteArray(dataOut.cbData)
        dataOut.pbData!!.read(0, key, 0, dataOut.cbData)
        return key
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
     * persisting a new one if none exists (first launch).
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
