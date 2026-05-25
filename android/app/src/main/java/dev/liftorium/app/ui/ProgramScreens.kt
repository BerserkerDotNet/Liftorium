package dev.liftorium.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Top-level program library list. Each row tap raises [onSelectVersion]
 * with the selected `programVersionId`. The host action area shows an
 * Import button that delegates to [onImportClick] (the activity-side
 * caller opens a SAF document picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ProgramLibraryScreen(
    versions: ImmutableList<ProgramVersionRow>,
    onSelectVersion: (ProgramVersionId) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                actions = {
                    TextButton(onClick = onImportClick) { Text("Import…") }
                },
            )
        },
    ) { padding ->
        if (versions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .semantics { testTag = "empty-library" },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No programs loaded yet.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use Import to select a finalized program JSON from the import workflow.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(versions, key = { it.programVersionId.value }) { row ->
                    ProgramVersionCard(row = row, onClick = { onSelectVersion(row.programVersionId) })
                }
            }
        }
    }
}

@Composable
private fun ProgramVersionCard(row: ProgramVersionRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "version-row-${row.programVersionId.value}" },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "v${row.versionLabel}" + (row.authorAttribution?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(row.validationStatus)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClick) { Text("Open") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "activatable" -> "Activatable" to MaterialTheme.colorScheme.primary
        "pending_runtime_references" -> "Pending refs" to MaterialTheme.colorScheme.tertiary
        "blocked" -> "Blocked" to MaterialTheme.colorScheme.error
        "rejected" -> "Rejected" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledLabelColor = color,
        ),
    )
}

/**
 * Program detail with read-only block/week/session tree and an Activate
 * action. Activate raises [onActivate] when there are no pending refs
 * and no variant choices; otherwise the caller is expected to surface a
 * [PendingReferencesDialog] / [WeekVariantPicker] first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ProgramDetailScreen(
    detail: ProgramDetailUi,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(detail.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "v${detail.versionLabel}" +
                    (detail.authorAttribution?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusChip(detail.validationStatus)

            HorizontalDivider()
            Text("Structure", style = MaterialTheme.typography.titleMedium)
            for (block in detail.blocks) {
                BlockSection(block)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "activate-button" },
            ) {
                Text("Activate program")
            }
        }
    }
}

@Composable
private fun BlockSection(block: BlockUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                block.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            for (week in block.weeks) {
                Text("• ${week.label}", style = MaterialTheme.typography.bodyMedium)
                for (sessionTitle in week.sessionTitles) {
                    Text(
                        "    – $sessionTitle",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Modal dialog collecting runtime training-max / 1RM values for every
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

    val resolved = remember(references) {
        derivedStateOf {
            references.mapNotNull { ref ->
                val raw = rawValues[ref.referenceId]?.trim().orEmpty()
                val parsed = raw.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    ref.referenceId to PendingValue(parsed, units[ref.referenceId] ?: ref.defaultUnit)
                } else {
                    null
                }
            }.toMap()
        }
    }.value
    val allValid = remember(references) {
        derivedStateOf { resolved.size == references.size }
    }.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter training maxes") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = "pending-refs-dialog" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        Spacer(Modifier.width(8.dp))
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

@Immutable
public data class PendingValue(val value: Double, val unit: WeightUnit)

@Composable
private fun UnitToggle(selected: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (unit in listOf(WeightUnit.Kg, WeightUnit.Lb)) {
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = { Text(if (unit == WeightUnit.Kg) "kg" else "lb") },
            )
        }
    }
}

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (group in groups) {
                    Column {
                        Text(
                            group.baseLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * Read-only Today session stub. android-workout-logging replaces the body with the
 * workout logging surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TodaySessionScreen(
    today: TodaySessionUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(today.sessionTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(today.programDisplayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "android-workout-logging will replace this stub with workout logging.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(today.items, key = { it.itemId }) { item ->
                TodayItemCard(item)
            }
        }
    }
}

@Composable
private fun TodayItemCard(item: TodayItemUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(item.role) },
                )
            }
            Spacer(Modifier.height(4.dp))
            for (line in item.setLines) {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Surface a non-fatal loader error to the user. Used for hash mismatch,
 * conflict, schema-version, and parse errors raised by the import flow.
 */
@Composable
public fun ImportErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "import-error-banner" },
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
