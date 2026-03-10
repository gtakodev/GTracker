package com.devtrack.data.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * Exposed DSL table definitions for DevTrack.
 * Based on PRD Annexe B schema.
 */
object Tables {

    /**
     * Tasks table (PRD 3.1)
     */
    object TasksTable : Table("tasks") {
        val id = varchar("id", 36)  // UUID as text
        val parentId = varchar("parent_id", 36)
            .references(id, onDelete = ReferenceOption.CASCADE)
            .nullable()
        val title = text("title")
        val description = text("description").nullable()
        val category = varchar("category", 50).default("DEVELOPMENT")
        val jiraTickets = text("jira_tickets").default("[]")  // JSON array
        val status = varchar("status", 30).default("TODO")
        val plannedDate = varchar("planned_date", 10).nullable()  // ISO date
        val isTemplate = bool("is_template").default(false)
        val createdAt = varchar("created_at", 50)  // ISO instant
        val updatedAt = varchar("updated_at", 50)  // ISO instant
        val displayOrder = integer("display_order").default(0)

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Work sessions table (PRD 3.2)
     */
    object WorkSessionsTable : Table("work_sessions") {
        val id = varchar("id", 36)  // UUID as text
        val taskId = varchar("task_id", 36)
            .references(TasksTable.id, onDelete = ReferenceOption.CASCADE)
        val date = varchar("date", 10)  // ISO date
        val startTime = varchar("start_time", 50)  // ISO instant
        val endTime = varchar("end_time", 50).nullable()  // ISO instant, null = orphan
        val sessionSource = varchar("source", 20).default("TIMER")
        val notes = text("notes").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Session events table (PRD 3.3)
     */
    object SessionEventsTable : Table("session_events") {
        val id = varchar("id", 36)  // UUID as text
        val sessionId = varchar("session_id", 36)
            .references(WorkSessionsTable.id, onDelete = ReferenceOption.CASCADE)
        val type = varchar("type", 20)
        val timestamp = varchar("timestamp", 50)  // ISO instant

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Template tasks table (PRD 3.5)
     */
    object TemplateTasksTable : Table("template_tasks") {
        val id = varchar("id", 36)  // UUID as text
        val title = text("title")
        val category = varchar("category", 50)
        val defaultDurationMin = integer("default_duration_min").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * User settings table (PRD 3.6)
     */
    object UserSettingsTable : Table("user_settings") {
        val id = varchar("id", 36)  // UUID as text
        val locale = varchar("locale", 10).default("fr")
        val theme = varchar("theme", 20).default("SYSTEM")
        val inactivityThresholdMin = integer("inactivity_threshold_min").default(30)
        val hoursPerDay = double("hours_per_day").default(8.0)
        val halfDayThreshold = double("half_day_threshold").default(4.0)
        val pomodoroWorkMin = integer("pomodoro_work_min").default(25)
        val pomodoroBreakMin = integer("pomodoro_break_min").default(5)
        val pomodoroLongBreakMin = integer("pomodoro_long_break_min").default(15)
        val pomodoroSessionsBeforeLong = integer("pomodoro_sessions_before_long").default(4)

        override val primaryKey = PrimaryKey(id)
    }
}
