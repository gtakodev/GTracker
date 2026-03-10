package com.devtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.ActiveSessionState
import com.devtrack.domain.service.PomodoroState
import com.devtrack.ui.components.CommandPalette
import com.devtrack.ui.components.InactivityDialog
import com.devtrack.ui.components.OrphanResolution
import com.devtrack.ui.components.OrphanSessionDialog
import com.devtrack.ui.components.TimerWidget
import com.devtrack.ui.components.formatDuration
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.navigation.NavigationState
import com.devtrack.ui.navigation.Screen
import com.devtrack.ui.screens.ThemeTestScreen
import com.devtrack.ui.screens.SettingsScreen
import com.devtrack.ui.screens.CalendarScreen
import com.devtrack.ui.screens.TemplatesScreen
import com.devtrack.ui.screens.TimelineScreen
import com.devtrack.ui.screens.TodayScreen
import com.devtrack.ui.screens.ReportsScreen
import com.devtrack.ui.screens.backlog.BacklogScreen
import com.devtrack.ui.theme.ThemeMode
import com.devtrack.viewmodel.BacklogViewModel
import com.devtrack.viewmodel.CalendarViewModel
import com.devtrack.viewmodel.CommandPaletteViewModel
import com.devtrack.viewmodel.PaletteMode
import com.devtrack.viewmodel.ReportsViewModel
import com.devtrack.viewmodel.SettingsViewModel
import com.devtrack.viewmodel.TemplatesViewModel
import com.devtrack.viewmodel.TimelineViewModel
import com.devtrack.viewmodel.TodayUiState
import com.devtrack.viewmodel.TodayViewModel
import java.time.Duration

