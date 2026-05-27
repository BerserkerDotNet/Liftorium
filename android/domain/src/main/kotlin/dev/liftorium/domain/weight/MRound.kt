package dev.liftorium.domain.weight

import kotlin.math.floor

/**
 * Excel-MROUND semantics: returns [value] rounded to the nearest multiple of
 * [multiple], with ties rounded away from zero (i.e. half-up for positive
 * inputs). Used to render schema-prescribed working weights from a percent ×
 * reference value, e.g. `mround(315 * 0.65, 5.0) == 205.0`.
 *
 * The user-locked formula is `=IF(unit="kg", MROUND(refValue*pct, 2.5),
 * MROUND(refValue*pct, 5))`. This helper only does the rounding; per-unit
 * defaults and the percent ÷ 100 division live with the caller.
 */
public fun mround(value: Double, multiple: Double): Double {
    require(multiple > 0.0) { "multiple must be > 0, was $multiple" }
    require(value.isFinite()) { "value must be finite, was $value" }
    return if (value >= 0.0) {
        floor(value / multiple + 0.5) * multiple
    } else {
        -floor(-value / multiple + 0.5) * multiple
    }
}
