package dev.liftorium.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import dev.liftorium.app.ui.PendingReferenceRow
import dev.liftorium.app.ui.theme.LiftoriumTokens
import dev.liftorium.domain.common.WeightUnit
import kotlinx.collections.immutable.ImmutableList

/**
 * Pure validation helper for the pending-references dialog. Returns the
 * fully-resolved values map (only entries where every reference has a
 * positive numeric value); callers compare `result.size == references.size`
 * to decide whether Activate is enabled.
 *
 * Lives outside the Composable so it can be unit-tested without spinning
 * up Compose / Robolectric (AlertDialog windows are unreliable under
 * Robolectric — see `ActivateFlowSemanticsTest` notes).
 */
internal fun resolvePendingValues(
    references: List<PendingReferenceRow>,
    rawValues: Map<String, String>,
    units: Map<String, WeightUnit>,
): Map<String, PendingValue> =
    references.mapNotNull { ref ->
        val raw = rawValues[ref.referenceId]?.trim().orEmpty()
        val parsed = raw.toDoubleOrNull()
        if (parsed != null && parsed > 0.0) {
            ref.referenceId to PendingValue(parsed, units[ref.referenceId] ?: ref.defaultUnit)
        } else {
            null
        }
    }.toMap()

/**
 * Modal dialog collecting runtime 1RM values for every
 * unsupplied first-week reference. Returns the values via
 * [onConfirm] keyed by referenceId; raises [onDismiss] on cancel.
 */
@Composable
public fun PendingReferencesDialog(
    references: ImmutableList<PendingReferenceRow>,
    onConfirm: (Map<String, PendingValue>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rawValues = remember { mutableStateMapOf<String, String>() }
    val units = remember(references) {
        mutableStateMapOf<String, WeightUnit>().apply {
            for (ref in references) put(ref.referenceId, ref.defaultUnit)
        }
    }

    val resolved = resolvePendingValues(references, rawValues, units)
    val allValid = resolved.size == references.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter 1RMs") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = "pending-refs-dialog" },
                verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.sm),
            ) {
                Text(
                    "Provide a value for each unsupplied reference before this program can start.",
                    style = MaterialTheme.typography.bodySmall,
                )
                for (ref in references) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = rawValues[ref.referenceId].orEmpty(),
                                onValueChange = { rawValues[ref.referenceId] = it },
                                label = { Text(ref.displayLabel) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { testTag = "pending-input-${ref.referenceId}" },
                            )
                        }
                        Spacer(Modifier.width(LiftoriumTokens.spacing.sm))
                        UnitToggle(
                            selected = units[ref.referenceId] ?: ref.defaultUnit,
                            onSelect = { units[ref.referenceId] = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(resolved) },
                enabled = allValid,
                modifier = Modifier.semantics { testTag = "pending-refs-confirm" },
            ) { Text("Activate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun UnitToggle(selected: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.xs)) {
        for (unit in listOf(WeightUnit.Kg, WeightUnit.Lb)) {
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = { Text(if (unit == WeightUnit.Kg) "kg" else "lb") },
            )
        }
    }
}
