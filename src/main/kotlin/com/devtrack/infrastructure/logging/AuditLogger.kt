package com.devtrack.infrastructure.logging

import org.slf4j.LoggerFactory

/**
 * Audit logger for tracking user actions.
 * Logs actions without exposing sensitive content (task titles, descriptions).
 * Only IDs and action types are logged.
 */
class AuditLogger {
    private val logger = LoggerFactory.getLogger("AUDIT")

    /**
     * Log a user action.
     * @param action The action performed (e.g., "CREATE", "UPDATE", "DELETE", "START_TIMER")
     * @param entityType The type of entity (e.g., "TASK", "SESSION", "EVENT")
     * @param entityId The ID of the entity
     */
    fun logUserAction(action: String, entityType: String, entityId: String) {
        logger.info("ACTION={} ENTITY={} ID={}", action, entityType, entityId)
    }

    /**
     * Log a user action with additional context (non-sensitive).
     */
    fun logUserAction(action: String, entityType: String, entityId: String, context: Map<String, String>) {
        val contextStr = context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        logger.info("ACTION={} ENTITY={} ID={} {}", action, entityType, entityId, contextStr)
    }

    /**
     * Log an application lifecycle event.
     */
    fun logAppEvent(event: String) {
        logger.info("APP_EVENT={}", event)
    }
}
