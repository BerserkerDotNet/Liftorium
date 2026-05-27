package dev.liftorium.app.ui.workout

import androidx.compose.runtime.Immutable
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.workout.ActualSet
import dev.liftorium.domain.workout.ActualSetId
import dev.liftorium.domain.workout.ActualSetWithSnapshot
import dev.liftorium.domain.workout.PrescriptionCalculationSnapshot
import dev.liftorium.domain.workout.SetRole
import dev.liftorium.domain.workout.SetState
import dev.liftorium.domain.workout.WorkoutExerciseLogWithSets
import dev.liftorium.domain.workout.WorkoutSessionAggregate
import dev.liftorium.domain.workout.WorkoutSessionId
import dev.liftorium.domain.workout.WorkoutSessionStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Stateless UI state for the workout-logging shell. Fed by
 * [WorkoutSessionViewModel] which maps a [WorkoutSessionAggregate]
 * (the Flow read from the Room workout DAO) into this immutable
 * snapshot.
 *
 * Slice 1 surfaces the "just-started" workout: each exercise row is
 * rendered with its warm-up rows + working rows in seeded
 * [SetState.Pending] state. Set completion interaction lands in
 * Slice 2.
 */
public sealed interface ActiveWorkoutShellState {
    @Immutable
    public data object Loading : ActiveWorkoutShellState

    @Immutable
    public data class NoActive(
        val pendingError: StartWorkoutErrorUi? = null,
    ) : ActiveWorkoutShellState

    @Immutable
    public data class Active(
        val ui: ActiveWorkoutUiState,
    ) : ActiveWorkoutShellState
}

@Immutable
public data class ActiveWorkoutUiState(
    val workoutSessionId: WorkoutSessionId,
    val programRunId: ProgramRunId,
    val plannedOccurrenceId: String,
    val pinnedProgramVersionId: String,
    val status: WorkoutSessionStatus,
    val startedAtEpochMillis: Long,
    val lastSavedMutationId: String,
    val title: String,
    val subtitle: String?,
    val exercises: ImmutableList<ActiveWorkoutExerciseUi>,
)

@Immutable
public data class ActiveWorkoutExerciseUi(
    val workoutExerciseLogId: String,
    val displayOrder: Int,
    val displayName: String,
    val isCompleted: Boolean,
    val isSkipped: Boolean,
    val sets: ImmutableList<ActiveWorkoutSetUi>,
)

@Immutable
public data class ActiveWorkoutSetUi(
    val actualSetId: ActualSetId,
    val sequence: Int,
    val role: SetRole,
    val state: SetState,
    val label: String,
    val targetSummary: String,
    val actualSummary: String?,
    val perSide: Boolean,
)

public sealed interface StartWorkoutErrorUi {
    public data object AlreadyActiveSession : StartWorkoutErrorUi
    public data class UnknownOccurrence(val plannedOccurrenceId: String) : StartWorkoutErrorUi
    public data class EmptyPlan(val plannedOccurrenceId: String) : StartWorkoutErrorUi
    public data class Unexpected(val message: String) : StartWorkoutErrorUi
}

/**
 * Pure mapper from the domain aggregate to the Compose state holder.
 * Lives in the UI package because every consumer is UI; keeping it
 * free of Android/Compose imports preserves testability.
 */
public fun WorkoutSessionAggregate.toUiState(): ActiveWorkoutUiState {
    val crumb = breadcrumb
    val title = crumb?.programDisplayName ?: "Workout"
    val subtitle = crumb?.let {
        listOfNotNull(
            "Cycle ${it.cycleIndex}",
            "Week ${it.weekIndex}",
            it.sessionDisplayName,
        ).joinToString(" · ")
    }
    return ActiveWorkoutUiState(
        workoutSessionId = session.workoutSessionId,
        programRunId = session.programRunId,
        plannedOccurrenceId = session.plannedOccurrenceId,
        pinnedProgramVersionId = session.pinnedProgramVersionId,
        status = session.status,
        startedAtEpochMillis = session.startedAtEpochMillis,
        lastSavedMutationId = session.lastSavedMutationId.value,
        title = title,
        subtitle = subtitle,
        exercises = exercises
            .sortedBy { it.log.displayOrder }
            .map { it.toUi() }
            .toImmutableList(),
    )
}

