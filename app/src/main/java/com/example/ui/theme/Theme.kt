package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val AlMakhzanjiColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = TextPrimary,
    primaryContainer = AccentRedContainer,
    onPrimaryContainer = TextPrimary,
    secondary = LinkBlue,
    onSecondary = CanvasBackground,
    secondaryContainer = LinkBlueContainer,
    onSecondaryContainer = LinkBlue,
    tertiary = RegisterGold,
    onTertiary = CanvasBackground,
    tertiaryContainer = RegisterGoldContainer,
    onTertiaryContainer = RegisterGold,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = CanvasBackground,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = DividerBorder,
    outline = DividerBorder,
    outlineVariant = DividerBorder,
    error = AccentRed,
    onError = TextPrimary
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
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = AlMakhzanjiColorScheme,
            typography = AlMakhzanjiTypography,
            shapes = AlMakhzanjiShapes,
            content = content
        )
    }
}