@Composable
fun MainLayout(
    navigationState: NavigationState,
    todayViewModel: TodayViewModel,
    backlogViewModel: BacklogViewModel,
    commandPaletteViewModel: CommandPaletteViewModel,
    reportsViewModel: ReportsViewModel,
    timelineViewModel: TimelineViewModel,
    templatesViewModel: TemplatesViewModel,
    settingsViewModel: SettingsViewModel,
    calendarViewModel: CalendarViewModel,
) {
    val currentScreen by navigationState.currentScreen.collectAsState()
    val isSidebarExpanded by navigationState.isSidebarExpanded.collectAsState()
    val themeMode by navigationState.themeMode.collectAsState()
    val activeSession by todayViewModel.activeSession.collectAsState()
    val uiState by todayViewModel.uiState.collectAsState()
    val paletteState by commandPaletteViewModel.uiState.collectAsState()
    val pomodoroState by todayViewModel.pomodoroState.collectAsState()

    // React to command execution reload signals
    LaunchedEffect(Unit) {
        commandPaletteViewModel.reloadSignal.collect {
            todayViewModel.loadTasks()
            backlogViewModel.loadTasks()
        }
    }

    // Show snackbar for command palette feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(paletteState.feedbackMessage) {
        val message = paletteState.feedbackMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            commandPaletteViewModel.dismissFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { keyEvent ->
                    // Track user activity for inactivity detection (P2.4.3)
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        todayViewModel.recordUserActivity()
                    }
                    handleGlobalKeyEvent(keyEvent, navigationState, commandPaletteViewModel)
                }
                .pointerInput(Unit) {
                    // Track mouse activity for inactivity detection (P2.4.3)
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Move || event.type == PointerEventType.Press) {
                                todayViewModel.recordUserActivity()
                            }
                        }
                    }
                }
        ) {
        // Top Bar
        TopBar(
            themeMode = themeMode,
            activeSession = activeSession,
            pomodoroState = pomodoroState,
            onThemeToggle = {
                val nextMode = when (themeMode) {
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                }
                navigationState.setThemeMode(nextMode)
            },
            onPauseSession = { todayViewModel.pauseSession() },
            onResumeSession = { todayViewModel.resumeSession() },
            onStopSession = { todayViewModel.stopSession() },
            onPomodoroStop = { todayViewModel.stopPomodoro() },
            onPomodoroSkip = { todayViewModel.skipPomodoroPhase() },
        )

        // Main content area with sidebar
        Row(modifier = Modifier.weight(1f)) {
            // Sidebar
            Sidebar(
                currentScreen = currentScreen,
                isExpanded = isSidebarExpanded,
                onNavigate = { navigationState.navigateTo(it) },
                onToggleExpand = { navigationState.toggleSidebar() },
            )

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp)
            ) {
                ContentArea(currentScreen, todayViewModel, backlogViewModel, reportsViewModel, timelineViewModel, templatesViewModel, settingsViewModel, calendarViewModel)
            }
        }

        // Status Bar
        StatusBar(
            activeSession = activeSession,
            totalTimeToday = uiState.totalTimeToday,
        )
        }

        // Command Palette overlay
        CommandPalette(viewModel = commandPaletteViewModel)

        // Orphan Session Dialog (P2.4.2) — shown at startup if orphans detected
        if (uiState.showOrphanDialog) {
            OrphanSessionDialog(
                orphanSessions = uiState.orphanSessions,
                onResolve = { sessionId, resolution ->
                    when (resolution) {
                        OrphanResolution.CLOSE_AT_LAST_ACTIVITY ->
                            todayViewModel.resolveOrphanSession(sessionId, closeAtLastActivity = true)
                        OrphanResolution.CLOSE_NOW ->
                            todayViewModel.resolveOrphanSession(sessionId, closeAtLastActivity = false)
                        OrphanResolution.EDIT_MANUALLY -> {
                            // TODO: Open ManualSessionEditor for this session (P2.7)
                            todayViewModel.resolveOrphanSession(sessionId, closeAtLastActivity = true)
                        }
                    }
                },
                onDismiss = { todayViewModel.dismissOrphanDialog() },
            )
        }

        // Inactivity Dialog (P2.4.4) — shown when user is idle with active session
        if (uiState.showInactivityDialog) {
            InactivityDialog(
                taskTitle = uiState.inactiveTaskTitle,
                inactiveMinutes = uiState.inactiveMinutes,
                onContinue = { todayViewModel.handleInactivityContinue() },
                onAutoPause = { todayViewModel.handleInactivityAutoPause() },
                onStop = { todayViewModel.handleInactivityStop() },
            )
        }

        // Snackbar for command palette feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun TopBar(
    themeMode: ThemeMode,
    activeSession: ActiveSessionState?,
    pomodoroState: PomodoroState = PomodoroState(),
    onThemeToggle: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onStopSession: () -> Unit,
    onPomodoroStop: () -> Unit = {},
    onPomodoroSkip: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Logo and title
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = I18n.t("app.name"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = I18n.t("app.name"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Timer widget (P1.4.5, P4.4.2)
            TimerWidget(
                activeSession = activeSession,
                pomodoroState = pomodoroState,
                onPause = onPauseSession,
                onResume = onResumeSession,
                onStop = onStopSession,
                onPomodoroStop = onPomodoroStop,
                onPomodoroSkip = onPomodoroSkip,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Theme toggle
            val themeIcon = when (themeMode) {
                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                ThemeMode.DARK -> Icons.Outlined.DarkMode
                ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = themeIcon,
                    contentDescription = I18n.t("theme.toggle"),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Sidebar(
    currentScreen: Screen,
    isExpanded: Boolean,
    onNavigate: (Screen) -> Unit,
    onToggleExpand: () -> Unit,
) {
    val sidebarWidth = if (isExpanded) 200.dp else 64.dp

    Surface(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
        ) {
            val navItems = listOf(
                NavItem(Screen.Today, Icons.Filled.Today, Icons.Outlined.Today),
                NavItem(Screen.Backlog, Icons.Filled.Inbox, Icons.Outlined.Inbox),
                NavItem(Screen.Timeline, Icons.Filled.Timeline, Icons.Outlined.Timeline),
                NavItem(Screen.Calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                NavItem(Screen.Reports, Icons.Filled.Assessment, Icons.Outlined.Assessment),
                NavItem(Screen.Templates, Icons.Filled.ContentCopy, Icons.Outlined.ContentCopy),
            )

            navItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    isSelected = currentScreen == item.screen,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(item.screen) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

            // Settings
            SidebarNavItem(
                item = NavItem(Screen.Settings, Icons.Filled.Settings, Icons.Outlined.Settings),
                isSelected = currentScreen is Screen.Settings,
                isExpanded = isExpanded,
                onClick = { onNavigate(Screen.Settings) },
            )

            // Collapse toggle
            SidebarNavItem(
                item = NavItem(
                    screen = Screen.Today, // dummy, not used for navigation
                    selectedIcon = if (isExpanded) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
                    unselectedIcon = if (isExpanded) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                    label = if (isExpanded) I18n.t("sidebar.collapse") else I18n.t("sidebar.expand"),
                ),
                isSelected = false,
                isExpanded = isExpanded,
                onClick = onToggleExpand,
            )
        }
    }
}

private data class NavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String = screen.title,
)

@Composable
private fun SidebarNavItem(
    item: NavItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            if (isExpanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun ContentArea(
    currentScreen: Screen,
    todayViewModel: TodayViewModel,
    backlogViewModel: BacklogViewModel,
    reportsViewModel: ReportsViewModel,
    timelineViewModel: TimelineViewModel,
    templatesViewModel: TemplatesViewModel,
    settingsViewModel: SettingsViewModel,
    calendarViewModel: CalendarViewModel,
) {
    when (currentScreen) {
        is Screen.Today -> TodayScreen(viewModel = todayViewModel)
        is Screen.Backlog -> BacklogScreen(viewModel = backlogViewModel)
        is Screen.Reports -> ReportsScreen(viewModel = reportsViewModel)
        is Screen.Timeline -> TimelineScreen(viewModel = timelineViewModel)
        is Screen.Templates -> TemplatesScreen(viewModel = templatesViewModel)
        is Screen.Settings -> SettingsScreen(viewModel = settingsViewModel)
        is Screen.Calendar -> CalendarScreen(viewModel = calendarViewModel)
        is Screen.ThemeTest -> ThemeTestScreen()
        else -> {
            // Placeholder content for other screens - will be replaced in later phases
            Column {
                Text(
                    text = currentScreen.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = I18n.t("screen.placeholder"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    activeSession: ActiveSessionState?,
    totalTimeToday: Duration,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (activeSession != null) {
                val statusText = if (activeSession.isPaused) {
                    "${I18n.t("timer.paused")} — ${activeSession.task.title}"
                } else {
                    "${I18n.t("status.active_since")} — ${activeSession.task.title}"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = I18n.t("status.no_active_session"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${I18n.t("status.total_today")}: ${formatDuration(totalTimeToday)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Handle global keyboard shortcuts.
 * Returns true if the event was consumed.
 */
private fun handleGlobalKeyEvent(
    keyEvent: KeyEvent,
    navigationState: NavigationState,
    commandPaletteViewModel: CommandPaletteViewModel,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    if (!keyEvent.isCtrlPressed) return false

    return when (keyEvent.key) {
        Key.One -> { navigationState.navigateTo(Screen.Today); true }
        Key.Two -> { navigationState.navigateTo(Screen.Backlog); true }
        Key.Three -> { navigationState.navigateTo(Screen.Timeline); true }
        Key.Four -> { navigationState.navigateTo(Screen.Calendar); true }
        Key.Five -> { navigationState.navigateTo(Screen.Reports); true }
        Key.Comma -> { navigationState.navigateTo(Screen.Settings); true }
        Key.T -> { navigationState.navigateTo(Screen.ThemeTest); true }
        Key.K -> { commandPaletteViewModel.open(PaletteMode.COMMAND); true }
        Key.N -> { commandPaletteViewModel.open(PaletteMode.CREATE); true }
        else -> false
    }
}
