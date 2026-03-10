package com.devtrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a unit of work, optionally linked to Jira tickets (PRD 3.1).
 * Supports sub-tasks via parentId (max depth: 1).
 */
data class Task(
    val id: UUID = UUID.randomUUID(),
    val parentId: UUID? = null,
    val title: String,
    val description: String? = null,
    val category: TaskCategory = TaskCategory.DEVELOPMENT,
    val jiraTickets: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.TODO,
    val plannedDate: LocalDate? = null,
    val isTemplate: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val displayOrder: Int = 0,
)
