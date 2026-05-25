package dev.liftorium.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Liftorium typography: Material 3 baseline + an extra `numeric` slot
 * with `tnum` (tabular numerals) enabled. Use [LiftoriumTokens.lTypography]'s
 * `numeric` style for weights, set counts, and any column-aligned numeric
 * value so digits do not jitter between rows.
 */
internal val LiftoriumMaterialTypography: Typography = Typography()

@Immutable
public data class LiftoriumTypography(
    val numeric: TextStyle,
)

internal val DefaultLiftoriumTypography: LiftoriumTypography = LiftoriumTypography(
    numeric = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "\"tnum\", \"lnum\"",
    ),
)

internal val LocalLiftoriumTypography = compositionLocalOf { DefaultLiftoriumTypography }
