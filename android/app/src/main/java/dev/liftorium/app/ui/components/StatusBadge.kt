package dev.liftorium.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.liftorium.app.ui.theme.LiftoriumTokens

/**
 * Non-tappable status pill. Replaces the previous
 * `SuggestionChip(onClick={}, enabled=false)` pattern, which carried
 * Material chip click semantics and emitted disabled styling.
 * [StatusBadge] is a plain [Surface] + [Text] — TalkBack announces the
 * label only, with no implicit clickable affordance.
 */
@Composable
public fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val (container, content) = tone.colors()
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = LiftoriumTokens.dimens.badgeMinHeight)
                .padding(
                    horizontal = LiftoriumTokens.spacing.sm,
                    vertical = LiftoriumTokens.spacing.xs,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BadgeTone.colors(): Pair<Color, Color> = when (this) {
    BadgeTone.Primary ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    BadgeTone.Tertiary ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    BadgeTone.Error ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    BadgeTone.Neutral ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}
