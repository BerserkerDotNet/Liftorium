package dev.liftorium.app.ui.program

import androidx.compose.runtime.Immutable
import dev.liftorium.domain.common.WeightUnit

/**
 * Result of one row in the pending-references dialog: the user-entered
 * numeric [value] in the chosen [unit]. Kept in its own file so callers
 * (typed `onConfirm` lambdas, future stats surfaces) can import the
 * type without dragging the entire dialog UI into their dependency graph.
 */
@Immutable
public data class PendingValue(
    val value: Double,
    val unit: WeightUnit,
)
