package com.devtrack.domain.service

import com.devtrack.domain.model.Task
import java.time.LocalDate

/**
 * A parsed command from the Command Palette (P2.3.2, P2.3.3).
 */
sealed class PaletteCommand {
    /** Start a timer on a task (create if needed). */
    data class Start(val query: String) : PaletteCommand()

    /** Pause the active session. */
    data object Pause : PaletteCommand()

    /** Resume the active session. */
    data object Resume : PaletteCommand()

    /** Stop session and mark task as DONE. */
    data object Done : PaletteCommand()

    /** Stop current session and start on a different task. */
    data class Switch(val query: String) : PaletteCommand()

    /** Plan a task for a date. */
    data class Plan(val query: String, val date: LocalDate?) : PaletteCommand()

    /** Instantiate a template (stub for Phase 4). */
    data class Template(val name: String) : PaletteCommand()

    /** Generate a report. */
    data class Report(val period: String) : PaletteCommand()

    /** Start a pomodoro (stub for Phase 4). */
    data class Pomodoro(val query: String) : PaletteCommand()

    /** Create a new task with the given title. */
    data class CreateTask(val title: String) : PaletteCommand()

    /** Navigate to a task (search result selected). */
    data class NavigateToTask(val task: Task) : PaletteCommand()

    /** Show ticket summary (P3.7.3). */
    data class TicketSearch(val ticket: String) : PaletteCommand()
}

/**
 * A suggestion displayed in the command palette.
 */
data class PaletteSuggestion(
    val label: String,
    val description: String,
    val command: PaletteCommand? = null,
    val task: Task? = null,
)

/**
 * Command definitions for the palette.
 */
data class CommandDef(
    val name: String,
    val description: String,
    val hasArgument: Boolean,
)

/**
 * Service that parses user input into palette commands and generates suggestions (P2.3).
 */
class CommandPaletteService(
    private val jiraTicketParser: JiraTicketParser,
) {

    val availableCommands = listOf(
        CommandDef("/start", "command.start.description", true),
        CommandDef("/pause", "command.pause.description", false),
        CommandDef("/resume", "command.resume.description", false),
        CommandDef("/done", "command.done.description", false),
        CommandDef("/switch", "command.switch.description", true),
        CommandDef("/plan", "command.plan.description", true),
        CommandDef("/template", "command.template.description", true),
        CommandDef("/report", "command.report.description", true),
        CommandDef("/pomodoro", "command.pomodoro.description", true),
    )

    /**
     * Parse a raw user input string into a PaletteCommand.
     * Returns null if the input is empty or not parseable into a command.
     */
    fun parseCommand(input: String): PaletteCommand? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // If it starts with '/', parse as a command
        if (trimmed.startsWith("/")) {
            val parts = trimmed.split(" ", limit = 2)
            val commandName = parts[0].lowercase()
            val argument = parts.getOrNull(1)?.trim() ?: ""

            return when (commandName) {
                "/start" -> if (argument.isNotEmpty()) PaletteCommand.Start(argument) else null
                "/pause" -> PaletteCommand.Pause
                "/resume" -> PaletteCommand.Resume
                "/done" -> PaletteCommand.Done
                "/switch" -> if (argument.isNotEmpty()) PaletteCommand.Switch(argument) else null
                "/plan" -> parsePlanCommand(argument)
                "/template" -> if (argument.isNotEmpty()) PaletteCommand.Template(argument) else null
                "/report" -> PaletteCommand.Report(argument.ifEmpty { "today" })
                "/pomodoro" -> if (argument.isNotEmpty()) PaletteCommand.Pomodoro(argument) else null
                else -> null
            }
        }

        // Not a command - treat as a task creation/search query
        return PaletteCommand.CreateTask(trimmed)
    }

    /**
     * Parse the argument of a /plan command.
     * Expected format: `/plan <ticket or title> today|tomorrow|<date>`
     */
    private fun parsePlanCommand(argument: String): PaletteCommand? {
        if (argument.isEmpty()) return null

        val parts = argument.split(" ")
        val dateStr = parts.lastOrNull()?.lowercase()
        val date = when (dateStr) {
            "today" -> LocalDate.now()
            "tomorrow" -> LocalDate.now().plusDays(1)
            else -> try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                null
            }
        }

        val query = if (date != null && parts.size > 1) {
            parts.dropLast(1).joinToString(" ")
        } else {
            argument
        }

        return PaletteCommand.Plan(query, date)
    }

    /**
     * Generate suggestions based on user input and available tasks.
     */
    fun generateSuggestions(input: String, tasks: List<Task>): List<PaletteSuggestion> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            // Show available commands
            return availableCommands.map { cmd ->
                PaletteSuggestion(
                    label = cmd.name,
                    description = cmd.description,
                )
            }
        }

        if (trimmed.startsWith("/")) {
            return generateCommandSuggestions(trimmed)
        }

        // Search mode: match against existing tasks
        return generateTaskSuggestions(trimmed, tasks)
    }

    /**
     * Generate command suggestions when input starts with '/'.
     */
    private fun generateCommandSuggestions(input: String): List<PaletteSuggestion> {
        val parts = input.split(" ", limit = 2)
        val cmdPrefix = parts[0].lowercase()

        // If it's a partial command, suggest matching commands
        val matching = availableCommands.filter { it.name.startsWith(cmdPrefix) }
        return matching.map { cmd ->
            PaletteSuggestion(
                label = cmd.name,
                description = cmd.description,
            )
        }
    }

    /**
     * Generate task suggestions when input is a search query.
     * Also suggests ticket search if input matches a Jira ticket pattern (P3.7.3).
     */
    private fun generateTaskSuggestions(query: String, tasks: List<Task>): List<PaletteSuggestion> {
        val lowerQuery = query.lowercase()
        val suggestions = mutableListOf<PaletteSuggestion>()

        // Check if the query looks like a Jira ticket — offer ticket summary (P3.7.3)
        val tickets = jiraTicketParser.extractTickets(query)
        for (ticket in tickets) {
            suggestions.add(
                PaletteSuggestion(
                    label = ticket,
                    description = "command_palette.ticket_search",
                    command = PaletteCommand.TicketSearch(ticket),
                )
            )
        }

        // Find matching tasks
        val matchingTasks = tasks.filter { task ->
            task.title.lowercase().contains(lowerQuery) ||
                task.jiraTickets.any { it.lowercase().contains(lowerQuery) } ||
                task.description?.lowercase()?.contains(lowerQuery) == true
        }.take(8)

        matchingTasks.forEach { task ->
            val ticketInfo = if (task.jiraTickets.isNotEmpty()) {
                " (${task.jiraTickets.joinToString(", ")})"
            } else ""

            suggestions.add(
                PaletteSuggestion(
                    label = task.title + ticketInfo,
                    description = "command.start_task",
                    command = PaletteCommand.Start(task.title),
                    task = task,
                )
            )
        }

        // If no exact match, suggest creating a new task
        if (matchingTasks.isEmpty() || matchingTasks.none { it.title.equals(query, ignoreCase = true) }) {
            suggestions.add(
                PaletteSuggestion(
                    label = query,
                    description = "command.create_task",
                    command = PaletteCommand.CreateTask(query),
                )
            )
        }

        return suggestions
    }
}
