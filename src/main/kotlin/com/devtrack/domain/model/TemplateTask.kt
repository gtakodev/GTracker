package com.devtrack.domain.model

import java.util.UUID

/**
 * A recurring task template that can be instantiated on any day (PRD 3.5).
 * Templates never create tasks automatically — they must be explicitly instantiated
 * by the user via click or the /template command.
 */
data class TemplateTask(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val category: TaskCategory,
    val defaultDurationMin: Int? = null,
)
