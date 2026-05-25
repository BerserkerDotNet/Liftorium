package dev.liftorium.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import dev.liftorium.app.ui.BlockUi
import dev.liftorium.app.ui.ProgramDetailUi
import dev.liftorium.app.ui.components.StatusBadge
import dev.liftorium.app.ui.theme.LiftoriumTokens

/**
 * Program detail with read-only block/week/session tree and an Activate
 * action. Activate raises [onActivate] when there are no pending refs
 * and no variant choices; otherwise the caller is expected to surface a
 * pending-references dialog / week-variant picker first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ProgramDetailScreen(
    detail: ProgramDetailUi,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badge = ValidationStatusBadge.of(detail.validationStatus)
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
                .padding(
                    horizontal = LiftoriumTokens.spacing.lg,
                    vertical = LiftoriumTokens.spacing.sm,
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.md),
        ) {
            Text(
                "v${detail.versionLabel}" +
                    (detail.authorAttribution?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            StatusBadge(text = badge.label, tone = badge.tone)

            HorizontalDivider(thickness = LiftoriumTokens.dimens.dividerThickness)
            Text("Structure", style = MaterialTheme.typography.titleMedium)
            for (block in detail.blocks) {
                BlockSection(block)
            }

            Spacer(Modifier.height(LiftoriumTokens.spacing.sm))
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
        Column(modifier = Modifier.padding(LiftoriumTokens.spacing.md)) {
            Text(
                block.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(LiftoriumTokens.spacing.xs))
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
