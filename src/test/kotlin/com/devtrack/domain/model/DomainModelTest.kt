package com.devtrack.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DomainModelTest {

    @Nested
    inner class TaskCategoryTest {
        @Test
        fun `should have 8 categories`() {
            assertEquals(8, TaskCategory.entries.size)
        }

        @Test
        fun `should parse hashtags to categories`() {
            assertEquals(TaskCategory.DEVELOPMENT, TaskCategory.fromHashtag("dev"))
            assertEquals(TaskCategory.DEVELOPMENT, TaskCategory.fromHashtag("development"))
            assertEquals(TaskCategory.BUGFIX, TaskCategory.fromHashtag("bug"))
            assertEquals(TaskCategory.BUGFIX, TaskCategory.fromHashtag("bugfix"))
            assertEquals(TaskCategory.MEETING, TaskCategory.fromHashtag("meet"))
            assertEquals(TaskCategory.MEETING, TaskCategory.fromHashtag("meeting"))
            assertEquals(TaskCategory.REVIEW, TaskCategory.fromHashtag("review"))
            assertEquals(TaskCategory.DOCUMENTATION, TaskCategory.fromHashtag("doc"))
            assertEquals(TaskCategory.DOCUMENTATION, TaskCategory.fromHashtag("documentation"))
            assertEquals(TaskCategory.LEARNING, TaskCategory.fromHashtag("learn"))
            assertEquals(TaskCategory.LEARNING, TaskCategory.fromHashtag("learning"))
            assertEquals(TaskCategory.MAINTENANCE, TaskCategory.fromHashtag("maint"))
            assertEquals(TaskCategory.MAINTENANCE, TaskCategory.fromHashtag("maintenance"))
            assertEquals(TaskCategory.SUPPORT, TaskCategory.fromHashtag("support"))
        }

        @Test
        fun `should parse hashtags case-insensitively`() {
            assertEquals(TaskCategory.BUGFIX, TaskCategory.fromHashtag("BUG"))
            assertEquals(TaskCategory.BUGFIX, TaskCategory.fromHashtag("Bug"))
            assertEquals(TaskCategory.BUGFIX, TaskCategory.fromHashtag("BugFix"))
        }

        @Test
        fun `should return null for unknown hashtag`() {
            assertNull(TaskCategory.fromHashtag("unknown"))
            assertNull(TaskCategory.fromHashtag(""))
            assertNull(TaskCategory.fromHashtag("xyz"))
        }
    }

    @Nested
    inner class TaskStatusTest {
        @Test
        fun `should have 5 statuses`() {
            assertEquals(5, TaskStatus.entries.size)
            assertTrue(TaskStatus.entries.containsAll(
                listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED, TaskStatus.DONE, TaskStatus.ARCHIVED)
            ))
        }
    }

    @Nested
    inner class SessionSourceTest {
        @Test
        fun `should have 3 sources`() {
            assertEquals(3, SessionSource.entries.size)
            assertTrue(SessionSource.entries.containsAll(
                listOf(SessionSource.TIMER, SessionSource.MANUAL, SessionSource.POMODORO)
            ))
        }
    }

    @Nested
    inner class EventTypeTest {
        @Test
        fun `should have 4 event types`() {
            assertEquals(4, EventType.entries.size)
            assertTrue(EventType.entries.containsAll(
                listOf(EventType.START, EventType.PAUSE, EventType.RESUME, EventType.END)
            ))
        }
    }

    @Nested
    inner class TaskTest {
        @Test
        fun `should have correct defaults`() {
            val task = Task(title = "Test task")
            assertNull(task.parentId)
            assertNull(task.description)
            assertEquals(TaskCategory.DEVELOPMENT, task.category)
            assertEquals(emptyList<String>(), task.jiraTickets)
            assertEquals(TaskStatus.TODO, task.status)
            assertNull(task.plannedDate)
            assertFalse(task.isTemplate)
            assertNotNull(task.id)
            assertNotNull(task.createdAt)
            assertNotNull(task.updatedAt)
        }

        @Test
        fun `should store jira tickets as list`() {
            val task = Task(
                title = "DPD-1423 fix pagination",
                jiraTickets = listOf("DPD-1423"),
            )
            assertEquals(listOf("DPD-1423"), task.jiraTickets)
        }

        @Test
        fun `should store multiple jira tickets`() {
            val task = Task(
                title = "DPD-1423 DPD-2456 cross-module fix",
                jiraTickets = listOf("DPD-1423", "DPD-2456"),
            )
            assertEquals(2, task.jiraTickets.size)
        }

        @Test
        fun `jira tickets should serialize to JSON`() {
            val tickets = listOf("DPD-1423", "DPD-2456")
            val json = Json.encodeToString(tickets)
            assertEquals("""["DPD-1423","DPD-2456"]""", json)

            val deserialized = Json.decodeFromString<List<String>>(json)
            assertEquals(tickets, deserialized)
        }

        @Test
        fun `empty jira tickets should serialize to empty JSON array`() {
            val tickets = emptyList<String>()
            val json = Json.encodeToString(tickets)
            assertEquals("[]", json)

            val deserialized = Json.decodeFromString<List<String>>(json)
            assertEquals(tickets, deserialized)
        }

        @Test
        fun `should support parent-child relationship`() {
            val parentId = UUID.randomUUID()
            val child = Task(title = "Sub-task", parentId = parentId)
            assertEquals(parentId, child.parentId)
        }

        @Test
        fun `should support planned date`() {
            val date = LocalDate.of(2026, 3, 9)
            val task = Task(title = "Planned", plannedDate = date)
            assertEquals(date, task.plannedDate)
        }
    }

    @Nested
    inner class WorkSessionTest {
        @Test
        fun `should have correct defaults`() {
            val taskId = UUID.randomUUID()
            val session = WorkSession(
                taskId = taskId,
                date = LocalDate.now(),
                startTime = Instant.now(),
            )
            assertNull(session.endTime)
            assertEquals(SessionSource.TIMER, session.source)
            assertNull(session.notes)
            assertEquals(taskId, session.taskId)
        }

        @Test
        fun `null endTime indicates orphan session`() {
            val session = WorkSession(
                taskId = UUID.randomUUID(),
                date = LocalDate.now(),
                startTime = Instant.now(),
            )
            assertNull(session.endTime)
        }
    }

    @Nested
    inner class SessionEventTest {
        @Test
        fun `should store event data`() {
            val sessionId = UUID.randomUUID()
            val now = Instant.now()
            val event = SessionEvent(
                sessionId = sessionId,
                type = EventType.START,
                timestamp = now,
            )
            assertEquals(sessionId, event.sessionId)
            assertEquals(EventType.START, event.type)
            assertEquals(now, event.timestamp)
        }
    }

    @Nested
    inner class TemplateTaskTest {
        @Test
        fun `should have correct defaults`() {
            val template = TemplateTask(
                title = "Daily standup",
                category = TaskCategory.MEETING,
            )
            assertNull(template.defaultDurationMin)
            assertEquals("Daily standup", template.title)
            assertEquals(TaskCategory.MEETING, template.category)
        }

        @Test
        fun `should support default duration`() {
            val template = TemplateTask(
                title = "Code review",
                category = TaskCategory.REVIEW,
                defaultDurationMin = 30,
            )
            assertEquals(30, template.defaultDurationMin)
        }
    }

    @Nested
    inner class UserSettingsTest {
        @Test
        fun `should have correct defaults matching PRD`() {
            val settings = UserSettings()
            assertEquals("fr", settings.locale)
            assertEquals("SYSTEM", settings.theme)
            assertEquals(30, settings.inactivityThresholdMin)
            assertEquals(8.0, settings.hoursPerDay)
            assertEquals(4.0, settings.halfDayThreshold)
            assertEquals(25, settings.pomodoroWorkMin)
            assertEquals(5, settings.pomodoroBreakMin)
            assertEquals(15, settings.pomodoroLongBreakMin)
            assertEquals(4, settings.pomodoroSessionsBeforeLong)
        }
    }

    @Nested
    inner class TaskWithTimeTest {
        @Test
        fun `should calculate progress for sub-tasks`() {
            val twt = TaskWithTime(
                task = Task(title = "Parent"),
                subTaskCount = 3,
                completedSubTaskCount = 2,
            )
            assertEquals(2.0 / 3.0, twt.progress)
        }

        @Test
        fun `should return null progress when no sub-tasks`() {
            val twt = TaskWithTime(task = Task(title = "Leaf"))
            assertNull(twt.progress)
        }

        @Test
        fun `should have zero duration by default`() {
            val twt = TaskWithTime(task = Task(title = "New"))
            assertEquals(Duration.ZERO, twt.totalDuration)
            assertEquals(0, twt.sessionCount)
        }
    }

    @Nested
    inner class ActiveSessionStateTest {
        @Test
        fun `should represent active session`() {
            val task = Task(title = "Active task")
            val session = WorkSession(
                taskId = task.id,
                date = LocalDate.now(),
                startTime = Instant.now(),
            )
            val event = SessionEvent(
                sessionId = session.id,
                type = EventType.START,
                timestamp = session.startTime,
            )
            val state = ActiveSessionState(
                session = session,
                task = task,
                events = listOf(event),
                effectiveDuration = Duration.ofMinutes(30),
                isPaused = false,
            )
            assertFalse(state.isPaused)
            assertEquals(Duration.ofMinutes(30), state.effectiveDuration)
            assertEquals(1, state.events.size)
        }
    }

    @Nested
    inner class DailyReportTest {
        @Test
        fun `should aggregate ticket entries`() {
            val report = DailyReport(
                date = LocalDate.of(2026, 3, 9),
                ticketEntries = listOf(
                    DailyReport.TicketEntry("DPD-1423", "Fix pagination", Duration.ofMinutes(135)),
                    DailyReport.TicketEntry("DPD-2456", "Analyse cache", Duration.ofMinutes(90)),
                ),
                noTicketEntries = listOf(
                    DailyReport.NoTicketEntry("Daily standup", TaskCategory.MEETING, Duration.ofMinutes(30)),
                ),
                totalDuration = Duration.ofMinutes(255),
            )
            assertEquals(2, report.ticketEntries.size)
            assertEquals(1, report.noTicketEntries.size)
            assertEquals(Duration.ofMinutes(255), report.totalDuration)
        }
    }
}
