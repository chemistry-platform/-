package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AlMakhzanjiDarkColorScheme = darkColorScheme(
    primary = DarkRedAccent,
    onPrimary = Color.White,
    primaryContainer = DarkRedContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkBlueLink,
    onSecondary = DarkCanvas,
    secondaryContainer = DarkBlueContainer,
    onSecondaryContainer = DarkBlueLink,
    tertiary = DarkAmberSubline,
    onTertiary = DarkCanvas,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkAmberSubline,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkOutline,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkRedAccent,
    onError = Color.White,
    errorContainer = DarkRedContainer,
    onErrorContainer = DarkTextPrimary
)

private val AlMakhzanjiLightColorScheme = lightColorScheme(
    primary = LightRedAccent,
    onPrimary = Color.White,
    primaryContainer = LightRedContainer,
    onPrimaryContainer = LightRedAccent,
    secondary = LightBlueLink,
    onSecondary = Color.White,
    secondaryContainer = LightBlueContainer,
    onSecondaryContainer = LightBlueLink,
    tertiary = LightAmberSubline,
    onTertiary = Color.White,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightAmberSubline,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightCanvas,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = LightOutline,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightRedAccent,
    onError = Color.White,
    errorContainer = LightRedContainer,
    onErrorContainer = LightRedAccent
)

val AlMakhzanjiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AlMakhzanjiTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) AlMakhzanjiDarkColorScheme else AlMakhzanjiLightColorScheme
    val semanticColors = if (useDark) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlMakhzanjiTypography,
            shapes = AlMakhzanjiShapes,
            content = content
        )
    }
}
