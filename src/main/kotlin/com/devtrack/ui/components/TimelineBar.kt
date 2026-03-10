package com.devtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import com.devtrack.domain.model.TaskCategory
import com.devtrack.infrastructure.export.DailyReportGenerator
import com.devtrack.ui.i18n.I18n
import com.devtrack.viewmodel.TimelineBlock

/**
 * Horizontal timeline bar component (P3.6.2).
 *
 * Displays a day's sessions as colored blocks on a horizontal axis.
 * - Colored blocks represent work sessions (colored by category).
 * - Light gray blocks represent gaps (inactivity).
 * - Tooltips show task name and duration on hover.
 *
 * @param blocks the timeline blocks (sessions + gaps)
 * @param dayStartHour start of the visible range (e.g. 8)
 * @param dayEndHour end of the visible range (e.g. 20)
 */
@Composable
fun TimelineBar(
    blocks: List<TimelineBlock>,
    dayStartHour: Int = 8,
    dayEndHour: Int = 20,
    modifier: Modifier = Modifier,
) {
    val totalHours = (dayEndHour - dayStartHour).toFloat()
    if (totalHours <= 0 || blocks.isEmpty()) {
        EmptyTimeline(dayStartHour, dayEndHour, modifier)
        return
    }

    Column(modifier = modifier) {
        // Hour labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            for (hour in dayStartHour..dayEndHour) {
                val weight = if (hour < dayEndHour) 1f else 0f
                if (weight > 0f) {
                    Text(
                        text = "${hour}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // The timeline bar itself
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                for (block in blocks) {
                    val blockWidth = ((block.endHour - block.startHour) / totalHours)
                    if (blockWidth > 0.001f) {
                        TimelineBlockView(
                            block = block,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(blockWidth.toFloat()),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineBlockView(
    block: TimelineBlock,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor = when {
        block.isGap -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        block.isPaused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> categoryColor(block.category)
    }

    Box(modifier = modifier) {
        TooltipArea(
            tooltip = {
                if (!block.isGap && block.taskTitle != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 4.dp,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = block.taskTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = DailyReportGenerator.formatDuration(block.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else if (block.isGap) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            text = I18n.t("timeline.gap"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            },
            delayMillis = 300,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .then(
                        if (!block.isGap) {
                            Modifier.border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Show task name inside block if wide enough
                if (!block.isGap && block.taskTitle != null && (block.endHour - block.startHour) > 0.75) {
                    Text(
                        text = block.taskTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline(
    dayStartHour: Int,
    dayEndHour: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Hour labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            for (hour in dayStartHour until dayEndHour) {
                Text(
                    text = "${hour}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Empty bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = I18n.t("timeline.no_sessions"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Map a TaskCategory to a display color for the timeline blocks.
 */
@Composable
fun categoryColor(category: TaskCategory?): Color {
    return when (category) {
        TaskCategory.DEVELOPMENT -> Color(0xFF4285F4)    // Blue
        TaskCategory.BUGFIX -> Color(0xFFEA4335)         // Red
        TaskCategory.MEETING -> Color(0xFFFBBC04)        // Yellow
        TaskCategory.REVIEW -> Color(0xFF34A853)          // Green
        TaskCategory.DOCUMENTATION -> Color(0xFF9C27B0)   // Purple
        TaskCategory.LEARNING -> Color(0xFFFF6D00)        // Orange
        TaskCategory.MAINTENANCE -> Color(0xFF607D8B)     // Blue-grey
        TaskCategory.SUPPORT -> Color(0xFF00BCD4)         // Cyan
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
}
