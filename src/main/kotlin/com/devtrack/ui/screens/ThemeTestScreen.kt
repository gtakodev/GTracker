package com.devtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devtrack.ui.theme.CategoryColors
import com.devtrack.ui.theme.MonospaceStyle
import com.devtrack.ui.theme.TimerColors

/**
 * Temporary test screen for visually verifying the Material 3 theme.
 * Displays base components, category colors, and timer colors.
 * This screen will be removed once the theme is validated.
 */
@Composable
fun ThemeTestScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Theme Test Screen",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Temporary screen for visual theme verification. Remove after validation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        // Section 1: Typography
        TypographySection()

        HorizontalDivider()

        // Section 2: Buttons
        ButtonsSection()

        HorizontalDivider()

        // Section 3: Cards and Surfaces
        CardsSection()

        HorizontalDivider()

        // Section 4: Text Fields
        TextFieldsSection()

        HorizontalDivider()

        // Section 5: Chips
        ChipsSection()

        HorizontalDivider()

        // Section 6: Category Colors
        CategoryColorsSection()

        HorizontalDivider()

        // Section 7: Timer Colors
        TimerColorsSection()

        HorizontalDivider()

        // Section 8: Color Scheme Overview
        ColorSchemeSection()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun TypographySection() {
    SectionTitle("Typography")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Display Large", style = MaterialTheme.typography.displayLarge)
        Text("Display Medium", style = MaterialTheme.typography.displayMedium)
        Text("Display Small", style = MaterialTheme.typography.displaySmall)
        Text("Headline Large", style = MaterialTheme.typography.headlineLarge)
        Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
        Text("Headline Small", style = MaterialTheme.typography.headlineSmall)
        Text("Title Large", style = MaterialTheme.typography.titleLarge)
        Text("Title Medium", style = MaterialTheme.typography.titleMedium)
        Text("Title Small", style = MaterialTheme.typography.titleSmall)
        Text("Body Large", style = MaterialTheme.typography.bodyLarge)
        Text("Body Medium", style = MaterialTheme.typography.bodyMedium)
        Text("Body Small", style = MaterialTheme.typography.bodySmall)
        Text("Label Large", style = MaterialTheme.typography.labelLarge)
        Text("Label Medium", style = MaterialTheme.typography.labelMedium)
        Text("Label Small", style = MaterialTheme.typography.labelSmall)
        Text("JIRA-1234 (Monospace)", style = MonospaceStyle)
    }
}

@Composable
private fun ButtonsSection() {
    SectionTitle("Buttons")

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(onClick = {}) { Text("Filled") }
        FilledTonalButton(onClick = {}) { Text("Tonal") }
        OutlinedButton(onClick = {}) { Text("Outlined") }
        TextButton(onClick = {}) { Text("Text") }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(onClick = {}, enabled = false) { Text("Disabled") }
        FloatingActionButton(onClick = {}) {
            Icon(Icons.Filled.Add, contentDescription = "FAB")
        }
        SmallFloatingActionButton(onClick = {}) {
            Icon(Icons.Filled.Add, contentDescription = "Small FAB")
        }
        ExtendedFloatingActionButton(
            onClick = {},
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            text = { Text("Start Timer") },
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
        FilledIconButton(onClick = {}) {
            Icon(Icons.Filled.Check, contentDescription = "Check")
        }
        OutlinedIconButton(onClick = {}) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
        }
    }
}

