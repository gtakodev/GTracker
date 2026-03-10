package com.devtrack.viewmodel

import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.domain.model.UserSettings
import com.devtrack.infrastructure.backup.BackupService
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.theme.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * UI state for the Settings screen (P4.6.1).
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val snackbarMessage: String? = null,
    // Appearance
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val locale: String = "fr",
    // Timer
    val inactivityThresholdMin: Int = 30,
    // Pomodoro
    val pomodoroWorkMin: Int = 25,
    val pomodoroBreakMin: Int = 5,
    val pomodoroLongBreakMin: Int = 15,
    val pomodoroSessionsBeforeLong: Int = 4,
    // Reports
    val hoursPerDay: String = "8.0",
    val halfDayThreshold: String = "4.0",
    // Data
    val databasePath: String = "",
    val showImportConfirmation: Boolean = false,
    val pendingImportPath: Path? = null,
    // Behavior
    val closeToTray: Boolean = false,
    // About
    val appVersion: String = "1.0.0",
)

/**
 * ViewModel for the Settings screen (P4.6.1).
 * Loads UserSettings on init and saves each modification immediately.
 * Theme and locale changes are applied in real-time (P4.6.3).
 */
class SettingsViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val navigationState: NavigationState,
    private val backupService: BackupService? = null,
    dispatcher: CoroutineContext = Dispatchers.Main,
) {
    private val logger = LoggerFactory.getLogger(SettingsViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Holds the current persisted settings so we can update individual fields. */
    private var currentSettings: UserSettings = UserSettings()

    init {
        loadSettings()
    }

    /** Load settings from the repository. */
    fun loadSettings() {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val settings = userSettingsRepository.get()
                currentSettings = settings

                val themeMode = try {
                    ThemeMode.valueOf(settings.theme)
                } catch (_: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        theme = themeMode,
                        locale = settings.locale,
                        inactivityThresholdMin = settings.inactivityThresholdMin,
                        pomodoroWorkMin = settings.pomodoroWorkMin,
                        pomodoroBreakMin = settings.pomodoroBreakMin,
                        pomodoroLongBreakMin = settings.pomodoroLongBreakMin,
                        pomodoroSessionsBeforeLong = settings.pomodoroSessionsBeforeLong,
                        hoursPerDay = settings.hoursPerDay.toString(),
                        halfDayThreshold = settings.halfDayThreshold.toString(),
                        closeToTray = settings.closeToTray,
                    )
                }

                // Apply loaded theme and locale immediately
                navigationState.setThemeMode(themeMode)
                I18n.setLocale(settings.locale)
            } catch (e: Exception) {
                logger.error("Failed to load settings", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    // -- Appearance --

    /** Change theme and apply immediately (P4.6.3). */
    fun setTheme(mode: ThemeMode) {
        _uiState.update { it.copy(theme = mode) }
        navigationState.setThemeMode(mode)
        saveField { it.copy(theme = mode.name) }
    }

    /** Change locale and apply immediately (P4.6.3). */
    fun setLocale(locale: String) {
        _uiState.update { it.copy(locale = locale) }
        I18n.setLocale(locale)
        saveField { it.copy(locale = locale) }
    }

    // -- Timer --

    /** Change inactivity threshold (picked up at next cycle). */
    fun setInactivityThreshold(minutes: Int) {
        val clamped = minutes.coerceIn(5, 120)
        _uiState.update { it.copy(inactivityThresholdMin = clamped) }
        saveField { it.copy(inactivityThresholdMin = clamped) }
    }

    // -- Pomodoro --

    fun setPomodoroWorkMin(minutes: Int) {
        val clamped = minutes.coerceIn(15, 60)
        _uiState.update { it.copy(pomodoroWorkMin = clamped) }
        saveField { it.copy(pomodoroWorkMin = clamped) }
    }

    fun setPomodoroBreakMin(minutes: Int) {
        val clamped = minutes.coerceIn(1, 15)
        _uiState.update { it.copy(pomodoroBreakMin = clamped) }
        saveField { it.copy(pomodoroBreakMin = clamped) }
    }

    fun setPomodoroLongBreakMin(minutes: Int) {
        val clamped = minutes.coerceIn(5, 30)
        _uiState.update { it.copy(pomodoroLongBreakMin = clamped) }
        saveField { it.copy(pomodoroLongBreakMin = clamped) }
    }

    fun setPomodoroSessionsBeforeLong(sessions: Int) {
        val clamped = sessions.coerceIn(1, 8)
        _uiState.update { it.copy(pomodoroSessionsBeforeLong = clamped) }
        saveField { it.copy(pomodoroSessionsBeforeLong = clamped) }
    }

    // -- Reports --

    fun setHoursPerDay(value: String) {
        _uiState.update { it.copy(hoursPerDay = value) }
        val parsed = value.toDoubleOrNull()
        if (parsed != null && parsed in 1.0..24.0) {
            saveField { it.copy(hoursPerDay = parsed) }
        }
    }

    fun setHalfDayThreshold(value: String) {
        _uiState.update { it.copy(halfDayThreshold = value) }
        val parsed = value.toDoubleOrNull()
        if (parsed != null && parsed in 0.5..12.0) {
            saveField { it.copy(halfDayThreshold = parsed) }
        }
    }

    // -- Data / Backup (P4.7.2) --

    /** Change close-to-tray behavior. */
    fun setCloseToTray(enabled: Boolean) {
        _uiState.update { it.copy(closeToTray = enabled) }
        saveField { it.copy(closeToTray = enabled) }
    }

    fun setDatabasePath(path: String) {
        _uiState.update { it.copy(databasePath = path) }
    }

    /** Export a backup to the given path. */
    fun exportBackup(destination: Path) {
        scope.launch {
            try {
                val service = backupService ?: return@launch
                val result = service.exportBackup(destination)
                if (result.success) {
                    val sizeKb = result.fileSizeBytes / 1024
                    _uiState.update { it.copy(snackbarMessage = "backup.export.success") }
                    logger.info("Backup exported: {} ({} KB)", result.filePath, sizeKb)
                } else {
                    _uiState.update { it.copy(error = I18n.t("backup.export.error", result.error ?: "")) }
                }
            } catch (e: Exception) {
                logger.error("Failed to export backup", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Request import — shows confirmation dialog first. */
    fun requestImportBackup(source: Path) {
        _uiState.update { it.copy(showImportConfirmation = true, pendingImportPath = source) }
    }

    /** Cancel the pending import. */
    fun cancelImport() {
        _uiState.update { it.copy(showImportConfirmation = false, pendingImportPath = null) }
    }

    /** Confirm and execute the import. */
    fun confirmImportBackup() {
        val importPath = _uiState.value.pendingImportPath ?: return
        _uiState.update { it.copy(showImportConfirmation = false, pendingImportPath = null) }

        scope.launch {
            try {
                val service = backupService ?: return@launch
                val result = service.importBackup(importPath)
                if (result.success) {
                    _uiState.update {
                        it.copy(snackbarMessage = "backup.import.success")
                    }
                    logger.info("Backup imported: {} tasks, {} sessions", result.taskCount, result.sessionCount)
                    // Reload settings since the DB was replaced
                    loadSettings()
                } else {
                    _uiState.update { it.copy(error = I18n.t("backup.import.error", result.error ?: "")) }
                }
            } catch (e: Exception) {
                logger.error("Failed to import backup", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // -- Utility --

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun dispose() {
        scope.cancel()
    }

    /**
     * Save a single field change. Updates the in-memory [currentSettings] and persists.
     */
    private fun saveField(update: (UserSettings) -> UserSettings) {
        scope.launch {
            try {
                currentSettings = update(currentSettings)
                userSettingsRepository.save(currentSettings)
                logger.debug("Settings saved: {}", currentSettings)
            } catch (e: Exception) {
                logger.error("Failed to save settings", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
