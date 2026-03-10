package com.devtrack.domain.service

import com.devtrack.domain.model.TaskCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("JiraTicketParser")
class JiraTicketParserTest {

    private val parser = JiraTicketParser()

    @Nested
    @DisplayName("extractTickets")
    inner class ExtractTicketsTests {

        @Test
        fun `returns empty list for title with no tickets`() {
            assertEquals(emptyList<String>(), parser.extractTickets("Fix the login bug"))
        }

        @Test
        fun `extracts single ticket`() {
            assertEquals(listOf("PROJ-123"), parser.extractTickets("PROJ-123 Fix login"))
        }

        @Test
        fun `extracts multiple tickets`() {
            assertEquals(
                listOf("DPD-1423", "DPD-2456"),
                parser.extractTickets("DPD-1423 DPD-2456 Fix pagination and cache"),
            )
        }

        @Test
        fun `extracts ticket from middle of title`() {
            assertEquals(listOf("ABC-99"), parser.extractTickets("Fix ABC-99 regression"))
        }

        @Test
        fun `deduplicates repeated tickets`() {
            assertEquals(listOf("FOO-1"), parser.extractTickets("FOO-1 related to FOO-1"))
        }

        @Test
        fun `ignores lowercase patterns`() {
            assertEquals(emptyList<String>(), parser.extractTickets("proj-123 fix"))
        }

        @Test
        fun `handles 2-char project key`() {
            assertEquals(listOf("AB-1"), parser.extractTickets("AB-1 short key"))
        }

        @Test
        fun `handles 10-char project key`() {
            assertEquals(listOf("ABCDEFGHIJ-999"), parser.extractTickets("ABCDEFGHIJ-999 long key"))
        }

        @Test
        fun `ignores 1-char project key (too short)`() {
            assertEquals(emptyList<String>(), parser.extractTickets("A-123 single char"))
        }

        @Test
        fun `handles empty string`() {
            assertEquals(emptyList<String>(), parser.extractTickets(""))
        }
    }

    @Nested
    @DisplayName("extractCategory")
    inner class ExtractCategoryTests {

        @Test
        fun `returns null for title with no hashtags`() {
            assertNull(parser.extractCategory("Fix the login bug"))
        }

        @Test
        fun `extracts bugfix from #bug`() {
            assertEquals(TaskCategory.BUGFIX, parser.extractCategory("Fix login #bug"))
        }

        @Test
        fun `extracts bugfix from #bugfix`() {
            assertEquals(TaskCategory.BUGFIX, parser.extractCategory("Fix login #bugfix"))
        }

        @Test
        fun `extracts development from #dev`() {
            assertEquals(TaskCategory.DEVELOPMENT, parser.extractCategory("New feature #dev"))
        }

        @Test
        fun `extracts meeting from #meeting`() {
            assertEquals(TaskCategory.MEETING, parser.extractCategory("Daily standup #meeting"))
        }

        @Test
        fun `extracts review from #review`() {
            assertEquals(TaskCategory.REVIEW, parser.extractCategory("PR review #review"))
        }

        @Test
        fun `extracts documentation from #doc`() {
            assertEquals(TaskCategory.DOCUMENTATION, parser.extractCategory("Write docs #doc"))
        }

        @Test
        fun `extracts learning from #learn`() {
            assertEquals(TaskCategory.LEARNING, parser.extractCategory("Study Kotlin #learn"))
        }

        @Test
        fun `extracts maintenance from #maint`() {
            assertEquals(TaskCategory.MAINTENANCE, parser.extractCategory("Update deps #maint"))
        }

        @Test
        fun `extracts support from #support`() {
            assertEquals(TaskCategory.SUPPORT, parser.extractCategory("Help user #support"))
        }

        @Test
        fun `returns first matching category when multiple hashtags`() {
            assertEquals(TaskCategory.BUGFIX, parser.extractCategory("Fix #bug and #dev"))
        }

        @Test
        fun `ignores unrecognized hashtags`() {
            assertNull(parser.extractCategory("Random #yolo tag"))
        }

        @Test
        fun `is case insensitive`() {
            assertEquals(TaskCategory.BUGFIX, parser.extractCategory("Fix #BUG"))
        }
    }

    @Nested
    @DisplayName("cleanTitle")
    inner class CleanTitleTests {

        @Test
        fun `removes single hashtag`() {
            assertEquals("Fix login", parser.cleanTitle("Fix login #bug"))
        }

        @Test
        fun `removes multiple hashtags`() {
            assertEquals("Fix login", parser.cleanTitle("#bug Fix login #dev"))
        }

        @Test
        fun `preserves ticket identifiers`() {
            assertEquals("PROJ-123 Fix login", parser.cleanTitle("PROJ-123 Fix login #bugfix"))
        }

        @Test
        fun `collapses extra whitespace`() {
            assertEquals("Fix the bug", parser.cleanTitle("Fix  the  #bug  bug"))
        }

        @Test
        fun `handles title with only hashtags`() {
            assertEquals("", parser.cleanTitle("#bug #dev"))
        }

        @Test
        fun `handles empty string`() {
            assertEquals("", parser.cleanTitle(""))
        }

        @Test
        fun `handles title with no hashtags`() {
            assertEquals("No hashtags here", parser.cleanTitle("No hashtags here"))
        }
    }
}
