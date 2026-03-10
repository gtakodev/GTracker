package com.devtrack.ui.navigation

import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen(val titleKey: String, val icon: String) {
    /** Resolved title using current locale. */
    val title: String get() = I18n.t(titleKey)

    data object Today : Screen("nav.today", "today")
    data object Backlog : Screen("nav.backlog", "backlog")
    data object Timeline : Screen("nav.timeline", "timeline")
    data object Calendar : Screen("nav.calendar", "calendar")
    data object Reports : Screen("nav.reports", "reports")
    data object Settings : Screen("nav.settings", "settings")
    data object Templates : Screen("nav.templates", "templates")

    /** Temporary screen for visual theme verification (P0.6.4). Remove after validation. */
    data object ThemeTest : Screen("nav.theme_test", "theme_test")
}

class NavigationState {
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Today)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isSidebarExpanded = MutableStateFlow(true)
    val isSidebarExpanded: StateFlow<Boolean> = _isSidebarExpanded.asStateFlow()

    /**
     * Signal emitted when a report type should be pre-selected (P3.5.3).
     * The value is the report period key: "today", "week", "month", "standup".
     */
    private val _reportTypeSignal = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val reportTypeSignal: SharedFlow<String> = _reportTypeSignal.asSharedFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    /**
     * Navigate to the Reports screen with a pre-selected report type (P3.5.3).
     */
    fun navigateToReports(reportPeriod: String) {
        _reportTypeSignal.tryEmit(reportPeriod)
        _currentScreen.value = Screen.Reports
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun toggleSidebar() {
        _isSidebarExpanded.value = !_isSidebarExpanded.value
    }

    fun setSidebarExpanded(expanded: Boolean) {
        _isSidebarExpanded.value = expanded
    }
}
