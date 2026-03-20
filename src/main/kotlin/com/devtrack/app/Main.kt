package com.devtrack.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.devtrack.data.database.DatabaseFactory
import com.devtrack.data.database.MigrationManager
import com.devtrack.domain.service.PomodoroEvent
import com.devtrack.domain.service.PomodoroService
import com.devtrack.domain.service.TemplateService
import com.devtrack.infrastructure.notification.NotificationService
import com.devtrack.infrastructure.systray.SystemTrayService
import com.devtrack.ui.DevTrackApp
import com.devtrack.viewmodel.TodayViewModel
import com.devtrack.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.devtrack.app.Main")

@OptIn(FlowPreview::class)
fun main() = application {
    logger.info("DevTrack starting up...")

    // Initialize Koin DI
    startKoin {
        modules(appModules())
    }

    // Initialize database
    val koin = getKoin()
    val databaseFactory = koin.get<DatabaseFactory>()
    databaseFactory.init()
    val migrationManager = koin.get<MigrationManager>()
    migrationManager.migrate()
    logger.info("Database initialized and migrated")

    // Seed default templates on first launch (P4.2.3)
    val templateService = koin.get<TemplateService>()
    runBlocking { templateService.seedDefaultTemplates() }

    // Load saved window preferences
    val windowStateManager = WindowStateManager()
    val prefs = windowStateManager.load()

    val windowPosition = if (prefs.x >= 0 && prefs.y >= 0) {
        WindowPosition(x = prefs.x.dp, y = prefs.y.dp)
    } else {
        WindowPosition(Alignment.Center)
    }

    val windowState = rememberWindowState(
        size = DpSize(prefs.width.dp, prefs.height.dp),
        position = windowPosition,
    )

    // System tray integration (P4.3)
    val systemTrayService = koin.get<SystemTrayService>()
    val todayViewModel = koin.get<TodayViewModel>()
    val settingsViewModel = koin.get<SettingsViewModel>()

    // Track whether the app should truly exit (Quit from tray) vs just hide (X button)
    var shouldReallyQuit = false

    // Initialize system tray with callbacks
    if (systemTrayService.isSupported) {
        systemTrayService.init(
            onOpen = {
                // Show the window (restore from tray)
                windowState.isMinimized = false
                // The window will become visible via the isVisible flag below
            },
            onPause = {
                todayViewModel.pauseSession()
            },
            onResume = {
                todayViewModel.resumeSession()
            },
            onStop = {
                todayViewModel.stopSession()
            },
            onQuit = {
                shouldReallyQuit = true
                // Trigger window close which will perform graceful shutdown
                // We can't call exitApplication() directly from AWT thread,
                // so we set the flag and the close handler checks it
            },
        )

        // Start real-time tray updates with a background scope
        val trayScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        systemTrayService.startUpdates(todayViewModel.activeSession, trayScope)
    }

    // Notification service — hook into Pomodoro events (P4.8.2)
    val notificationService = koin.get<NotificationService>()
    val pomodoroService = koin.get<PomodoroService>()
    val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    notificationScope.launch {
        pomodoroService.events.collect { event ->
            when (event) {
                is PomodoroEvent.WorkComplete ->
                    notificationService.notifyPomodoroWorkComplete(event.sessionNumber, event.totalSessions)
                is PomodoroEvent.BreakComplete ->
                    notificationService.notifyPomodoroBreakComplete(event.sessionNumber, event.totalSessions)
                is PomodoroEvent.LongBreakComplete ->
                    notificationService.notifyPomodoroLongBreakComplete()
                is PomodoroEvent.PomodoroStopped -> { /* No notification on manual stop */ }
            }
        }
    }

    // Observe window state changes and save periodically (debounced to avoid excessive writes)
    LaunchedEffect(windowState) {
        combine(
            snapshotFlow { windowState.size },
            snapshotFlow { windowState.position },
        ) { size, position ->
            Triple(size, position, Unit)
        }
            .debounce(500L)
            .collectLatest { (size, position, _) ->
                val x = (position as? WindowPosition.Absolute)?.x?.value?.toInt() ?: -1
                val y = (position as? WindowPosition.Absolute)?.y?.value?.toInt() ?: -1
                windowStateManager.save(
                    width = size.width.value.toInt(),
                    height = size.height.value.toInt(),
                    x = x,
                    y = y,
                )
            }
    }

    // Graceful shutdown procedure
    fun performShutdown() {
        try {
            runBlocking {
                todayViewModel.gracefulShutdown()
            }
            todayViewModel.dispose()
        } catch (e: Exception) {
            logger.error("Error during graceful shutdown", e)
        }

        // Save final window state on close
        val size = windowState.size
        val position = windowState.position
        val x = (position as? WindowPosition.Absolute)?.x?.value?.toInt() ?: -1
        val y = (position as? WindowPosition.Absolute)?.y?.value?.toInt() ?: -1
        windowStateManager.save(
            width = size.width.value.toInt(),
            height = size.height.value.toInt(),
            x = x,
            y = y,
        )

        // Clean up system tray
        systemTrayService.dispose()

        // Close database
        databaseFactory.close()

        logger.info("DevTrack shutting down...")
    }

    val appIcon = painterResource("icons/devtrack.png")

    Window(
        onCloseRequest = {
            val closeToTray = settingsViewModel.uiState.value.closeToTray
            if (!closeToTray || shouldReallyQuit || !systemTrayService.isSupported) {
                // Real quit: perform full shutdown and exit
                performShutdown()
                exitApplication()
            } else {
                // Close window = minimize to tray (user opted in via settings)
                windowState.isMinimized = true
                logger.info("Window minimized to tray")
            }
        },
        title = "DevTrack",
        state = windowState,
        icon = appIcon,
    ) {
        window.minimumSize = java.awt.Dimension(900, 600)
        DevTrackApp()
    }
}
