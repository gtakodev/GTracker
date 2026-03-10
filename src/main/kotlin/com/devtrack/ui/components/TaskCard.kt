package com.devtrack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devtrack.domain.model.TaskCategory
import com.devtrack.domain.model.TaskLevel
import com.devtrack.domain.model.Task
import com.devtrack.domain.model.TaskStatus
import com.devtrack.domain.model.TaskWithTime
import com.devtrack.ui.i18n.I18n
import com.devtrack.ui.theme.CategoryColors
import com.devtrack.ui.theme.TimerColors
import java.time.Duration

/**
 * Get the color associated with a task category.
 */
fun categoryColor(category: TaskCategory): Color = when (category) {
    TaskCategory.DEVELOPMENT -> CategoryColors.Development
    TaskCategory.BUGFIX -> CategoryColors.Bugfix
    TaskCategory.MEETING -> CategoryColors.Meeting
    TaskCategory.REVIEW -> CategoryColors.Review
    TaskCategory.DOCUMENTATION -> CategoryColors.Documentation
    TaskCategory.LEARNING -> CategoryColors.Learning
    TaskCategory.MAINTENANCE -> CategoryColors.Maintenance
    TaskCategory.SUPPORT -> CategoryColors.Support
}

/**
 * Format a Duration as "Xh XXm" or "XXm" for display.
 */
fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h %02dm".format(minutes) else "${minutes}m"
}

/**
 * Format a Duration as "HH:MM:SS" for the timer display.
 */
fun formatTimerDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/**
 * Task card composable for the Today view (P1.4.2).
 * Shows category color bar, title, Jira tickets, time, and play/pause button.
 * Active task has a pulsating green border.
 */
@Composable
fun TaskCard(
    taskWithTime: TaskWithTime,
    isActive: Boolean = false,
    isPaused: Boolean = false,
    activeDuration: Duration? = null,
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    onMarkDone: () -> Unit = {},
    onToggleSubTaskDone: ((Task) -> Unit)? = null,
    onDeleteSubTask: ((Task) -> Unit)? = null,
    subTasksExpanded: Boolean = false,
    onToggleSubTasksExpanded: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val task = taskWithTime.task
    val isDone = task.status == TaskStatus.DONE
    val canManageSubTasks = taskWithTime.subTasks.isNotEmpty() && onToggleSubTaskDone != null && onDeleteSubTask != null

    // Pulsating border for active task
    val infiniteTransition = rememberInfiniteTransition()
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val borderColor = if (isActive && !isPaused) {
        TimerColors.ActiveLight.copy(alpha = borderAlpha)
    } else if (isActive && isPaused) {
        TimerColors.PausedLight.copy(alpha = 0.7f)
    } else {
        Color.Transparent
    }

    val cardAlpha = if (isDone) 0.6f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isActive) 4.dp else 1.dp,
        tonalElevation = if (isActive) 2.dp else 0.dp,
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        } else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 64.dp)
                    .background(categoryColor(task.category))
            )

            // Main content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = I18n.t("status.done"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canManageSubTasks) {
                        IconButton(
                            onClick = { onToggleSubTasksExpanded?.invoke() },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (subTasksExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (subTasksExpanded) I18n.t("sidebar.collapse") else I18n.t("sidebar.expand"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Jira tickets
                if (task.jiraTickets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.jiraTickets.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Sub-task progress indicator (P2.5.2)
                if (taskWithTime.subTaskCount > 0) {
                    val targetProgress = taskWithTime.progress?.toFloat() ?: 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                        label = "subtaskProgress",
                    )
                    val isComplete = taskWithTime.progress == 1.0
                    val progressColor by animateColorAsState(
                        targetValue = if (isComplete) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                        animationSpec = tween(durationMillis = 350),
                        label = "subtaskProgressColor",
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${taskWithTime.completedSubTaskCount}/${taskWithTime.subTaskCount} ${I18n.t("subtask.completed")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                if (canManageSubTasks) {
                    AnimatedVisibility(visible = subTasksExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            taskWithTime.subTasks.forEach { subTask ->
                                TaskCardSubTaskItem(
                                    subTask = subTask,
                                    onToggleDone = { onToggleSubTaskDone?.invoke(subTask) },
                                    onDelete = { onDeleteSubTask?.invoke(subTask) },
                                )
                            }
                        }
                    }
                }

                // Category + Level Badge + Time
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = categoryColor(task.category).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = I18n.t("category.${task.category.name.lowercase()}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor(task.category),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Level badge (P2.2.2)
                    if (taskWithTime.level != TaskLevel.PLANNED) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val (levelColor, levelLabel) = when (taskWithTime.level) {
                            TaskLevel.ACTIVE -> TimerColors.ActiveLight to I18n.t("task.level.active")
                            TaskLevel.BACKLOG -> MaterialTheme.colorScheme.outline to I18n.t("task.level.backlog")
                            TaskLevel.PLANNED -> MaterialTheme.colorScheme.primary to I18n.t("task.level.planned")
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = levelColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = levelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = levelColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(activeDuration ?: taskWithTime.totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) TimerColors.ActiveLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Action buttons
            if (!isDone) {
                if (isActive) {
                    // Pause/Resume + Stop
                    if (isPaused) {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = I18n.t("timer.resume"),
                                tint = TimerColors.ActiveLight,
                            )
                        }
                    } else {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = I18n.t("timer.pause"),
                                tint = TimerColors.PausedLight,
                            )
                        }
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = I18n.t("timer.stop"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    // Play + Mark done
                    IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = I18n.t("timer.start"),
                            tint = TimerColors.ActiveLight,
                        )
                    }
                    IconButton(onClick = onMarkDone, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.CheckCircleOutline,
                            contentDescription = I18n.t("task.action.mark_done"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun TaskCardSubTaskItem(
    subTask: Task,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDone = subTask.status == TaskStatus.DONE

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = subTask.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = I18n.t("button.delete"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
