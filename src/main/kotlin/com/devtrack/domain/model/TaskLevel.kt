package com.devtrack.domain.model

/**
 * The three task levels representing a task's lifecycle stage (P2.2, PRD 3.1).
 *
 * - BACKLOG: No planned date, not archived. Stored in the backlog.
 * - PLANNED: Has a planned date but no active timer.
 * - ACTIVE: Currently has a running session (IN_PROGRESS status).
 */
enum class TaskLevel {
    BACKLOG,
    PLANNED,
    ACTIVE,
}
