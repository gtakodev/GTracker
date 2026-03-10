package com.devtrack.infrastructure.notification

import com.devtrack.infrastructure.systray.SystemTrayService
import com.devtrack.ui.i18n.I18n
import org.slf4j.LoggerFactory
import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Notification types for classifying notification importance.
 */
enum class NotificationType {
    INFO,
    WARNING,
    TIMER_END,
    INACTIVITY,
}

/**
 * Cross-platform notification service (P4.8.1).
 *
 * Strategy:
 * - If [SystemTrayService] is available and system tray is supported,
 *   uses [TrayIcon.displayMessage] for notifications (works on Windows and most Linux DEs).
 * - As a fallback on Linux, uses `notify-send` via ProcessBuilder.
 * - If neither is available, logs the notification.
 */
class NotificationService(
    private val systemTrayService: SystemTrayService,
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    private val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")

    /**
     * Send a desktop notification.
     *
     * @param title Notification title
     * @param message Notification body text
     * @param type Notification type (affects icon/urgency)
     */
    fun notify(title: String, message: String, type: NotificationType = NotificationType.INFO) {
        try {
            if (systemTrayService.isSupported) {
                // Use system tray balloon notification
                val trayType = when (type) {
                    NotificationType.INFO -> TrayIcon.MessageType.INFO
                    NotificationType.WARNING -> TrayIcon.MessageType.WARNING
                    NotificationType.TIMER_END -> TrayIcon.MessageType.INFO
                    NotificationType.INACTIVITY -> TrayIcon.MessageType.WARNING
                }
                systemTrayService.showNotification(title, message, trayType)
                logger.debug("Notification sent via system tray: [{}] {}", title, message)
            } else if (isLinux) {
                // Fallback: use notify-send on Linux
                sendNotifySend(title, message, type)
            } else {
                // No notification mechanism available
                logger.info("Notification (no handler): [{}] {}", title, message)
            }
        } catch (e: Exception) {
            logger.error("Failed to send notification: [{}] {}", title, message, e)
        }
    }

    // -- Convenience methods for common notification scenarios --

    /**
     * Notify that a work session has ended.
     */
    fun notifySessionEnd(taskTitle: String, duration: String) {
        notify(
            title = I18n.t("notification.session.end.title"),
            message = I18n.t("notification.session.end.message", taskTitle, duration),
            type = NotificationType.TIMER_END,
        )
    }

    /**
     * Notify that a Pomodoro work phase has completed (time for a break).
     */
    fun notifyPomodoroWorkComplete(sessionNumber: Int, totalSessions: Int) {
        notify(
            title = I18n.t("notification.pomodoro.work.title"),
            message = I18n.t("notification.pomodoro.work.message", sessionNumber, totalSessions),
            type = NotificationType.TIMER_END,
        )
    }

    /**
     * Notify that a Pomodoro break has completed (time to work).
     */
    fun notifyPomodoroBreakComplete(sessionNumber: Int, totalSessions: Int) {
        notify(
            title = I18n.t("notification.pomodoro.break.title"),
            message = I18n.t("notification.pomodoro.break.message", sessionNumber, totalSessions),
            type = NotificationType.INFO,
        )
    }

    /**
     * Notify that a Pomodoro long break has completed (new cycle).
     */
    fun notifyPomodoroLongBreakComplete() {
        notify(
            title = I18n.t("notification.pomodoro.longbreak.title"),
            message = I18n.t("notification.pomodoro.longbreak.message"),
            type = NotificationType.INFO,
        )
    }

    /**
     * Notify that the user has been inactive for a while.
     */
    fun notifyInactivity(minutes: Int) {
        notify(
            title = I18n.t("notification.inactivity.title"),
            message = I18n.t("notification.inactivity.message", minutes),
            type = NotificationType.INACTIVITY,
        )
    }

    /**
     * Linux fallback: use notify-send command.
     */
    private fun sendNotifySend(title: String, message: String, type: NotificationType) {
        try {
            val urgency = when (type) {
                NotificationType.WARNING, NotificationType.INACTIVITY -> "critical"
                NotificationType.TIMER_END -> "normal"
                NotificationType.INFO -> "low"
            }
            val process = ProcessBuilder(
                "notify-send",
                "--urgency=$urgency",
                "--app-name=DevTrack",
                title,
                message,
            ).start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.debug("Notification sent via notify-send: [{}] {}", title, message)
            } else {
                logger.warn("notify-send exited with code {}", exitCode)
            }
        } catch (e: Exception) {
            logger.warn("notify-send not available, notification dropped: [{}] {}", title, message)
        }
    }
}
