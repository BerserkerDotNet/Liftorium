package dev.liftorium.app.ui.program

import dev.liftorium.app.ui.PendingReferenceRow
import dev.liftorium.domain.common.WeightUnit
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the pending-references Activate gate. The previous
 * implementation wrapped the `allValid` derivation in a `derivedStateOf`
 * whose lambda captured an already-evaluated `resolved` local instead of
 * any observable state, so Compose never recomputed it after the first
 * frame and the Activate button stayed disabled even when every training
 * max was filled in.
 *
 * These tests exercise the pure [resolvePendingValues] helper that the
 * dialog now calls directly, so the gate logic is verifiable without
 * spinning up Compose / Robolectric (which cannot reliably idle on
 * AlertDialog windows in this configuration).
 */
class PendingReferencesValidationTest {

    private val squat = PendingReferenceRow(
        referenceId = "squat-1rm",
        displayLabel = "1RM · Squat",
        referenceType = "one_rep_max",
        defaultUnit = WeightUnit.Kg,
    )
    private val bench = PendingReferenceRow(
        referenceId = "bench-1rm",
        displayLabel = "1RM · Bench",
        referenceType = "one_rep_max",
        defaultUnit = WeightUnit.Kg,
    )

    @Test
    fun allFieldsFilledWithPositiveValues_isFullyResolved() {
        val refs = listOf(squat, bench)
        val raw = mapOf("squat-1rm" to "250", "bench-1rm" to "225")
        val units = mapOf("squat-1rm" to WeightUnit.Kg, "bench-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertEquals(refs.size, resolved.size, "Activate must enable when every reference has a positive value")
        assertEquals(PendingValue(250.0, WeightUnit.Kg), resolved["squat-1rm"])
        assertEquals(PendingValue(225.0, WeightUnit.Kg), resolved["bench-1rm"])
    }

    @Test
    fun missingValue_isNotResolved() {
        val refs = listOf(squat, bench)
        val raw = mapOf("squat-1rm" to "250")
        val units = mapOf("squat-1rm" to WeightUnit.Kg, "bench-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertTrue(resolved.size < refs.size, "Activate must stay disabled when a value is missing")
        assertTrue(resolved.containsKey("squat-1rm"))
    }

    @Test
    fun blankWhitespaceValue_isNotResolved() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "   ")
        val units = mapOf("squat-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertTrue(resolved.isEmpty(), "Whitespace-only input must not count as a resolved reference")
    }

    @Test
    fun zeroValue_isNotResolved() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "0")
        val units = mapOf("squat-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertTrue(resolved.isEmpty(), "Zero must not count as a positive 1RM")
    }

    @Test
    fun negativeValue_isNotResolved() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "-50")
        val units = mapOf("squat-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertTrue(resolved.isEmpty(), "Negative values must not count as a resolved reference")
    }

    @Test
    fun nonNumericValue_isNotResolved() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "heavy")
        val units = mapOf("squat-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertTrue(resolved.isEmpty(), "Non-numeric input must not count as a resolved reference")
    }

    @Test
    fun trimsLeadingAndTrailingWhitespace() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "  250  ")
        val units = mapOf("squat-1rm" to WeightUnit.Kg)

        val resolved = resolvePendingValues(refs, raw, units)

        assertEquals(PendingValue(250.0, WeightUnit.Kg), resolved["squat-1rm"])
    }

    @Test
    fun chosenUnitOverridesDefault() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "550")
        val units = mapOf("squat-1rm" to WeightUnit.Lb)

        val resolved = resolvePendingValues(refs, raw, units)

        assertEquals(WeightUnit.Lb, resolved["squat-1rm"]?.unit, "Selected unit must propagate to PendingValue")
    }

    @Test
    fun missingUnitFallsBackToReferenceDefault() {
        val refs = listOf(squat)
        val raw = mapOf("squat-1rm" to "250")
        val units = emptyMap<String, WeightUnit>()

        val resolved = resolvePendingValues(refs, raw, units)

        assertEquals(WeightUnit.Kg, resolved["squat-1rm"]?.unit, "Fall back to reference's defaultUnit when none selected")
    }
}
