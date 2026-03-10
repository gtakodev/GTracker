package com.devtrack.domain.service

import com.devtrack.domain.model.EventType
import com.devtrack.domain.model.SessionEvent
import com.devtrack.domain.model.UserSettings
import com.devtrack.domain.model.WorkSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("TimeCalculator")
class TimeCalculatorTest {

    private val calculator = TimeCalculator()
    private val baseTime = Instant.parse("2026-03-09T09:00:00Z")

    private fun event(sessionId: UUID, type: EventType, offsetSeconds: Long): SessionEvent =
        SessionEvent(
            sessionId = sessionId,
            type = type,
            timestamp = baseTime.plusSeconds(offsetSeconds),
        )

    @Nested
    @DisplayName("calculateEffectiveTime")
    inner class CalculateEffectiveTimeTests {

        @Test
        fun `returns zero for empty events`() {
            assertEquals(Duration.ZERO, calculator.calculateEffectiveTime(emptyList()))
        }

        @Test
        fun `simple start to end session`() {
            val sid = UUID.randomUUID()
            val events = listOf(
                event(sid, EventType.START, 0),       // 09:00
                event(sid, EventType.END, 3600),       // 10:00
            )
            assertEquals(Duration.ofHours(1), calculator.calculateEffectiveTime(events))
        }

        @Test
        fun `session with one pause`() {
            val sid = UUID.randomUUID()
            val events = listOf(
                event(sid, EventType.START, 0),        // 09:00
                event(sid, EventType.PAUSE, 1800),     // 09:30 (30m work)
                event(sid, EventType.RESUME, 2400),    // 09:40 (10m pause)
                event(sid, EventType.END, 3600),       // 10:00 (20m work)
            )
            // Effective = 30m + 20m = 50m
            assertEquals(Duration.ofMinutes(50), calculator.calculateEffectiveTime(events))
        }

        @Test
        fun `session with multiple pauses`() {
            val sid = UUID.randomUUID()
            val events = listOf(
                event(sid, EventType.START, 0),        // 09:00
                event(sid, EventType.PAUSE, 600),      // 09:10 (10m work)
                event(sid, EventType.RESUME, 900),     // 09:15 (5m pause)
                event(sid, EventType.PAUSE, 1500),     // 09:25 (10m work)
                event(sid, EventType.RESUME, 1800),    // 09:30 (5m pause)
                event(sid, EventType.END, 2400),       // 09:40 (10m work)
            )
            // Effective = 10 + 10 + 10 = 30m
            assertEquals(Duration.ofMinutes(30), calculator.calculateEffectiveTime(events))
        }

        @Test
        fun `orphan session (no END) counts up to now`() {
            val sid = UUID.randomUUID()
            val events = listOf(
                event(sid, EventType.START, 0),        // 09:00
            )
            val now = baseTime.plusSeconds(7200) // 11:00
            assertEquals(Duration.ofHours(2), calculator.calculateEffectiveTime(events, now))
        }

        @Test
        fun `orphan session paused then resumed counts up to now`() {
            val sid = UUID.randomUUID()
            val events = listOf(
                event(sid, EventType.START, 0),        // 09:00
                event(sid, EventType.PAUSE, 1800),     // 09:30 (30m)
                event(sid, EventType.RESUME, 2400),    // 09:40
            )
            val now = baseTime.plusSeconds(3600) // 10:00 (20m after resume)
            // Effective = 30m + 20m = 50m
            assertEquals(Duration.ofMinutes(50), calculator.calculateEffectiveTime(events, now))
        }

        @Test
        fun `single START event only`() {
            val sid = UUID.randomUUID()
            val events = listOf(event(sid, EventType.START, 0))
            val now = baseTime.plusSeconds(600)
            assertEquals(Duration.ofMinutes(10), calculator.calculateEffectiveTime(events, now))
        }
    }

    @Nested
    @DisplayName("calculateTotalForTask")
    inner class CalculateTotalForTaskTests {

        @Test
        fun `sums time across multiple sessions`() {
            val taskId = UUID.randomUUID()
            val s1 = WorkSession(taskId = taskId, date = LocalDate.of(2026, 3, 9), startTime = baseTime)
            val s2 = WorkSession(taskId = taskId, date = LocalDate.of(2026, 3, 9), startTime = baseTime.plusSeconds(7200))

            val eventsMap = mapOf(
                s1.id to listOf(
                    event(s1.id, EventType.START, 0),
                    event(s1.id, EventType.END, 3600), // 1h
                ),
                s2.id to listOf(
                    event(s2.id, EventType.START, 7200),
                    event(s2.id, EventType.END, 9000), // 30m
                ),
            )

            val total = calculator.calculateTotalForTask(listOf(s1, s2), eventsMap)
            assertEquals(Duration.ofMinutes(90), total)
        }

        @Test
        fun `handles session with no events`() {
            val taskId = UUID.randomUUID()
            val s1 = WorkSession(taskId = taskId, date = LocalDate.of(2026, 3, 9), startTime = baseTime)

            val total = calculator.calculateTotalForTask(listOf(s1), emptyMap())
            assertEquals(Duration.ZERO, total)
        }

        @Test
        fun `handles empty sessions list`() {
            val total = calculator.calculateTotalForTask(emptyList(), emptyMap())
            assertEquals(Duration.ZERO, total)
        }
    }

    @Nested
    @DisplayName("convertToDays")
    inner class ConvertToDaysTests {

        private val settings = UserSettings(hoursPerDay = 8.0, halfDayThreshold = 4.0)

        @Test
        fun `zero duration returns 0`() {
            assertEquals(0.0, calculator.convertToDays(Duration.ZERO, settings))
        }

        @Test
        fun `exactly half day threshold returns 0_5`() {
            assertEquals(0.5, calculator.convertToDays(Duration.ofHours(4), settings))
        }

        @Test
        fun `exactly full day returns 1_0`() {
            assertEquals(1.0, calculator.convertToDays(Duration.ofHours(8), settings))
        }

        @Test
        fun `above full day`() {
            // 12 hours = 1 full day (8h) + 4h remainder >= halfDay -> 1.5
            assertEquals(1.5, calculator.convertToDays(Duration.ofHours(12), settings))
        }

        @Test
        fun `below half day is fractional`() {
            // 2 hours / 8 = 0.25
            assertEquals(0.25, calculator.convertToDays(Duration.ofHours(2), settings))
        }

        @Test
        fun `16 hours equals 2 full days`() {
            assertEquals(2.0, calculator.convertToDays(Duration.ofHours(16), settings))
        }
    }
}
