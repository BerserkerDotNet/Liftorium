package dev.liftorium.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Token accessor for the non-Material parts of [LiftoriumTheme]: the
 * spacing scale, the surface dimensions, and the extra typography slot
 * for tabular-numerals values. Color and Material typography stay on
 * `MaterialTheme.colorScheme` / `MaterialTheme.typography`.
 *
 * All getters are `@ReadOnlyComposable` so they participate in Compose
 * stability inference and may be called from any `@Composable` context
 * without forcing a recomposition slot.
 */
public object LiftoriumTokens {
    public val lTypography: LiftoriumTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLiftoriumTypography.current

    public val spacing: LiftoriumSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalLiftoriumSpacing.current

    public val dimens: LiftoriumDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalLiftoriumDimens.current
}
