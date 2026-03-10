package com.devtrack.infrastructure.export

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.*
import com.devtrack.domain.service.ReportDataService
import com.devtrack.domain.service.TimeCalculator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.LocalDate

/**
 * Tests for WeeklyReportGenerator (P3.3.2).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeeklyReportGeneratorTest {

    private val reportDataService = mockk<ReportDataService>()
    private val timeCalculator = TimeCalculator()
    private val userSettingsRepository = mockk<UserSettingsRepository>()

    private lateinit var generator: WeeklyReportGenerator

    private val defaultSettings = UserSettings(hoursPerDay = 8.0, halfDayThreshold = 4.0)

    // Monday 2026-03-09
    private val weekStart = LocalDate.of(2026, 3, 9)
    private val weekEnd = LocalDate.of(2026, 3, 13)

    @BeforeEach
    fun setup() {
        generator = WeeklyReportGenerator(
            reportDataService = reportDataService,
            timeCalculator = timeCalculator,
            userSettingsRepository = userSettingsRepository,
        )
        coEvery { userSettingsRepository.get() } returns defaultSettings
    }

    private fun emptyDailyData(date: LocalDate) = DailyReportData(
        date = date,
        ticketDurations = emptyMap(),
        noTicketDurations = emptyMap(),
        ticketDescriptions = emptyMap(),
        totalDuration = Duration.ZERO,
    )

    private fun emptyWeekData() = WeeklyReportData(
        weekStart = weekStart,
        weekEnd = weekEnd,
        dailyData = (0L..4L).map { emptyDailyData(weekStart.plusDays(it)) },
        ticketDescriptions = emptyMap(),
        allTickets = emptyList(),
        totalDuration = Duration.ZERO,
    )

    @Nested
    inner class RenderMarkdownTests {

        @Test
        fun `empty week renders no-activity message`() {
            val data = emptyWeekData()
            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("Rapport Semaine du"))
            assertTrue(md.contains("Aucune activite enregistree cette semaine."))
            assertFalse(md.contains("| Ticket"))
        }

        @Test
        fun `week with one ticket renders table`() {
            val dailyData = (0L..4L).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset)
                DailyReportData(
                    date = date,
                    ticketDurations = if (dayOffset == 0L) mapOf("DPD-100" to Duration.ofHours(4)) else emptyMap(),
                    noTicketDurations = emptyMap(),
                    ticketDescriptions = if (dayOffset == 0L) mapOf("DPD-100" to "Fix") else emptyMap(),
                    totalDuration = if (dayOffset == 0L) Duration.ofHours(4) else Duration.ZERO,
                )
            }

            val data = WeeklyReportData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                dailyData = dailyData,
                ticketDescriptions = mapOf("DPD-100" to "Fix"),
                allTickets = listOf("DPD-100"),
                totalDuration = Duration.ofHours(4),
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("| Ticket | Description "))
            assertTrue(md.contains("`DPD-100`"))
            assertTrue(md.contains("4h00m"))
            assertTrue(md.contains("| **Total** "))
        }

        @Test
        fun `week with non-ticket work renders Hors-ticket row`() {
            val dailyData = (0L..4L).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset)
                DailyReportData(
                    date = date,
                    ticketDurations = emptyMap(),
                    noTicketDurations = if (dayOffset == 2L) mapOf(TaskCategory.MEETING to Duration.ofHours(1)) else emptyMap(),
                    ticketDescriptions = emptyMap(),
                    totalDuration = if (dayOffset == 2L) Duration.ofHours(1) else Duration.ZERO,
                )
            }

            val data = WeeklyReportData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                dailyData = dailyData,
                ticketDescriptions = emptyMap(),
                allTickets = emptyList(),
                totalDuration = Duration.ofHours(1),
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("*Hors-ticket*"))
            assertTrue(md.contains("Reunions/Annexes"))
        }

        @Test
        fun `header contains 5 day columns`() {
            val dailyData = (0L..4L).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset)
                DailyReportData(
                    date = date,
                    ticketDurations = mapOf("X-1" to Duration.ofHours(1)),
                    noTicketDurations = emptyMap(),
                    ticketDescriptions = emptyMap(),
                    totalDuration = Duration.ofHours(1),
                )
            }

            val data = WeeklyReportData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                dailyData = dailyData,
                ticketDescriptions = emptyMap(),
                allTickets = listOf("X-1"),
                totalDuration = Duration.ofHours(5),
            )

            val md = generator.renderMarkdown(data, defaultSettings)
            val headerLine = md.lines().first { it.startsWith("| Ticket") }

            // Ticket + Description + 5 days + Total = 8 cells = 9 pipes
            val pipeCount = headerLine.count { it == '|' }
            assertEquals(9, pipeCount)
        }

        @Test
        fun `multiple tickets each get a row`() {
            val dailyData = (0L..4L).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset)
                DailyReportData(
                    date = date,
                    ticketDurations = mapOf(
                        "DPD-100" to Duration.ofHours(2),
                        "DPD-200" to Duration.ofHours(3),
                    ),
                    noTicketDurations = emptyMap(),
                    ticketDescriptions = mapOf("DPD-100" to "A", "DPD-200" to "B"),
                    totalDuration = Duration.ofHours(5),
                )
            }

            val data = WeeklyReportData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                dailyData = dailyData,
                ticketDescriptions = mapOf("DPD-100" to "A", "DPD-200" to "B"),
                allTickets = listOf("DPD-100", "DPD-200"),
                totalDuration = Duration.ofHours(25),
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("`DPD-100`"))
            assertTrue(md.contains("`DPD-200`"))
        }
    }

    @Nested
    inner class RenderPlainTextTests {

        @Test
        fun `empty week renders no-activity in plain text`() {
            val data = emptyWeekData()
            val text = generator.renderPlainText(data, defaultSettings)

            assertTrue(text.contains("Rapport Semaine du"))
            assertTrue(text.contains("Aucune activite enregistree cette semaine."))
        }

        @Test
        fun `plain text lists tickets`() {
            val dailyData = (0L..4L).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset)
                DailyReportData(
                    date = date,
                    ticketDurations = if (dayOffset == 0L) mapOf("DPD-100" to Duration.ofHours(8)) else emptyMap(),
                    noTicketDurations = emptyMap(),
                    ticketDescriptions = if (dayOffset == 0L) mapOf("DPD-100" to "Fix") else emptyMap(),
                    totalDuration = if (dayOffset == 0L) Duration.ofHours(8) else Duration.ZERO,
                )
            }

            val data = WeeklyReportData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                dailyData = dailyData,
                ticketDescriptions = mapOf("DPD-100" to "Fix"),
                allTickets = listOf("DPD-100"),
                totalDuration = Duration.ofHours(8),
            )

            val text = generator.renderPlainText(data, defaultSettings)

            assertTrue(text.contains("DPD-100"))
            assertTrue(text.contains("Total semaine:"))
        }
    }

    @Nested
    inner class GenerateTests {

        @Test
        fun `generate calls service and returns ReportOutput`() = runTest {
            val data = emptyWeekData()
            coEvery { reportDataService.getWeeklyData(weekStart) } returns data

            val output = generator.generate(ReportPeriod.Week(weekStart))

            assertTrue(output.title.contains("Rapport Semaine du"))
            assertNotNull(output.markdownContent)
            assertNotNull(output.plainTextContent)
        }

        @Test
        fun `generate throws for non-Week period`() {
            assertThrows(IllegalArgumentException::class.java) {
                runTest {
                    generator.generate(ReportPeriod.Day(LocalDate.now()))
                }
            }
        }
    }
}
