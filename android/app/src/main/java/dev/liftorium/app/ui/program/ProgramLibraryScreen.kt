package dev.liftorium.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import dev.liftorium.app.ui.ProgramVersionRow
import dev.liftorium.app.ui.components.EmptyState
import dev.liftorium.app.ui.components.StatusBadge
import dev.liftorium.app.ui.theme.LiftoriumTokens
import dev.liftorium.domain.common.ProgramVersionId
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
            EmptyState(
                title = "No programs loaded yet.",
                body = "Use Import to select a finalized program JSON from the import workflow.",
                modifier = Modifier.padding(padding),
                semanticsTag = "empty-library",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(
                        horizontal = LiftoriumTokens.spacing.md,
                        vertical = LiftoriumTokens.spacing.sm,
                    ),
                verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.sm),
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
    val badge = ValidationStatusBadge.of(row.validationStatus)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "version-row-${row.programVersionId.value}" },
        elevation = CardDefaults.cardElevation(defaultElevation = LiftoriumTokens.dimens.cardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftoriumTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.xs),
        ) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "v${row.versionLabel}" + (row.authorAttribution?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(LiftoriumTokens.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = badge.label, tone = badge.tone)
                Spacer(Modifier.width(LiftoriumTokens.spacing.sm))
                TextButton(onClick = onClick) { Text("Open") }
            }
        }
    }
}
