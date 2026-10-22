package io.github.z0orb.akagi.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val akagiColorScheme = darkColorScheme(
    primary = AkagiPalette.steelBlue,
    onPrimary = AkagiPalette.onSteelBlue,
    primaryContainer = AkagiPalette.steelBlueContainer,
    onPrimaryContainer = AkagiPalette.onSteelBlueContainer,
    inversePrimary = AkagiPalette.deepSteelBlue,
    secondary = AkagiPalette.slate,
    onSecondary = AkagiPalette.background,
    secondaryContainer = AkagiPalette.slateContainer,
    onSecondaryContainer = AkagiPalette.onSlateContainer,
    tertiary = AkagiPalette.slate,
    onTertiary = AkagiPalette.background,
    tertiaryContainer = AkagiPalette.slateContainer,
    onTertiaryContainer = AkagiPalette.onSlateContainer,
    background = AkagiPalette.background,
    onBackground = AkagiPalette.onSurface,
    surface = AkagiPalette.surface,
    onSurface = AkagiPalette.onSurface,
    surfaceVariant = AkagiPalette.surfaceContainerHigh,
    onSurfaceVariant = AkagiPalette.onSurfaceVariant,
    surfaceTint = AkagiPalette.steelBlue,
    inverseSurface = AkagiPalette.onSurface,
    inverseOnSurface = AkagiPalette.surface,
    error = AkagiPalette.error,
    onError = AkagiPalette.onError,
    errorContainer = AkagiPalette.errorContainer,
    onErrorContainer = AkagiPalette.onErrorContainer,
    outline = AkagiPalette.outline,
    outlineVariant = AkagiPalette.outlineVariant,
    scrim = Color.Black,
    surfaceBright = AkagiPalette.surfaceContainerHighest,
    surfaceDim = AkagiPalette.background,
    surfaceContainerLowest = AkagiPalette.abyss,
    surfaceContainerLow = AkagiPalette.surfaceLow,
    surfaceContainer = AkagiPalette.surfaceContainer,
    surfaceContainerHigh = AkagiPalette.surfaceContainerHigh,
    surfaceContainerHighest = AkagiPalette.surfaceContainerHighest,
)

/** Akagi is dark-only by design; there is no light scheme and no dynamic color. */
@Composable
fun AkagiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = akagiColorScheme,
        typography = akagiTypography,
        shapes = akagiShapes,
        content = content,
    )
}
