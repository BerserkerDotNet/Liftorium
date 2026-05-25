package dev.liftorium.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public data class LiftoriumDimens(
    val cardElevation: Dp = 1.dp,
    val badgeIndicatorSize: Dp = 8.dp,
    val badgeMinHeight: Dp = 24.dp,
    val dividerThickness: Dp = 1.dp,
)

internal val DefaultLiftoriumDimens: LiftoriumDimens = LiftoriumDimens()

internal val LocalLiftoriumDimens = compositionLocalOf { DefaultLiftoriumDimens }
