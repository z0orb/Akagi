package io.github.z0orb.akagi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Raw Akagi palette (PLAN §7.18). UI code reads colors through MaterialTheme, not from here. */
internal object AkagiPalette {
    val abyss = Color(0xFF080C12)
    val background = Color(0xFF0B1018)
    val surfaceLow = Color(0xFF0F1520)
    val surface = Color(0xFF111824)
    val surfaceContainer = Color(0xFF172030)
    val surfaceContainerHigh = Color(0xFF1E2939)
    val surfaceContainerHighest = Color(0xFF253144)
    val outline = Color(0xFF2C3A4F)
    val outlineVariant = Color(0xFF223047)

    val onSurface = Color(0xFFE3E8F0)
    val onSurfaceVariant = Color(0xFF9AA7B8)

    val steelBlue = Color(0xFF6FA3E8)
    val onSteelBlue = Color(0xFF08121F)
    val steelBlueContainer = Color(0xFF1C3A63)
    val onSteelBlueContainer = Color(0xFFCFE1FF)
    val deepSteelBlue = Color(0xFF3A78D6)

    val slate = Color(0xFF8E9BB0)
    val slateContainer = Color(0xFF243044)
    val onSlateContainer = Color(0xFFD6DEEA)

    val error = Color(0xFFFF8A80)
    val onError = Color(0xFF3B0A07)
    val errorContainer = Color(0xFF5C1A15)
    val onErrorContainer = Color(0xFFFFDAD5)

    val waveformUnplayed = Color(0xFF3A4A61)
}
