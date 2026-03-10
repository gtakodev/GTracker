package com.devtrack.data.repository

import com.devtrack.domain.model.UserSettings

/**
 * Repository for UserSettings (P1.2.1).
 * Acts as a singleton: get returns the single row, save performs an upsert.
 */
interface UserSettingsRepository {
    suspend fun get(): UserSettings
    suspend fun save(settings: UserSettings)
}
