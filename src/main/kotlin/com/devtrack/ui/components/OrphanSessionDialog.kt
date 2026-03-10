package com.devtrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devtrack.domain.model.OrphanSessionInfo
import com.devtrack.ui.i18n.I18n
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Action to resolve an orphan session (P2.4.2).
 */
enum class OrphanResolution {
    CLOSE_AT_LAST_ACTIVITY,
    CLOSE_NOW,
    EDIT_MANUALLY,
}

/**
 * Dialog shown at startup when orphan sessions are detected (P2.4.2).
 *
 * Displays each orphan session with task info, date, last activity timestamp,
 * and effective duration. For each session the user can choose to close it
 * at the last activity time, close it now, or edit it manually.
 *
 * Sessions are resolved one at a time — after resolving one, the next is shown.
 */
@Composable
fun OrphanSessionDialog(
    orphanSessions: List<OrphanSessionInfo>,
    onResolve: (sessionId: java.util.UUID, resolution: OrphanResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    if (orphanSessions.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 24.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = I18n.t("orphan.dialog.title"),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = I18n.t("orphan.dialog.description"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Session list
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    orphanSessions.forEach { orphan ->
                        OrphanSessionCard(
                            orphan = orphan,
                            onResolve = { resolution -> onResolve(orphan.session.id, resolution) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(I18n.t("button.close"))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrphanSessionCard(
    orphan: OrphanSessionInfo,
    onResolve: (OrphanResolution) -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Task name
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Task,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = orphan.task.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Session info
            val dateText = orphan.session.date.format(dateFormatter)
            val lastActivityText = orphan.lastEventTimestamp
                .atZone(zone)
                .format(timeFormatter)
            val durationText = formatDuration(orphan.effectiveDuration)

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                InfoRow(label = I18n.t("orphan.session.date").format(dateText))
                InfoRow(label = I18n.t("orphan.session.last_activity").format(lastActivityText))
                InfoRow(label = I18n.t("orphan.session.duration").format(durationText))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onResolve(OrphanResolution.CLOSE_AT_LAST_ACTIVITY) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = I18n.t("orphan.action.close_at_last_activity"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = { onResolve(OrphanResolution.CLOSE_NOW) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = I18n.t("orphan.action.close_now"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = { onResolve(OrphanResolution.EDIT_MANUALLY) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = I18n.t("orphan.action.edit_manually"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Dialog shown when inactivity is detected while a session is active (P2.4.4).
 *
 * Offers three options: continue working, auto-pause (retroactive), or stop the session.
 */
@Composable
fun InactivityDialog(
    taskTitle: String,
    inactiveMinutes: Long,
    onContinue: () -> Unit,
    onAutoPause: () -> Unit,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinue,
        icon = {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = I18n.t("inactivity.dialog.title"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                text = I18n.t("inactivity.dialog.message").format(inactiveMinutes, taskTitle),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text(I18n.t("inactivity.action.continue"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStop) {
                    Text(
                        text = I18n.t("inactivity.action.stop"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(onClick = onAutoPause) {
                    Text(I18n.t("inactivity.action.auto_pause"))
                }
            }
        },
    )
}