@Composable
private fun CardsSection() {
    SectionTitle("Cards & Surfaces")

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Filled Card", style = MaterialTheme.typography.titleMedium)
                Text("Card content goes here", style = MaterialTheme.typography.bodyMedium)
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Elevated Card", style = MaterialTheme.typography.titleMedium)
                Text("Card content goes here", style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Outlined Card", style = MaterialTheme.typography.titleMedium)
                Text("Card content goes here", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TextFieldsSection() {
    SectionTitle("Text Fields")

    var text1 by remember { mutableStateOf("") }
    var text2 by remember { mutableStateOf("") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = text1,
            onValueChange = { text1 = it },
            label = { Text("Task title") },
            placeholder = { Text("Enter task title...") },
            modifier = Modifier.weight(1f),
        )
        TextField(
            value = text2,
            onValueChange = { text2 = it },
            label = { Text("Notes") },
            placeholder = { Text("Add notes...") },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "Error state",
            onValueChange = {},
            isError = true,
            label = { Text("With error") },
            supportingText = { Text("This field is required") },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = "Disabled",
            onValueChange = {},
            enabled = false,
            label = { Text("Disabled") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChipsSection() {
    SectionTitle("Chips")

    var selectedFilter by remember { mutableStateOf(0) }
    val categories = listOf("Development", "Bugfix", "Meeting", "Review", "Documentation")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEachIndexed { index, category ->
            FilterChip(
                selected = selectedFilter == index,
                onClick = { selectedFilter = index },
                label = { Text(category) },
                leadingIcon = if (selectedFilter == index) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AssistChip(
            onClick = {},
            label = { Text("Assist Chip") },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        InputChip(
            selected = false,
            onClick = {},
            label = { Text("Input Chip") },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        SuggestionChip(
            onClick = {},
            label = { Text("Suggestion") },
        )
    }
}

@Composable
private fun CategoryColorsSection() {
    SectionTitle("Category Colors (PRD 3.4)")

    val categories = listOf(
        "Development" to CategoryColors.Development,
        "Bugfix" to CategoryColors.Bugfix,
        "Meeting" to CategoryColors.Meeting,
        "Review" to CategoryColors.Review,
        "Documentation" to CategoryColors.Documentation,
        "Learning" to CategoryColors.Learning,
        "Maintenance" to CategoryColors.Maintenance,
        "Support" to CategoryColors.Support,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEach { (name, color) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Show categories as chips with colored backgrounds
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEach { (name, color) ->
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerColorsSection() {
    SectionTitle("Timer Colors")

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Active timer (light)
        TimerColorSwatch("Active (Light)", TimerColors.ActiveLight)
        TimerColorSwatch("Active (Dark)", TimerColors.ActiveDark)
        TimerColorSwatch("Paused (Light)", TimerColors.PausedLight)
        TimerColorSwatch("Paused (Dark)", TimerColors.PausedDark)
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Simulated timer display
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Active timer simulation
        Surface(
            color = TimerColors.ActiveLight.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TimerColors.ActiveLight,
                )
                Text(
                    text = "01:23:45",
                    style = MaterialTheme.typography.titleLarge,
                    color = TimerColors.ActiveLight,
                )
            }
        }

        // Paused timer simulation
        Surface(
            color = TimerColors.PausedLight.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = null,
                    tint = TimerColors.PausedLight,
                )
                Text(
                    text = "00:45:12",
                    style = MaterialTheme.typography.titleLarge,
                    color = TimerColors.PausedLight,
                )
            }
        }
    }
}

@Composable
private fun TimerColorSwatch(label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSchemeSection() {
    SectionTitle("Color Scheme")

    val colors = listOf(
        "Primary" to MaterialTheme.colorScheme.primary,
        "On Primary" to MaterialTheme.colorScheme.onPrimary,
        "Primary Container" to MaterialTheme.colorScheme.primaryContainer,
        "Secondary" to MaterialTheme.colorScheme.secondary,
        "Tertiary" to MaterialTheme.colorScheme.tertiary,
        "Surface" to MaterialTheme.colorScheme.surface,
        "Surface Variant" to MaterialTheme.colorScheme.surfaceVariant,
        "Background" to MaterialTheme.colorScheme.background,
        "Error" to MaterialTheme.colorScheme.error,
        "Outline" to MaterialTheme.colorScheme.outline,
    )

    // Display as a grid of color swatches
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { (name, color) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
