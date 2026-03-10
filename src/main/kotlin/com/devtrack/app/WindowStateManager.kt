package com.devtrack.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Persisted window state data.
 */
@Serializable
data class WindowPreferences(
    val width: Int = 1200,
    val height: Int = 800,
    val x: Int = -1,  // -1 means center on screen
    val y: Int = -1,
)

/**
 * Manages persistence of window size and position.
 * Saves to a JSON file in the DevTrack data directory (~/.devtrack/window.json).
 * This is independent of the database to ensure window state is available
 * before the full application context (Koin, DB) is initialized.
 */
class WindowStateManager {
    private val logger = LoggerFactory.getLogger(WindowStateManager::class.java)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val prefsFile: File
        get() {
            val userHome = System.getProperty("user.home")
            return File("$userHome/.devtrack/window.json")
        }

    /**
     * Load saved window preferences, or return defaults if no saved state exists.
     */
    fun load(): WindowPreferences {
        return try {
            val file = prefsFile
            if (file.exists()) {
                val content = file.readText()
                val prefs = json.decodeFromString<WindowPreferences>(content)
                logger.info("Loaded window preferences: {}x{} at ({}, {})", prefs.width, prefs.height, prefs.x, prefs.y)
                prefs
            } else {
                logger.info("No saved window preferences found, using defaults")
                WindowPreferences()
            }
        } catch (e: Exception) {
            logger.warn("Failed to load window preferences, using defaults: {}", e.message)
            WindowPreferences()
        }
    }

    /**
     * Save the current window size and position.
     */
    fun save(width: Int, height: Int, x: Int, y: Int) {
        try {
            val prefs = WindowPreferences(width = width, height = height, x = x, y = y)
            val file = prefsFile
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(prefs))
            logger.debug("Saved window preferences: {}x{} at ({}, {})", width, height, x, y)
        } catch (e: Exception) {
            logger.warn("Failed to save window preferences: {}", e.message)
        }
    }
}
