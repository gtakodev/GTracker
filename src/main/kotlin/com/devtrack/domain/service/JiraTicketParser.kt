package com.devtrack.domain.service

import com.devtrack.domain.model.TaskCategory

/**
 * Parses Jira ticket identifiers and hashtag categories from task titles (P1.3.1).
 *
 * - Ticket pattern: `[A-Z]{2,10}-\d+` (PRD 3.1)
 * - Hashtag pattern: `#word` mapped to [TaskCategory] via [TaskCategory.HASHTAG_MAP]
 */
class JiraTicketParser {

    companion object {
        val JIRA_TICKET_REGEX = Regex("[A-Z]{2,10}-\\d+")
        val HASHTAG_REGEX = Regex("#(\\w+)")
    }

    /**
     * Extract all Jira ticket identifiers from a title string.
     * Returns distinct tickets in the order they appear.
     */
    fun extractTickets(title: String): List<String> {
        return JIRA_TICKET_REGEX.findAll(title)
            .map { it.value }
            .distinct()
            .toList()
    }

    /**
     * Extract the first matching [TaskCategory] from hashtags in the title.
     * Returns null if no recognized hashtag is found.
     */
    fun extractCategory(title: String): TaskCategory? {
        return HASHTAG_REGEX.findAll(title)
            .mapNotNull { match -> TaskCategory.fromHashtag(match.groupValues[1]) }
            .firstOrNull()
    }

    /**
     * Remove all hashtags from the title and trim whitespace.
     */
    fun cleanTitle(title: String): String {
        return HASHTAG_REGEX.replace(title, "").trim().replace(Regex("\\s+"), " ")
    }
}
