package com.devtrack.data.repository.impl

import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.Tables.UserSettingsTable
import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.UserSettings
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/**
 * Exposed-based implementation of [UserSettingsRepository] (P1.2.6).
 * Treats settings as a singleton row. If no row exists, creates one with defaults.
 */
class UserSettingsRepositoryImpl(
    private val databaseFactory: DatabaseFactory,
) : UserSettingsRepository {

    private fun ResultRow.toUserSettings(): UserSettings = UserSettings(
        id = UUID.fromString(this[UserSettingsTable.id]),
        locale = this[UserSettingsTable.locale],
        theme = this[UserSettingsTable.theme],
        inactivityThresholdMin = this[UserSettingsTable.inactivityThresholdMin],
        hoursPerDay = this[UserSettingsTable.hoursPerDay],
        halfDayThreshold = this[UserSettingsTable.halfDayThreshold],
        pomodoroWorkMin = this[UserSettingsTable.pomodoroWorkMin],
        pomodoroBreakMin = this[UserSettingsTable.pomodoroBreakMin],
        pomodoroLongBreakMin = this[UserSettingsTable.pomodoroLongBreakMin],
        pomodoroSessionsBeforeLong = this[UserSettingsTable.pomodoroSessionsBeforeLong],
        closeToTray = this[UserSettingsTable.closeToTray],
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(db = databaseFactory.getDatabase()) { block() }

    override suspend fun get(): UserSettings = dbQuery {
        val row = UserSettingsTable.selectAll().singleOrNull()
        if (row != null) {
            row.toUserSettings()
        } else {
            // Create default settings on first access
            val defaults = UserSettings()
            UserSettingsTable.insert {
                it[id] = defaults.id.toString()
                it[locale] = defaults.locale
                it[theme] = defaults.theme
                it[inactivityThresholdMin] = defaults.inactivityThresholdMin
                it[hoursPerDay] = defaults.hoursPerDay
                it[halfDayThreshold] = defaults.halfDayThreshold
                it[pomodoroWorkMin] = defaults.pomodoroWorkMin
                it[pomodoroBreakMin] = defaults.pomodoroBreakMin
                it[pomodoroLongBreakMin] = defaults.pomodoroLongBreakMin
                it[pomodoroSessionsBeforeLong] = defaults.pomodoroSessionsBeforeLong
                it[closeToTray] = defaults.closeToTray
            }
            defaults
        }
    }

    override suspend fun save(settings: UserSettings): Unit = dbQuery {
        val exists = UserSettingsTable.selectAll()
            .where { UserSettingsTable.id eq settings.id.toString() }
            .singleOrNull() != null

        if (exists) {
            UserSettingsTable.update({ UserSettingsTable.id eq settings.id.toString() }) {
                it[locale] = settings.locale
                it[theme] = settings.theme
                it[inactivityThresholdMin] = settings.inactivityThresholdMin
                it[hoursPerDay] = settings.hoursPerDay
                it[halfDayThreshold] = settings.halfDayThreshold
                it[pomodoroWorkMin] = settings.pomodoroWorkMin
                it[pomodoroBreakMin] = settings.pomodoroBreakMin
                it[pomodoroLongBreakMin] = settings.pomodoroLongBreakMin
                it[pomodoroSessionsBeforeLong] = settings.pomodoroSessionsBeforeLong
                it[closeToTray] = settings.closeToTray
            }
        } else {
            UserSettingsTable.insert {
                it[id] = settings.id.toString()
                it[locale] = settings.locale
                it[theme] = settings.theme
                it[inactivityThresholdMin] = settings.inactivityThresholdMin
                it[hoursPerDay] = settings.hoursPerDay
                it[halfDayThreshold] = settings.halfDayThreshold
                it[pomodoroWorkMin] = settings.pomodoroWorkMin
                it[pomodoroBreakMin] = settings.pomodoroBreakMin
                it[pomodoroLongBreakMin] = settings.pomodoroLongBreakMin
                it[pomodoroSessionsBeforeLong] = settings.pomodoroSessionsBeforeLong
                it[closeToTray] = settings.closeToTray
            }
        }
    }
}
