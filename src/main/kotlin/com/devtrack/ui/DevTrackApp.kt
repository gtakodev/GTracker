package com.devtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.devtrack.data.repository.UserSettingsRepository
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.theme.DevTrackTheme
import com.devtrack.viewmodel.BacklogViewModel
import com.devtrack.viewmodel.CalendarViewModel
import com.devtrack.viewmodel.CommandPaletteViewModel
import com.devtrack.viewmodel.ReportsViewModel
import com.devtrack.viewmodel.SettingsViewModel
import com.devtrack.viewmodel.TemplatesViewModel
import com.devtrack.viewmodel.TimelineViewModel
import com.devtrack.viewmodel.TodayViewModel
import org.koin.compose.koinInject

@Composable
fun DevTrackApp() {
    val navigationState: NavigationState = koinInject()
    val todayViewModel: TodayViewModel = koinInject()
    val backlogViewModel: BacklogViewModel = koinInject()
    val commandPaletteViewModel: CommandPaletteViewModel = koinInject()
    val reportsViewModel: ReportsViewModel = koinInject()
    val timelineViewModel: TimelineViewModel = koinInject()
    val templatesViewModel: TemplatesViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()
    val calendarViewModel: CalendarViewModel = koinInject()
    val userSettingsRepository: UserSettingsRepository = koinInject()
    val currentTheme by navigationState.themeMode.collectAsState()

    // Collect locale state so the entire composition tree recomposes on locale change (P2.6.3)
    @Suppress("UNUSED_VARIABLE")
    val currentLocale by I18n.localeState.collectAsState()

    // Load saved locale from UserSettings on first composition
    LaunchedEffect(Unit) {
        val settings = userSettingsRepository.get()
        I18n.setLocale(settings.locale)
    }

    DevTrackTheme(themeMode = currentTheme) {
        MainLayout(
            navigationState = navigationState,
            todayViewModel = todayViewModel,
            backlogViewModel = backlogViewModel,
            commandPaletteViewModel = commandPaletteViewModel,
            reportsViewModel = reportsViewModel,
            timelineViewModel = timelineViewModel,
            templatesViewModel = templatesViewModel,
            settingsViewModel = settingsViewModel,
            calendarViewModel = calendarViewModel,
        )
    }
}
