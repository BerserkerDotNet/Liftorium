package dev.liftorium.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Canonical theme wrapper for every Liftorium UI surface. Consumers
 * (`MainActivity`, Paparazzi snapshots, future screens) must wrap their
 * content in [LiftoriumTheme] INSTEAD of `MaterialTheme {}` so the brand
 * palette, typography, spacing scale, and dimension tokens are all
 * available through [LiftoriumTokens] and through `MaterialTheme.*`.
 *
 * Dynamic color is intentionally not supported — see
 * `LiftoriumColorScheme.kt` for the rationale.
 */
@Composable
public fun LiftoriumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) LiftoriumDarkColors else LiftoriumLightColors
    CompositionLocalProvider(
        LocalLiftoriumSpacing provides DefaultLiftoriumSpacing,
        LocalLiftoriumDimens provides DefaultLiftoriumDimens,
        LocalLiftoriumTypography provides DefaultLiftoriumTypography,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = LiftoriumMaterialTypography,
            shapes = LiftoriumShapes,
            content = content,
        )
    }
}
