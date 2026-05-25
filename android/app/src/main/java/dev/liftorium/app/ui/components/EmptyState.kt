package dev.liftorium.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import dev.liftorium.app.ui.theme.LiftoriumTokens

/**
 * Centered empty-state surface for list / library screens. Replaces the
 * inline `Column { Text("..."); Spacer; Text("...") }` pattern. Pass an
 * optional [semanticsTag] to surface the empty state to UI tests
 * (e.g., the program library uses `"empty-library"`).
 */
@Composable
public fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    semanticsTag: String? = null,
) {
    val tagged = if (semanticsTag != null) {
        modifier.semantics { testTag = semanticsTag }
    } else {
        modifier
    }
    Column(
        modifier = tagged
            .fillMaxSize()
            .padding(LiftoriumTokens.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            modifier = Modifier.padding(top = LiftoriumTokens.spacing.sm),
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
