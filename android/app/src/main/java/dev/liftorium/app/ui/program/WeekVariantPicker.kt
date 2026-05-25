package dev.liftorium.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import dev.liftorium.app.ui.VariantGroupUi
import dev.liftorium.app.ui.theme.LiftoriumTokens
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Modal week-variant picker. One [VariantGroupUi] per multi-member
 * variant group; the user must pick one option per group. Empty
 * groups list raises [onConfirm] immediately with an empty map.
 */
@Composable
public fun WeekVariantPicker(
    groups: ImmutableList<VariantGroupUi>,
    onConfirm: (Map<WeekVariantGroupKey, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(groups) { mutableStateMapOf<WeekVariantGroupKey, String>() }
    val allChosen = remember(groups) {
        derivedStateOf { groups.all { choices.containsKey(it.key) } }
    }.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose week variants") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = "variant-picker" },
                verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.md),
            ) {
                for (group in groups) {
                    Column {
                        Text(
                            group.baseLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.sm)) {
                            for (option in group.options) {
                                FilterChip(
                                    selected = choices[group.key] == option.weekId,
                                    onClick = { choices[group.key] = option.weekId },
                                    label = { Text(option.label) },
                                    modifier = Modifier.semantics {
                                        testTag = "variant-${group.key.baseWeekId}-${option.weekId}"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(choices.toMap()) },
                enabled = allChosen,
                modifier = Modifier.semantics { testTag = "variant-picker-confirm" },
            ) { Text("Activate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