private fun WorkoutExerciseLogWithSets.toUi(): ActiveWorkoutExerciseUi =
    ActiveWorkoutExerciseUi(
        workoutExerciseLogId = log.workoutExerciseLogId.value,
        displayOrder = log.displayOrder,
        displayName = log.prescribedExerciseId,
        isCompleted = log.isCompleted,
        isSkipped = log.isSkipped,
        sets = sets
            .sortedBy { it.set.sequence }
            .mapIndexed { idx, withSnapshot -> withSnapshot.toUi(displayIndex = idx) }
            .toImmutableList(),
    )

private fun ActualSetWithSnapshot.toUi(displayIndex: Int): ActiveWorkoutSetUi =
    ActiveWorkoutSetUi(
        actualSetId = set.actualSetId,
        sequence = set.sequence,
        role = set.role,
        state = set.state,
        label = labelFor(set.role, displayIndex),
        targetSummary = targetSummaryFor(set, snapshot),
        actualSummary = actualSummaryFor(set),
        perSide = set.perSide,
    )

private fun labelFor(role: SetRole, displayIndex: Int): String = when (role) {
    SetRole.Warmup -> "Warm-up ${displayIndex + 1}"
    SetRole.Working -> "Set ${displayIndex + 1}"
    SetRole.TopSet -> "Top set"
    SetRole.BackOff -> "Back-off ${displayIndex + 1}"
    SetRole.Amrap -> "AMRAP"
    SetRole.Optional -> "Optional ${displayIndex + 1}"
    SetRole.Extra -> "Extra ${displayIndex + 1}"
}

private fun targetSummaryFor(
    set: ActualSet,
    snapshot: PrescriptionCalculationSnapshot?,
): String {
    if (snapshot == null) {
        return if (set.role == SetRole.Warmup) "Warm-up" else "—"
    }
    val load = snapshot.displayLoad?.let { formatLoad(it, snapshot.displayLoadUnit) }
    val reps = snapshot.targetReps?.let { "${it} reps" }
    val rpe = snapshot.targetRpe?.let { "RPE ${formatDouble(it)}" }
    val rir = snapshot.targetRir?.let { "RIR $it" }
    val percent = snapshot.percent?.let { "${it.toInt()}% 1RM" }
    val parts = listOfNotNull(load, reps, rpe, rir, percent)
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

private fun actualSummaryFor(set: ActualSet): String? {
    if (set.state == SetState.Pending) return null
    val load = set.actualLoad?.let { formatLoad(it, set.actualLoadUnit) }
    val reps = set.actualReps?.let { "${it} reps" }
    val rpe = set.actualRpe?.let { "RPE ${formatDouble(it)}" }
    val rir = set.actualRir?.let { "RIR $it" }
    val parts = listOfNotNull(load, reps, rpe, rir)
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun formatLoad(value: Double, unit: WeightUnit?): String {
    val rounded = if (value % 1.0 == 0.0) value.toLong().toString() else formatDouble(value)
    val suffix = when (unit) {
        WeightUnit.Kg -> " kg"
        WeightUnit.Lb -> " lb"
        null -> ""
    }
    return rounded + suffix
}

private fun formatDouble(value: Double): String {
    val asInt = value.toLong()
    return if (value == asInt.toDouble()) asInt.toString() else String.format("%.1f", value)
}

/** Sentinel used by the VM until the first emission lands. */
internal val EmptySetList: ImmutableList<ActiveWorkoutSetUi> = persistentListOf()
