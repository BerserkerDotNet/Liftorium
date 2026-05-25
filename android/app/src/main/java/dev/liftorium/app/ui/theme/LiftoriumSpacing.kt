package dev.liftorium.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. All padding, gaps, and arrangements in Liftorium screens
 * must read from this scale rather than hard-coded `.dp` values. See
 * `docs/design-system.md` for which step to reach for in which context.
 */
@Immutable
public data class LiftoriumSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

internal val DefaultLiftoriumSpacing: LiftoriumSpacing = LiftoriumSpacing()

internal val LocalLiftoriumSpacing = compositionLocalOf { DefaultLiftoriumSpacing }
