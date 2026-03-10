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
 * Tests for MonthlyReportGenerator (P3.2.3).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyReportGeneratorTest {

    private val reportDataService = mockk<ReportDataService>()
    private val timeCalculator = TimeCalculator()
    private val userSettingsRepository = mockk<UserSettingsRepository>()

    private lateinit var generator: MonthlyReportGenerator

    private val defaultSettings = UserSettings(hoursPerDay = 8.0, halfDayThreshold = 4.0)

    @BeforeEach
    fun setup() {
        generator = MonthlyReportGenerator(
            reportDataService = reportDataService,
            timeCalculator = timeCalculator,
            userSettingsRepository = userSettingsRepository,
        )
        coEvery { userSettingsRepository.get() } returns defaultSettings
    }

    @Nested
    inner class RoundToHalfDayTests {

        @Test
        fun `zero duration rounds to 0`() {
            assertEquals(0.0, generator.roundToHalfDay(Duration.ZERO, defaultSettings))
        }

        @Test
        fun `8h rounds to 1 day`() {
            assertEquals(1.0, generator.roundToHalfDay(Duration.ofHours(8), defaultSettings))
        }

        @Test
        fun `4h rounds to 0_5 day`() {
            assertEquals(0.5, generator.roundToHalfDay(Duration.ofHours(4), defaultSettings))
        }

        @Test
        fun `2h rounds to 0_5 day (nearest half)`() {
            // 2h = 0.25 day raw -> rounds to 0.5
            assertEquals(0.5, generator.roundToHalfDay(Duration.ofHours(2), defaultSettings))
        }

        @Test
        fun `1h rounds to 0`() {
            // 1h = 0.125 day raw -> rounds to 0.0
            assertEquals(0.0, generator.roundToHalfDay(Duration.ofHours(1), defaultSettings))
        }

        @Test
        fun `12h rounds to 1_5 days`() {
            assertEquals(1.5, generator.roundToHalfDay(Duration.ofHours(12), defaultSettings))
        }

        @Test
        fun `16h rounds to 2 days`() {
            assertEquals(2.0, generator.roundToHalfDay(Duration.ofHours(16), defaultSettings))
        }

        @Test
        fun `negative duration rounds to 0`() {
            assertEquals(0.0, generator.roundToHalfDay(Duration.ofHours(-1), defaultSettings))
        }
    }

    @Nested
    inner class FormatDayValueTests {

        @Test
        fun `zero formats as empty string`() {
            assertEquals("", MonthlyReportGenerator.formatDayValue(0.0))
        }

        @Test
        fun `1_0 formats without decimal`() {
            assertEquals("1", MonthlyReportGenerator.formatDayValue(1.0))
        }

        @Test
        fun `0_5 formats with decimal`() {
            assertEquals("0.5", MonthlyReportGenerator.formatDayValue(0.5))
        }

        @Test
        fun `2_0 formats without decimal`() {
            assertEquals("2", MonthlyReportGenerator.formatDayValue(2.0))
        }

        @Test
        fun `1_5 formats with decimal`() {
            assertEquals("1.5", MonthlyReportGenerator.formatDayValue(1.5))
        }
    }

    @Nested
    inner class RenderMarkdownTests {

        @Test
        fun `empty month renders no-activity message`() {
            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = emptyMap(),
                allTickets = emptyList(),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ZERO,
                workingDays = 22,
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("CRA - Mars 2026"))
            assertTrue(md.contains("Aucune activite enregistree ce mois."))
            assertFalse(md.contains("| Ticket"))
        }

        @Test
        fun `month with tickets renders cross-table`() {
            val date1 = LocalDate.of(2026, 3, 2)
            val date2 = LocalDate.of(2026, 3, 3)

            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = mapOf(
                    date1 to DailyReportData(
                        date = date1,
                        ticketDurations = mapOf("DPD-100" to Duration.ofHours(8)),
                        noTicketDurations = emptyMap(),
                        ticketDescriptions = mapOf("DPD-100" to "Fix login"),
                        totalDuration = Duration.ofHours(8),
                    ),
                    date2 to DailyReportData(
                        date = date2,
                        ticketDurations = mapOf("DPD-100" to Duration.ofHours(4)),
                        noTicketDurations = emptyMap(),
                        ticketDescriptions = mapOf("DPD-100" to "Fix login"),
                        totalDuration = Duration.ofHours(4),
                    ),
                ),
                allTickets = listOf("DPD-100"),
                ticketDescriptions = mapOf("DPD-100" to "Fix login"),
                totalDuration = Duration.ofHours(12),
                workingDays = 22,
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("CRA - Mars 2026"))
            assertTrue(md.contains("| Ticket "))
            assertTrue(md.contains("`DPD-100`"))
            assertTrue(md.contains("| **Total** "))
            // Should contain day-value legend
            assertTrue(md.contains("1 jour = 8.0h"))
        }

        @Test
        fun `month with non-ticket work renders Reunions row`() {
            val date1 = LocalDate.of(2026, 3, 5)

            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = mapOf(
                    date1 to DailyReportData(
                        date = date1,
                        ticketDurations = emptyMap(),
                        noTicketDurations = mapOf(TaskCategory.MEETING to Duration.ofHours(2)),
                        ticketDescriptions = emptyMap(),
                        totalDuration = Duration.ofHours(2),
                    ),
                ),
                allTickets = emptyList(),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ofHours(2),
                workingDays = 22,
            )

            val md = generator.renderMarkdown(data, defaultSettings)

            assertTrue(md.contains("*Reunions/Annexes*"))
            assertTrue(md.contains("| **Total** "))
        }

        @Test
        fun `month header has 31 day columns for March`() {
            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = mapOf(
                    LocalDate.of(2026, 3, 1) to DailyReportData(
                        date = LocalDate.of(2026, 3, 1),
                        ticketDurations = mapOf("X-1" to Duration.ofHours(8)),
                        noTicketDurations = emptyMap(),
                        ticketDescriptions = emptyMap(),
                        totalDuration = Duration.ofHours(8),
                    ),
                ),
                allTickets = listOf("X-1"),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ofHours(8),
                workingDays = 22,
            )

            val md = generator.renderMarkdown(data, defaultSettings)
            val headerLine = md.lines().first { it.startsWith("| Ticket") }

            // 31 day columns + Ticket + Total = 33 cell separators
            // Count "|" chars — should be (1 + 31 + 1 + 1) pipe chars = 34
            val pipeCount = headerLine.count { it == '|' }
            assertEquals(34, pipeCount)
        }
    }

    @Nested
    inner class RenderPlainTextTests {

        @Test
        fun `empty month renders no-activity message in plain text`() {
            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = emptyMap(),
                allTickets = emptyList(),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ZERO,
                workingDays = 22,
            )

            val text = generator.renderPlainText(data, defaultSettings)

            assertTrue(text.contains("CRA - Mars 2026"))
            assertTrue(text.contains("Aucune activite enregistree ce mois."))
        }

        @Test
        fun `plain text lists tickets with totals`() {
            val date1 = LocalDate.of(2026, 3, 2)

            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = mapOf(
                    date1 to DailyReportData(
                        date = date1,
                        ticketDurations = mapOf("DPD-100" to Duration.ofHours(8)),
                        noTicketDurations = emptyMap(),
                        ticketDescriptions = mapOf("DPD-100" to "Fix login"),
                        totalDuration = Duration.ofHours(8),
                    ),
                ),
                allTickets = listOf("DPD-100"),
                ticketDescriptions = mapOf("DPD-100" to "Fix login"),
                totalDuration = Duration.ofHours(8),
                workingDays = 22,
            )

            val text = generator.renderPlainText(data, defaultSettings)

            assertTrue(text.contains("DPD-100"))
            assertTrue(text.contains("Fix login"))
            assertTrue(text.contains("Total:"))
        }
    }

    @Nested
    inner class GenerateTests {

        @Test
        fun `generate calls service and returns ReportOutput`() = runTest {
            val data = MonthlyReportData(
                year = 2026,
                month = 3,
                dailyData = emptyMap(),
                allTickets = emptyList(),
                ticketDescriptions = emptyMap(),
                totalDuration = Duration.ZERO,
                workingDays = 22,
            )

            coEvery { reportDataService.getMonthlyData(2026, 3) } returns data

            val output = generator.generate(ReportPeriod.Month(2026, 3))

            assertTrue(output.title.contains("Mars 2026"))
            assertNotNull(output.markdownContent)
            assertNotNull(output.plainTextContent)
        }

        @Test
        fun `generate throws for non-Month period`() {
            assertThrows(IllegalArgumentException::class.java) {
                runTest {
                    generator.generate(ReportPeriod.Day(LocalDate.now()))
                }
            }
        }
    }
}
