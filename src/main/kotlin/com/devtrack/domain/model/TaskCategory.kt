package com.devtrack.domain.model

/**
 * Task category (PRD 3.4).
 * Each category has a French label, English label, and associated hex color.
 */
enum class TaskCategory(val labelFr: String, val labelEn: String, val colorHex: String) {
    DEVELOPMENT("Développement", "Development", "#3B82F6"),
    BUGFIX("Correction de bug", "Bug Fix", "#EF4444"),
    MEETING("Réunion", "Meeting", "#8B5CF6"),
    REVIEW("Code review", "Code Review", "#F97316"),
    DOCUMENTATION("Documentation", "Documentation", "#22C55E"),
    LEARNING("Apprentissage", "Learning", "#06B6D4"),
    MAINTENANCE("Maintenance", "Maintenance", "#6B7280"),
    SUPPORT("Support", "Support", "#EAB308");

    companion object {
        /**
         * Map of hashtag aliases to their corresponding category (PRD 5.4.1).
         */
        val HASHTAG_MAP: Map<String, TaskCategory> = mapOf(
            "dev" to DEVELOPMENT,
            "development" to DEVELOPMENT,
            "bug" to BUGFIX,
            "bugfix" to BUGFIX,
            "meet" to MEETING,
            "meeting" to MEETING,
            "review" to REVIEW,
            "doc" to DOCUMENTATION,
            "documentation" to DOCUMENTATION,
            "learn" to LEARNING,
            "learning" to LEARNING,
            "maint" to MAINTENANCE,
            "maintenance" to MAINTENANCE,
            "support" to SUPPORT,
        )

        /**
         * Parse a category from a hashtag string (without the '#' prefix).
         * Returns null if no matching category is found.
         */
        fun fromHashtag(tag: String): TaskCategory? {
            return HASHTAG_MAP[tag.lowercase()]
        }
    }
}
