package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GolosGidColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = AmberOnPrimary,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = EmeraldSuccess,
    onTertiary = ObsidianBackground,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = ObsidianBackground,
    onBackground = OffWhiteText,
    surface = SlateSurface,
    onSurface = OffWhiteText,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = SoftSlateText,
    outline = SlateBorder
)

val GolosGidShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GolosGidTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GolosGidColorScheme,
        typography = AppTypography,
        shapes = GolosGidShapes,
        content = content
    )
}
