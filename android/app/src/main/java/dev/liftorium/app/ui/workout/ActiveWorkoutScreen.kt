package dev.liftorium.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.liftorium.app.ui.components.BadgeTone
import dev.liftorium.app.ui.components.StatusBadge
import dev.liftorium.app.ui.theme.LiftoriumTokens
import dev.liftorium.domain.workout.SetState

/**
 * Read-only Active workout shell rendered when there is an in-progress
 * [dev.liftorium.domain.workout.WorkoutSession]. Slice 1 shows the
 * seeded exercises with their warm-up + working rows; set-completion
 * interaction lands in Slice 2.
 *
 * Theming: relies on the outer `LiftoriumTheme` wrapper from the host
 * (see `LiftoriumNavHost` ADR — the host no longer wraps itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Workout in progress") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LiftoriumTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.md),
        ) {
            item {
                Column {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!state.subtitle.isNullOrBlank()) {
                        Text(
                            state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(state.exercises, key = { it.workoutExerciseLogId }) { exercise ->
                ActiveExerciseCard(exercise)
            }
        }
    }
}

@Composable
private fun ActiveExerciseCard(exercise: ActiveWorkoutExerciseUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LiftoriumTokens.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val tone = when {
                    exercise.isCompleted -> BadgeTone.Primary
                    exercise.isSkipped -> BadgeTone.Tertiary
                    else -> BadgeTone.Neutral
                }
                val label = when {
                    exercise.isCompleted -> "Done"
                    exercise.isSkipped -> "Skipped"
                    else -> "In progress"
                }
                StatusBadge(text = label, tone = tone)
            }
            Spacer(Modifier.height(LiftoriumTokens.spacing.xs))
            for (set in exercise.sets) {
                SetRow(set)
            }
        }
    }
}

@Composable
private fun SetRow(set: ActiveWorkoutSetUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            set.label,
            style = LiftoriumTokens.lTypography.numeric,
            modifier = Modifier.weight(0.25f),
        )
        Column(modifier = Modifier.weight(0.55f)) {
            Text(set.targetSummary, style = LiftoriumTokens.lTypography.numeric)
            set.actualSummary?.let { actual ->
                Text(
                    actual,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        val tone = when (set.state) {
            SetState.Pending -> BadgeTone.Neutral
            SetState.Completed -> BadgeTone.Primary
            SetState.Skipped -> BadgeTone.Tertiary
        }
        val label = when (set.state) {
            SetState.Pending -> "Pending"
            SetState.Completed -> "Done"
            SetState.Skipped -> "Skipped"
        }
        StatusBadge(text = label, tone = tone)
    }
}
