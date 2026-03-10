package com.devtrack.ui.theme

import androidx.compose.ui.graphics.Color

// Light theme colors (PRD 5.2)
object LightColors {
    val Primary = Color(0xFF2563EB)
    val OnPrimary = Color.White
    val PrimaryContainer = Color(0xFFD6E4FF)
    val OnPrimaryContainer = Color(0xFF001B3E)

    val Secondary = Color(0xFF545F70)
    val OnSecondary = Color.White
    val SecondaryContainer = Color(0xFFD8E3F8)
    val OnSecondaryContainer = Color(0xFF111C2B)

    val Tertiary = Color(0xFF6B5778)
    val OnTertiary = Color.White
    val TertiaryContainer = Color(0xFFF3DAFF)
    val OnTertiaryContainer = Color(0xFF251432)

    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF1A1C1E)
    val SurfaceVariant = Color(0xFFE1E2EC)
    val OnSurfaceVariant = Color(0xFF44474F)

    val Background = Color(0xFFF8FAFC)
    val OnBackground = Color(0xFF1A1C1E)

    val Error = Color(0xFFDC2626)
    val OnError = Color.White
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    val Outline = Color(0xFF74777F)
    val OutlineVariant = Color(0xFFC4C6D0)
    val SurfaceTint = Primary
}

// Dark theme colors (PRD 5.2)
object DarkColors {
    val Primary = Color(0xFF60A5FA)
    val OnPrimary = Color(0xFF003062)
    val PrimaryContainer = Color(0xFF00468A)
    val OnPrimaryContainer = Color(0xFFD6E4FF)

    val Secondary = Color(0xFFBCC7DB)
    val OnSecondary = Color(0xFF263141)
    val SecondaryContainer = Color(0xFF3C4758)
    val OnSecondaryContainer = Color(0xFFD8E3F8)

    val Tertiary = Color(0xFFD7BEE4)
    val OnTertiary = Color(0xFF3B2948)
    val TertiaryContainer = Color(0xFF533F60)
    val OnTertiaryContainer = Color(0xFFF3DAFF)

    val Surface = Color(0xFF1E1E2E)
    val OnSurface = Color(0xFFE3E2E6)
    val SurfaceVariant = Color(0xFF44474F)
    val OnSurfaceVariant = Color(0xFFC4C6D0)

    val Background = Color(0xFF11111B)
    val OnBackground = Color(0xFFE3E2E6)

    val Error = Color(0xFFF87171)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)

    val Outline = Color(0xFF8E9099)
    val OutlineVariant = Color(0xFF44474F)
    val SurfaceTint = Primary
}

// Timer colors
object TimerColors {
    val ActiveLight = Color(0xFF16A34A)
    val ActiveDark = Color(0xFF4ADE80)
    val PausedLight = Color(0xFFEAB308)
    val PausedDark = Color(0xFFFACC15)
    // Pomodoro phase colors (P4.4.2)
    val PomodoroWork = Color(0xFFDC2626)   // Red for work
    val PomodoroBreak = Color(0xFF2563EB)  // Blue for break
    val PomodoroLongBreak = Color(0xFF7C3AED)  // Purple for long break
}

// Category colors (PRD 3.4)
object CategoryColors {
    val Development = Color(0xFF3B82F6)
    val Bugfix = Color(0xFFEF4444)
    val Meeting = Color(0xFF8B5CF6)
    val Review = Color(0xFFF97316)
    val Documentation = Color(0xFF22C55E)
    val Learning = Color(0xFF06B6D4)
    val Maintenance = Color(0xFF6B7280)
    val Support = Color(0xFFEAB308)
}
