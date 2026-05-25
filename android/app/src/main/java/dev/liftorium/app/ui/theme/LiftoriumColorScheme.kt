package dev.liftorium.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Liftorium brand palette — a neutral steel/iron family with a single
 * steel-blue accent. We deliberately do NOT use Material 3 dynamic color
 * because dynamic theming defeats brand consistency across devices and
 * makes Paparazzi goldens non-deterministic across wallpapers.
 */
private val AccentSteelBlue = Color(0xFF3F6E8F)
private val AccentSteelBlueLight = Color(0xFFBCD7E9)
private val AccentSteelBlueDark = Color(0xFF11354E)
private val AccentSteelBlueContainerLight = Color(0xFFD3E6F2)
private val AccentSteelBlueContainerDark = Color(0xFF1F4A66)

private val WarningAmber = Color(0xFF8A5A00)
private val WarningAmberContainerLight = Color(0xFFFFDEA8)
private val WarningAmberOnContainer = Color(0xFF2B1700)
private val WarningAmberDark = Color(0xFFE7B872)
private val WarningAmberContainerDark = Color(0xFF4A3000)

private val DangerRed = Color(0xFFB3261E)
private val DangerRedContainerLight = Color(0xFFF9DEDC)
private val DangerRedDark = Color(0xFFF2B8B5)
private val DangerRedContainerDark = Color(0xFF601410)

private val SteelGrey99 = Color(0xFFFAFBFC)
private val SteelGrey95 = Color(0xFFEFF2F5)
private val SteelGrey90 = Color(0xFFE1E5E9)
private val SteelGrey70 = Color(0xFFA3AAB2)
private val SteelGrey50 = Color(0xFF6B737B)
private val SteelGrey30 = Color(0xFF3F4549)
private val SteelGrey20 = Color(0xFF2A2E32)
private val SteelGrey10 = Color(0xFF181B1E)
private val SteelGrey05 = Color(0xFF0F1113)

internal val LiftoriumLightColors: ColorScheme = lightColorScheme(
    primary = AccentSteelBlue,
    onPrimary = Color.White,
    primaryContainer = AccentSteelBlueContainerLight,
    onPrimaryContainer = AccentSteelBlueDark,
    secondary = SteelGrey30,
    onSecondary = Color.White,
    secondaryContainer = SteelGrey90,
    onSecondaryContainer = SteelGrey10,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberContainerLight,
    onTertiaryContainer = WarningAmberOnContainer,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedContainerLight,
    onErrorContainer = Color(0xFF410E0B),
    background = SteelGrey99,
    onBackground = SteelGrey10,
    surface = SteelGrey99,
    onSurface = SteelGrey10,
    surfaceVariant = SteelGrey95,
    onSurfaceVariant = SteelGrey30,
    outline = SteelGrey50,
    outlineVariant = SteelGrey90,
)

internal val LiftoriumDarkColors: ColorScheme = darkColorScheme(
    primary = AccentSteelBlueLight,
    onPrimary = AccentSteelBlueDark,
    primaryContainer = AccentSteelBlueContainerDark,
    onPrimaryContainer = AccentSteelBlueLight,
    secondary = SteelGrey70,
    onSecondary = SteelGrey10,
    secondaryContainer = SteelGrey30,
    onSecondaryContainer = SteelGrey90,
    tertiary = WarningAmberDark,
    onTertiary = Color(0xFF2B1700),
    tertiaryContainer = WarningAmberContainerDark,
    onTertiaryContainer = WarningAmberContainerLight,
    error = DangerRedDark,
    onError = Color(0xFF601410),
    errorContainer = DangerRedContainerDark,
    onErrorContainer = DangerRedContainerLight,
    background = SteelGrey05,
    onBackground = SteelGrey95,
    surface = SteelGrey10,
    onSurface = SteelGrey95,
    surfaceVariant = SteelGrey20,
    onSurfaceVariant = SteelGrey70,
    outline = SteelGrey50,
    outlineVariant = SteelGrey30,
)
