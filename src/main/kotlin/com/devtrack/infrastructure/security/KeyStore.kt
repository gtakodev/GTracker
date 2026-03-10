package com.devtrack.infrastructure.security

/**
 * Interface for platform-specific secure key storage.
 * Used to store the SQLCipher encryption key.
 */
interface KeyStore {
    /**
     * Store a key securely.
     * @param alias The alias/name for the key
     * @param key The key data
     */
    fun storeKey(alias: String, key: ByteArray)

    /**
     * Retrieve a key by alias.
     * @param alias The alias/name for the key
     * @return The key data, or null if not found
     */
    fun retrieveKey(alias: String): ByteArray?

    /**
     * Delete a key by alias.
     * @param alias The alias/name for the key
     */
    fun deleteKey(alias: String)

    /**
     * Check if a key exists.
     * @param alias The alias/name for the key
     */
    fun hasKey(alias: String): Boolean
}
