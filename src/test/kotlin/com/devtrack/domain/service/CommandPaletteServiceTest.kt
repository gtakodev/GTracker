package com.devtrack.domain.service

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import com.devtrack.domain.model.*
import java.time.LocalDate

/**
 * Unit tests for CommandPaletteService (P2.3.6).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandPaletteServiceTest {

    private val jiraTicketParser = JiraTicketParser()
    private lateinit var service: CommandPaletteService

    @BeforeEach
    fun setup() {
        service = CommandPaletteService(jiraTicketParser)
    }

    // -- parseCommand tests --

    @Test
    fun `parseCommand returns null for empty input`() {
        assertNull(service.parseCommand(""))
        assertNull(service.parseCommand("   "))
    }

    @Test
    fun `parseCommand parses start command with argument`() {
        val result = service.parseCommand("/start PROJ-123")
        assertTrue(result is PaletteCommand.Start)
        assertEquals("PROJ-123", (result as PaletteCommand.Start).query)
    }

    @Test
    fun `parseCommand returns null for start without argument`() {
        assertNull(service.parseCommand("/start"))
        assertNull(service.parseCommand("/start "))
    }

    @Test
    fun `parseCommand parses pause command`() {
        val result = service.parseCommand("/pause")
        assertTrue(result is PaletteCommand.Pause)
    }

    @Test
    fun `parseCommand parses resume command`() {
        val result = service.parseCommand("/resume")
        assertTrue(result is PaletteCommand.Resume)
    }

    @Test
    fun `parseCommand parses done command`() {
        val result = service.parseCommand("/done")
        assertTrue(result is PaletteCommand.Done)
    }

    @Test
    fun `parseCommand parses switch command with argument`() {
        val result = service.parseCommand("/switch Fix login page")
        assertTrue(result is PaletteCommand.Switch)
        assertEquals("Fix login page", (result as PaletteCommand.Switch).query)
    }

    @Test
    fun `parseCommand returns null for switch without argument`() {
        assertNull(service.parseCommand("/switch"))
    }

    @Test
    fun `parseCommand parses plan command with today`() {
        val result = service.parseCommand("/plan PROJ-456 today")
        assertTrue(result is PaletteCommand.Plan)
        val plan = result as PaletteCommand.Plan
        assertEquals("PROJ-456", plan.query)
        assertEquals(LocalDate.now(), plan.date)
    }

    @Test
    fun `parseCommand parses plan command with tomorrow`() {
        val result = service.parseCommand("/plan PROJ-456 tomorrow")
        assertTrue(result is PaletteCommand.Plan)
        val plan = result as PaletteCommand.Plan
        assertEquals("PROJ-456", plan.query)
        assertEquals(LocalDate.now().plusDays(1), plan.date)
    }

    @Test
    fun `parseCommand parses plan command with ISO date`() {
        val result = service.parseCommand("/plan PROJ-456 2026-03-15")
        assertTrue(result is PaletteCommand.Plan)
        val plan = result as PaletteCommand.Plan
        assertEquals("PROJ-456", plan.query)
        assertEquals(LocalDate.of(2026, 3, 15), plan.date)
    }

    @Test
    fun `parseCommand parses plan command without date`() {
        val result = service.parseCommand("/plan PROJ-456")
        assertTrue(result is PaletteCommand.Plan)
        val plan = result as PaletteCommand.Plan
        assertEquals("PROJ-456", plan.query)
        assertNull(plan.date)
    }

    @Test
    fun `parseCommand returns null for plan without argument`() {
        assertNull(service.parseCommand("/plan"))
    }

    @Test
    fun `parseCommand parses template command`() {
        val result = service.parseCommand("/template daily-standup")
        assertTrue(result is PaletteCommand.Template)
        assertEquals("daily-standup", (result as PaletteCommand.Template).name)
    }

    @Test
    fun `parseCommand parses report command with period`() {
        val result = service.parseCommand("/report today")
        assertTrue(result is PaletteCommand.Report)
        assertEquals("today", (result as PaletteCommand.Report).period)
    }

    @Test
    fun `parseCommand parses report command without period defaults to today`() {
        val result = service.parseCommand("/report")
        assertTrue(result is PaletteCommand.Report)
        assertEquals("today", (result as PaletteCommand.Report).period)
    }

    @Test
    fun `parseCommand parses pomodoro command`() {
        val result = service.parseCommand("/pomodoro PROJ-789")
        assertTrue(result is PaletteCommand.Pomodoro)
        assertEquals("PROJ-789", (result as PaletteCommand.Pomodoro).query)
    }

    @Test
    fun `parseCommand returns null for unknown command`() {
        assertNull(service.parseCommand("/unknown"))
    }

    @Test
    fun `parseCommand treats non-slash input as CreateTask`() {
        val result = service.parseCommand("Fix login page #bug")
        assertTrue(result is PaletteCommand.CreateTask)
        assertEquals("Fix login page #bug", (result as PaletteCommand.CreateTask).title)
    }

    @Test
    fun `parseCommand is case insensitive for commands`() {
        val result = service.parseCommand("/PAUSE")
        assertTrue(result is PaletteCommand.Pause)
    }

    @Test
    fun `parseCommand trims input`() {
        val result = service.parseCommand("  /pause  ")
        assertTrue(result is PaletteCommand.Pause)
    }

    // -- generateSuggestions tests --

    @Test
    fun `generateSuggestions returns all commands for empty input`() {
        val suggestions = service.generateSuggestions("", emptyList())
        assertEquals(service.availableCommands.size, suggestions.size)
        assertTrue(suggestions.all { it.label.startsWith("/") })
    }

    @Test
    fun `generateSuggestions filters commands by prefix`() {
        val suggestions = service.generateSuggestions("/pa", emptyList())
        assertEquals(1, suggestions.size)
        assertEquals("/pause", suggestions[0].label)
    }

    @Test
    fun `generateSuggestions returns matching commands for slash prefix`() {
        val suggestions = service.generateSuggestions("/s", emptyList())
        assertEquals(2, suggestions.size) // /start, /switch
        assertTrue(suggestions.all { it.label.startsWith("/s") })
    }

    @Test
    fun `generateSuggestions searches tasks by title`() {
        val task = Task(title = "Fix login page", category = TaskCategory.BUGFIX)
        val suggestions = service.generateSuggestions("Fix login", listOf(task))

        // Should contain the task match + a create option
        assertTrue(suggestions.any { it.task == task })
        assertTrue(suggestions.any { it.command is PaletteCommand.CreateTask })
    }

    @Test
    fun `generateSuggestions searches tasks by Jira ticket`() {
        val task = Task(
            title = "Login fix",
            jiraTickets = listOf("PROJ-123"),
            category = TaskCategory.BUGFIX,
        )
        val suggestions = service.generateSuggestions("PROJ-123", listOf(task))

        assertTrue(suggestions.any { it.task == task })
    }

    @Test
    fun `generateSuggestions offers create when no exact match`() {
        val task = Task(title = "Some other task")
        val suggestions = service.generateSuggestions("New task title", listOf(task))

        // No task match, should have only create
        assertTrue(suggestions.any { it.command is PaletteCommand.CreateTask })
    }

    @Test
    fun `generateSuggestions limits task results to 8`() {
        val tasks = (1..20).map { Task(title = "Task $it") }
        val suggestions = service.generateSuggestions("Task", tasks)

        // Should have at most 8 task matches + 1 create = 9
        val taskSuggestions = suggestions.filter { it.task != null }
        assertTrue(taskSuggestions.size <= 8)
    }

    @Test
    fun `availableCommands contains all expected commands`() {
        val commandNames = service.availableCommands.map { it.name }
        assertTrue("/start" in commandNames)
        assertTrue("/pause" in commandNames)
        assertTrue("/resume" in commandNames)
        assertTrue("/done" in commandNames)
        assertTrue("/switch" in commandNames)
        assertTrue("/plan" in commandNames)
        assertTrue("/template" in commandNames)
        assertTrue("/report" in commandNames)
        assertTrue("/pomodoro" in commandNames)
    }

    @Test
    fun `parseCommand handles plan with multi-word query and date`() {
        val result = service.parseCommand("/plan Fix login page today")
        assertTrue(result is PaletteCommand.Plan)
        val plan = result as PaletteCommand.Plan
        assertEquals("Fix login page", plan.query)
        assertEquals(LocalDate.now(), plan.date)
    }
}
