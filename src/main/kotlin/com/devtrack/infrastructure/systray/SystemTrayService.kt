package com.devtrack.infrastructure.systray

import com.devtrack.domain.model.ActiveSessionState
import com.devtrack.ui.i18n.I18n
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.time.Duration
import java.time.Instant

/**
 * System tray integration for DevTrack (P4.3).
 *
 * Provides:
 * - Tray icon with color-coded states (green=active, yellow=paused, gray=inactive)
 * - Context menu (Open, Pause/Resume, Stop, Quit)
 * - Real-time tooltip updates with timer information
 * - Double-click to show window
 *
 * Uses [java.awt.SystemTray] for cross-platform support (Windows + Linux).
 */
class SystemTrayService {
    private val logger = LoggerFactory.getLogger(SystemTrayService::class.java)

    private var trayIcon: TrayIcon? = null
    private var systemTray: SystemTray? = null
    private var updateJob: Job? = null

    // Menu items that need dynamic text updates
    private var pauseResumeItem: MenuItem? = null
    private var stopItem: MenuItem? = null
    private var openItem: MenuItem? = null
    private var quitItem: MenuItem? = null

    // Callbacks set by the application
    private var onOpenWindow: (() -> Unit)? = null
    private var onPauseSession: (() -> Unit)? = null
    private var onResumeSession: (() -> Unit)? = null
    private var onStopSession: (() -> Unit)? = null
    private var onQuitApplication: (() -> Unit)? = null

    /** Whether the system tray is supported on this platform. */
    val isSupported: Boolean get() = SystemTray.isSupported()

