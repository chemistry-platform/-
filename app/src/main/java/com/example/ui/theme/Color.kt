package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Palette (exact high-contrast near-black palette — no blue-gray surfaces)
val DarkCanvas = Color(0xFF0B0B0C)
val DarkSurface = Color(0xFF151517)
val DarkSurfaceElevated = Color(0xFF1C1C1F)
val DarkOutline = Color(0xFF262629)
val DarkTextPrimary = Color(0xFFF5F5F7)
val DarkTextMuted = Color(0xFF9E9EA7)

val DarkRedAccent = Color(0xFFE5484D)
val DarkRedContainer = Color(0xFF331416)
val DarkAmberSubline = Color(0xFFF2B94B)
val DarkAmberContainer = Color(0xFF30240E)
val DarkBlueLink = Color(0xFF6CB4FF)
val DarkBlueContainer = Color(0xFF162433)

// Light Palette (crisp editorial palette preserving identical semantic hue roles)
val LightCanvas = Color(0xFFF7F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEEEEF1)
val LightOutline = Color(0xFFDADCE2)
val LightTextPrimary = Color(0xFF16181D)
val LightTextMuted = Color(0xFF5A606D)

val LightRedAccent = Color(0xFFC93035)
val LightRedContainer = Color(0xFFFDE8E9)
val LightAmberSubline = Color(0xFF9C6500)
val LightAmberContainer = Color(0xFFFFF3D6)
val LightBlueLink = Color(0xFF165EC9)
val LightBlueContainer = Color(0xFFE4EEFD)

@Immutable
data class SemanticColors(
    val definitionTranslation: Color,
    val wordSubline: Color,
    val exampleTranslation: Color,
    val divider: Color,
    val cardSurface: Color,
    val elevatedSurface: Color
)

val DarkSemanticColors = SemanticColors(
    definitionTranslation = DarkRedAccent,
    wordSubline = DarkAmberSubline,
    exampleTranslation = DarkBlueLink,
    divider = DarkOutline,
    cardSurface = DarkSurface,
    elevatedSurface = DarkSurfaceElevated
)

val LightSemanticColors = SemanticColors(
    definitionTranslation = LightRedAccent,
    wordSubline = LightAmberSubline,
    exampleTranslation = LightBlueLink,
    divider = LightOutline,
    cardSurface = LightSurface,
    elevatedSurface = LightSurfaceElevated
)

val LocalSemanticColors = staticCompositionLocalOf { DarkSemanticColors }
