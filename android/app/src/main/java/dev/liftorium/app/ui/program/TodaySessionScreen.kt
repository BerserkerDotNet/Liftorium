package dev.liftorium.app.ui.program

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.liftorium.app.ui.TodayItemUi
import dev.liftorium.app.ui.TodaySessionUi
import dev.liftorium.app.ui.components.BadgeTone
import dev.liftorium.app.ui.components.StatusBadge
import dev.liftorium.app.ui.theme.LiftoriumTokens

/**
 * Read-only Today session stub. The Start workout CTA fires
 * [onStartWorkout] which the host wires into
 * `WorkoutSessionViewModel.start`; the workout-logging surface lives
 * in `dev.liftorium.app.ui.workout`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TodaySessionScreen(
    today: TodaySessionUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onStartWorkout: () -> Unit = {},
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
                .padding(LiftoriumTokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LiftoriumTokens.spacing.md),
        ) {
            item {
                Column {
                    Text(today.programDisplayName, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(LiftoriumTokens.spacing.sm))
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start workout")
                    }
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
        Column(modifier = Modifier.padding(LiftoriumTokens.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = item.role, tone = BadgeTone.Neutral)
            }
            Spacer(Modifier.height(LiftoriumTokens.spacing.xs))
            for (line in item.setLines) {
                Text(line, style = LiftoriumTokens.lTypography.numeric)
            }
        }
    }
}