    /**
     * Initialize the system tray icon and context menu.
     *
     * @param onOpen Callback when "Open DevTrack" is clicked or icon is double-clicked
     * @param onPause Callback when "Pause" is clicked
     * @param onResume Callback when "Resume" is clicked
     * @param onStop Callback when "Stop session" is clicked
     * @param onQuit Callback when "Quit" is clicked (real application exit)
     */
    fun init(
        onOpen: () -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
        onQuit: () -> Unit,
    ) {
        if (!isSupported) {
            logger.warn("System tray is not supported on this platform")
            return
        }

        onOpenWindow = onOpen
        onPauseSession = onPause
        onResumeSession = onResume
        onStopSession = onStop
        onQuitApplication = onQuit

        try {
            systemTray = SystemTray.getSystemTray()

            // Build context menu
            val popup = buildContextMenu()

            // Create tray icon with inactive state
            val icon = createTrayIcon(TrayIconState.INACTIVE)
            trayIcon = TrayIcon(icon, "DevTrack", popup).apply {
                isImageAutoSize = true

                // Double-click to open window
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.clickCount == 2) {
                            onOpenWindow?.invoke()
                        }
                    }
                })
            }

            systemTray?.add(trayIcon)
            logger.info("System tray initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize system tray", e)
        }
    }

    /**
     * Start real-time updates of the tray icon and tooltip (P4.3.4).
     *
     * @param activeSessionState StateFlow of the current active session (null = no session)
     * @param scope CoroutineScope to launch the update coroutine in
     */
    fun startUpdates(
        activeSessionState: StateFlow<ActiveSessionState?>,
        scope: CoroutineScope,
    ) {
        updateJob?.cancel()
        updateJob = scope.launch {
            // Collect session state changes and update tray
            var lastState: TrayIconState? = null

            while (isActive) {
                try {
                    val session = activeSessionState.value
                    val newState = when {
                        session == null -> TrayIconState.INACTIVE
                        session.isPaused -> TrayIconState.PAUSED
                        else -> TrayIconState.ACTIVE
                    }

                    // Update icon if state changed
                    if (newState != lastState) {
                        updateIcon(newState)
                        updateMenuState(session)
                        lastState = newState
                    }

                    // Update tooltip with timer info
                    updateTooltip(session)
                } catch (e: Exception) {
                    logger.error("Error updating tray icon", e)
                }

                delay(1000) // Update every second
            }
        }
    }

    /**
     * Update the tray icon to reflect a new state.
     */
    private fun updateIcon(state: TrayIconState) {
        trayIcon?.image = createTrayIcon(state)
    }

    /**
     * Update the tooltip text with current timer information.
     */
    private fun updateTooltip(session: ActiveSessionState?) {
        val tooltip = if (session != null) {
            val timerStr = formatDuration(session.effectiveDuration)
            val tickets = session.task.jiraTickets
            val ticketStr = if (tickets.isNotEmpty()) "${tickets.first()} - " else ""
            val statusStr = if (session.isPaused) {
                "[${I18n.t("timer.paused")}]"
            } else {
                "[${I18n.t("timer.active")}]"
            }
            "$statusStr $ticketStr${session.task.title} - $timerStr"
        } else {
            "DevTrack - ${I18n.t("timer.inactive")}"
        }

        // TrayIcon tooltip is limited to 127 characters on some platforms
        trayIcon?.toolTip = tooltip.take(127)
    }

    /**
     * Update the context menu items based on session state.
     */
    private fun updateMenuState(session: ActiveSessionState?) {
        if (session == null) {
            pauseResumeItem?.label = I18n.t("tray.pause")
            pauseResumeItem?.isEnabled = false
            stopItem?.isEnabled = false
        } else if (session.isPaused) {
            pauseResumeItem?.label = I18n.t("tray.resume")
            pauseResumeItem?.isEnabled = true
            stopItem?.isEnabled = true
        } else {
            pauseResumeItem?.label = I18n.t("tray.pause")
            pauseResumeItem?.isEnabled = true
            stopItem?.isEnabled = true
        }
    }

    /**
     * Build the AWT popup menu for the tray icon.
     */
    private fun buildContextMenu(): PopupMenu {
        val popup = PopupMenu()

        // Open DevTrack
        openItem = MenuItem(I18n.t("tray.open")).apply {
            addActionListener { onOpenWindow?.invoke() }
        }
        popup.add(openItem)

        popup.addSeparator()

        // Pause / Resume (dynamic label)
        pauseResumeItem = MenuItem(I18n.t("tray.pause")).apply {
            isEnabled = false
            addActionListener {
                // Determine current action based on label text
                if (label == I18n.t("tray.resume")) {
                    onResumeSession?.invoke()
                } else {
                    onPauseSession?.invoke()
                }
            }
        }
        popup.add(pauseResumeItem)

        // Stop session
        stopItem = MenuItem(I18n.t("tray.stop")).apply {
            isEnabled = false
            addActionListener { onStopSession?.invoke() }
        }
        popup.add(stopItem)

        popup.addSeparator()

        // Quit
        quitItem = MenuItem(I18n.t("tray.quit")).apply {
            addActionListener { onQuitApplication?.invoke() }
        }
        popup.add(quitItem)

        return popup
    }

    /**
     * Remove the tray icon and clean up resources.
     */
    fun dispose() {
        updateJob?.cancel()
        updateJob = null

        trayIcon?.let { icon ->
            systemTray?.remove(icon)
        }
        trayIcon = null
        systemTray = null
        logger.info("System tray disposed")
    }

    /**
     * Show a notification balloon from the tray icon.
     *
     * @param title Notification title
     * @param message Notification body text
     * @param type Message type (INFO, WARNING, ERROR, NONE)
     */
    fun showNotification(
        title: String,
        message: String,
        type: TrayIcon.MessageType = TrayIcon.MessageType.INFO,
    ) {
        trayIcon?.displayMessage(title, message, type)
    }

    companion object {
        /**
         * Create a 16x16 tray icon image with the given color state.
         * Uses a filled circle with a border for visibility.
         */
        fun createTrayIcon(state: TrayIconState): Image {
            val size = 16
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g2d = image.createGraphics()

            // Enable anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Fill color based on state
            val fillColor = when (state) {
                TrayIconState.ACTIVE -> Color(76, 175, 80) // Green
                TrayIconState.PAUSED -> Color(255, 193, 7) // Yellow/Amber
                TrayIconState.INACTIVE -> Color(158, 158, 158) // Gray
            }

            // Dark border for contrast
            val borderColor = when (state) {
                TrayIconState.ACTIVE -> Color(46, 125, 50)
                TrayIconState.PAUSED -> Color(255, 160, 0)
                TrayIconState.INACTIVE -> Color(117, 117, 117)
            }

            // Draw filled circle
            g2d.color = fillColor
            g2d.fillOval(1, 1, size - 2, size - 2)

            // Draw border
            g2d.color = borderColor
            g2d.drawOval(1, 1, size - 3, size - 3)

            // Draw a small "D" letter in the center for branding
            g2d.color = Color.WHITE
            g2d.font = Font("SansSerif", Font.BOLD, 10)
            val fm = g2d.fontMetrics
            val textWidth = fm.stringWidth("D")
            val textHeight = fm.ascent
            g2d.drawString("D", (size - textWidth) / 2, (size + textHeight) / 2 - 1)

            g2d.dispose()
            return image
        }

        /**
         * Format a duration as HH:MM:SS for the tooltip.
         */
        private fun formatDuration(duration: Duration): String {
            val totalSeconds = duration.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }
}

/**
 * Visual states for the tray icon.
 */
enum class TrayIconState {
    /** Green — active timer running. */
    ACTIVE,
    /** Yellow/Amber — timer is paused. */
    PAUSED,
    /** Gray — no active session. */
    INACTIVE,
}
