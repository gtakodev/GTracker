package com.devtrack.infrastructure.export

import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Utility for copying text to the system clipboard (P1.5.2).
 */
object ClipboardService {
    private val logger = LoggerFactory.getLogger(ClipboardService::class.java)

    /**
     * Copy the given text to the system clipboard.
     * Returns true if successful, false otherwise.
     */
    fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            logger.info("Copied {} chars to clipboard", text.length)
            true
        } catch (e: Exception) {
            logger.error("Failed to copy to clipboard", e)
            false
        }
    }
}
