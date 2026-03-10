package com.devtrack.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.ActiveSessionState
import com.devtrack.domain.service.PomodoroPhase
import com.devtrack.domain.service.PomodoroState
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.theme.TimerColors

/**
 * Timer widget for the top bar (P1.4.3, P4.4.2).
 * Shows active task name, Jira ticket, HH:MM:SS timer, and pause/resume/stop buttons.
 * Green when active, yellow when paused.
 *
 * When Pomodoro mode is active, shows countdown, phase indicator, session counter,
 * and a progress bar with phase-specific colors.
 */
@Composable
fun TimerWidget(
    activeSession: ActiveSessionState?,
    pomodoroState: PomodoroState = PomodoroState(),
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    onPomodoroStop: () -> Unit = {},
    onPomodoroSkip: () -> Unit = {},
) {
    val isPomodoroActive = pomodoroState.isRunning && pomodoroState.phase != PomodoroPhase.IDLE

    if (isPomodoroActive) {
        PomodoroTimerWidget(
            activeSession = activeSession,
            pomodoroState = pomodoroState,
            onStop = onPomodoroStop,
            onSkip = onPomodoroSkip,
        )
        return
    }

    if (activeSession == null) {
        // No active session
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = I18n.t("timer.inactive"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val isPaused = activeSession.isPaused

    // Pulsating animation for active state
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val timerColor = if (isPaused) TimerColors.PausedLight else TimerColors.ActiveLight
    val backgroundColor = if (isPaused) {
        TimerColors.PausedLight.copy(alpha = 0.12f)
    } else {
        TimerColors.ActiveLight.copy(alpha = 0.12f * pulseAlpha)
    }

    Surface(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(50),
                color = timerColor.copy(alpha = if (!isPaused) pulseAlpha else 0.7f),
            ) {}

            Spacer(modifier = Modifier.width(8.dp))

            // Task name (truncated)
            Column(
                modifier = Modifier.widthIn(max = 150.dp),
            ) {
                Text(
                    text = activeSession.task.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activeSession.task.jiraTickets.isNotEmpty()) {
                    Text(
                        text = activeSession.task.jiraTickets.first(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = timerColor,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Timer display
            Text(
                text = formatTimerDuration(activeSession.effectiveDuration),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = timerColor,
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Controls
            if (isPaused) {
                IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = I18n.t("timer.resume"),
                        tint = TimerColors.ActiveLight,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = I18n.t("timer.pause"),
                        tint = TimerColors.PausedLight,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = I18n.t("timer.stop"),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Pomodoro-specific timer widget (P4.4.2).
 * Shows countdown, phase indicator, session counter, and progress bar.
 */
@Composable
private fun PomodoroTimerWidget(
    activeSession: ActiveSessionState?,
    pomodoroState: PomodoroState,
    onStop: () -> Unit,
    onSkip: () -> Unit,
) {
    val phaseColor = when (pomodoroState.phase) {
        PomodoroPhase.WORK -> TimerColors.PomodoroWork
        PomodoroPhase.BREAK -> TimerColors.PomodoroBreak
        PomodoroPhase.LONG_BREAK -> TimerColors.PomodoroLongBreak
        PomodoroPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val phaseLabel = when (pomodoroState.phase) {
        PomodoroPhase.WORK -> I18n.t("pomodoro.phase.work")
        PomodoroPhase.BREAK -> I18n.t("pomodoro.phase.break")
        PomodoroPhase.LONG_BREAK -> I18n.t("pomodoro.phase.long_break")
        PomodoroPhase.IDLE -> ""
    }

    val progress = if (pomodoroState.totalSeconds > 0) {
        1f - (pomodoroState.remainingSeconds.toFloat() / pomodoroState.totalSeconds.toFloat())
    } else 0f

    // Pulsating animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Surface(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
        color = phaseColor.copy(alpha = 0.12f * pulseAlpha),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Phase indicator
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(50),
                    color = phaseColor.copy(alpha = pulseAlpha),
                ) {}

                Spacer(modifier = Modifier.width(6.dp))

                // Phase label
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = phaseColor,
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Task name
                if (activeSession != null) {
                    Text(
                        text = activeSession.task.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Countdown display
                Text(
                    text = formatCountdown(pomodoroState.remainingSeconds),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = phaseColor,
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Session counter
                Text(
                    text = I18n.t("pomodoro.session_counter", pomodoroState.currentSession, pomodoroState.sessionsBeforeLongBreak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Skip button
                IconButton(onClick = onSkip, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = I18n.t("pomodoro.skip"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Stop button
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = I18n.t("pomodoro.stop"),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = phaseColor,
                trackColor = phaseColor.copy(alpha = 0.2f),
            )
        }
    }
}

/**
 * Format seconds into MM:SS countdown string.
 */
private fun formatCountdown(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
