package dev.liftorium.data.workout

import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.workout.ActualSet
import dev.liftorium.domain.workout.ActualSetId
import dev.liftorium.domain.workout.ActualSetWithSnapshot
import dev.liftorium.domain.workout.LocalMutation
import dev.liftorium.domain.workout.MutationType
import dev.liftorium.domain.workout.PrescriptionCalculationSnapshot
import dev.liftorium.domain.workout.PrescriptionCalculationSnapshotId
import dev.liftorium.domain.workout.SetRole
import dev.liftorium.domain.workout.SetState
import dev.liftorium.domain.workout.WorkoutExerciseLog
import dev.liftorium.domain.workout.WorkoutExerciseLogId
import dev.liftorium.domain.workout.WorkoutExerciseLogWithSets
import dev.liftorium.domain.workout.WorkoutSession
import dev.liftorium.domain.workout.WorkoutSessionAggregate
import dev.liftorium.domain.workout.WorkoutSessionId
import dev.liftorium.domain.workout.WorkoutSessionStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Domain ↔ Room mappers for the workout-logging aggregate. Each enum
 * exposes a `wire()` extension producing the canonical string written
 * to disk, and a `fromWire()` helper translating it back. Unknown wire
 * values raise [CorruptWorkoutWireValueException] so corrupt rows are
 * surfaced loudly instead of silently mis-bucketed.
 */

internal fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    workoutSessionId = workoutSessionId.value,
    programRunId = programRunId.value,
    plannedOccurrenceId = plannedOccurrenceId,
    pinnedProgramVersionId = pinnedProgramVersionId,
    status = status.wire(),
    startedAtEpochMillis = startedAtEpochMillis,
    eventZoneId = eventZoneId,
    localDateEpochDay = localDateEpochDay,
    completedAtEpochMillis = completedAtEpochMillis,
    abandonedAtEpochMillis = abandonedAtEpochMillis,
    lastSavedMutationId = lastSavedMutationId.value,
    activeWorkoutSlot = if (status == WorkoutSessionStatus.InProgress) 1L else null,
    syncMetadata = syncMetadata.toEmbeddable(),
)

internal fun WorkoutSessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    workoutSessionId = WorkoutSessionId(workoutSessionId),
    programRunId = ProgramRunId(programRunId),
    plannedOccurrenceId = plannedOccurrenceId,
    pinnedProgramVersionId = pinnedProgramVersionId,
    status = workoutSessionStatusFromWire(status, workoutSessionId),
    startedAtEpochMillis = startedAtEpochMillis,
    eventZoneId = eventZoneId,
    localDateEpochDay = localDateEpochDay,
    completedAtEpochMillis = completedAtEpochMillis,
    abandonedAtEpochMillis = abandonedAtEpochMillis,
    lastSavedMutationId = dev.liftorium.domain.common.ClientMutationId(lastSavedMutationId),
    syncMetadata = syncMetadata.toDomain(),
)

internal fun WorkoutExerciseLog.toEntity(): WorkoutExerciseLogEntity = WorkoutExerciseLogEntity(
    workoutExerciseLogId = workoutExerciseLogId.value,
    workoutSessionId = workoutSessionId.value,
    prescriptionItemId = prescriptionItemId,
    exerciseGroupId = exerciseGroupId,
    displayOrder = displayOrder,
    prescribedExerciseId = prescribedExerciseId,
    performedExerciseId = performedExerciseId,
    notes = notes,
    isCompleted = isCompleted,
    isSkipped = isSkipped,
    syncMetadata = syncMetadata.toEmbeddable(),
)

internal fun WorkoutExerciseLogEntity.toDomain(): WorkoutExerciseLog = WorkoutExerciseLog(
    workoutExerciseLogId = WorkoutExerciseLogId(workoutExerciseLogId),
    workoutSessionId = WorkoutSessionId(workoutSessionId),
    prescriptionItemId = prescriptionItemId,
    exerciseGroupId = exerciseGroupId,
    displayOrder = displayOrder,
    prescribedExerciseId = prescribedExerciseId,
    performedExerciseId = performedExerciseId,
    notes = notes,
    isCompleted = isCompleted,
    isSkipped = isSkipped,
    syncMetadata = syncMetadata.toDomain(),
)

internal fun ActualSet.toEntity(): ActualSetEntity = ActualSetEntity(
    actualSetId = actualSetId.value,
    workoutExerciseLogId = workoutExerciseLogId.value,
    prescribedSetId = prescribedSetId,
    role = role.wire(),
    state = state.wire(),
    sequence = sequence,
    performedExerciseId = performedExerciseId,
    perSide = perSide,
    actualLoad = actualLoad,
    actualLoadUnit = actualLoadUnit?.wire(),
    actualReps = actualReps,
    actualRpe = actualRpe,
    actualRir = actualRir,
    notes = notes,
    calculationSnapshotId = calculationSnapshotId?.value,
    sourceSubstitutionEventId = sourceSubstitutionEventId,
    syncMetadata = syncMetadata.toEmbeddable(),
)

internal fun ActualSetEntity.toDomain(): ActualSet = ActualSet(
    actualSetId = ActualSetId(actualSetId),
    workoutExerciseLogId = WorkoutExerciseLogId(workoutExerciseLogId),
    prescribedSetId = prescribedSetId,
    role = setRoleFromWire(role, actualSetId),
    state = setStateFromWire(state, actualSetId),
    sequence = sequence,
    performedExerciseId = performedExerciseId,
    perSide = perSide,
    actualLoad = actualLoad,
    actualLoadUnit = actualLoadUnit?.let { weightUnitFromWire(it, actualSetId) },
    actualReps = actualReps,
    actualRpe = actualRpe,
    actualRir = actualRir,
    notes = notes,
    calculationSnapshotId = calculationSnapshotId?.let { PrescriptionCalculationSnapshotId(it) },
    sourceSubstitutionEventId = sourceSubstitutionEventId,
    syncMetadata = syncMetadata.toDomain(),
)

internal fun PrescriptionCalculationSnapshot.toEntity(json: Json): PrescriptionCalculationSnapshotEntity =
    PrescriptionCalculationSnapshotEntity(
        snapshotId = snapshotId.value,
        actualSetId = actualSetId.value,
        referenceType = referenceType,
        referenceExerciseId = referenceExerciseId,
        referenceValue = referenceValue,
        referenceUnit = referenceUnit?.wire(),
        percent = percent,
        roundingRule = roundingRule,
        calculatedRawLoad = calculatedRawLoad,
        displayLoad = displayLoad,
        displayLoadUnit = displayLoadUnit?.wire(),
        targetReps = targetReps,
        targetRpe = targetRpe,
        targetRir = targetRir,
        caveatsJson = json.encodeToString(CAVEATS_SERIALIZER, caveats),
    )

internal fun PrescriptionCalculationSnapshotEntity.toDomain(json: Json): PrescriptionCalculationSnapshot =
    PrescriptionCalculationSnapshot(
        snapshotId = PrescriptionCalculationSnapshotId(snapshotId),
        actualSetId = ActualSetId(actualSetId),
        referenceType = referenceType,
        referenceExerciseId = referenceExerciseId,
        referenceValue = referenceValue,
        referenceUnit = referenceUnit?.let { weightUnitFromWire(it, snapshotId) },
        percent = percent,
        roundingRule = roundingRule,
        calculatedRawLoad = calculatedRawLoad,
        displayLoad = displayLoad,
        displayLoadUnit = displayLoadUnit?.let { weightUnitFromWire(it, snapshotId) },
        targetReps = targetReps,
        targetRpe = targetRpe,
        targetRir = targetRir,
        caveats = if (caveatsJson.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString(CAVEATS_SERIALIZER, caveatsJson)
        },
    )

internal fun LocalMutation.toEntity(): LocalMutationEntity = LocalMutationEntity(
    clientMutationId = clientMutationId.value,
    type = type.wire(),
    entityType = entityType,
    entityId = entityId,
    createdAtEpochMillis = createdAtEpochMillis,
    eventZoneId = eventZoneId,
    localDateEpochDay = localDateEpochDay,
    syncMetadata = syncMetadata.toEmbeddable(),
)

internal fun WorkoutSessionAggregateRow.toDomain(json: Json): WorkoutSessionAggregate {
    val setsByLog = actualSets.groupBy { it.workoutExerciseLogId }
    val snapshotsBySet = snapshots.associateBy { it.actualSetId }
    return WorkoutSessionAggregate(
        session = session.toDomain(),
        exercises = exerciseLogs.map { logEntity ->
            val rows = setsByLog[logEntity.workoutExerciseLogId].orEmpty()
            WorkoutExerciseLogWithSets(
                log = logEntity.toDomain(),
                sets = rows.map { setEntity ->
                    ActualSetWithSnapshot(
                        set = setEntity.toDomain(),
                        snapshot = snapshotsBySet[setEntity.actualSetId]?.toDomain(json),
                    )
                },
            )
        },
    )
}

internal fun WorkoutSessionStatus.wire(): String = when (this) {
    WorkoutSessionStatus.Planned -> "planned"
    WorkoutSessionStatus.InProgress -> "in_progress"
    WorkoutSessionStatus.Completed -> "completed"
    WorkoutSessionStatus.Abandoned -> "abandoned"
}

internal fun workoutSessionStatusFromWire(wire: String, sessionId: String): WorkoutSessionStatus = when (wire) {
    "planned" -> WorkoutSessionStatus.Planned
    "in_progress" -> WorkoutSessionStatus.InProgress
    "completed" -> WorkoutSessionStatus.Completed
    "abandoned" -> WorkoutSessionStatus.Abandoned
    else -> throw CorruptWorkoutWireValueException("workout_session.status", wire, sessionId)
}

internal fun SetRole.wire(): String = when (this) {
    SetRole.Warmup -> "warmup"
    SetRole.Working -> "working"
    SetRole.TopSet -> "top_set"
    SetRole.BackOff -> "back_off"
    SetRole.Amrap -> "amrap"
    SetRole.Optional -> "optional"
    SetRole.Extra -> "extra"
}

internal fun setRoleFromWire(wire: String, rowId: String): SetRole = when (wire) {
    "warmup" -> SetRole.Warmup
    "working" -> SetRole.Working
    "top_set" -> SetRole.TopSet
    "back_off" -> SetRole.BackOff
    "amrap" -> SetRole.Amrap
    "optional" -> SetRole.Optional
    "extra" -> SetRole.Extra
    else -> throw CorruptWorkoutWireValueException("actual_set.role", wire, rowId)
}

internal fun SetState.wire(): String = when (this) {
    SetState.Pending -> "pending"
    SetState.Completed -> "completed"
    SetState.Skipped -> "skipped"
}

internal fun setStateFromWire(wire: String, rowId: String): SetState = when (wire) {
    "pending" -> SetState.Pending
    "completed" -> SetState.Completed
    "skipped" -> SetState.Skipped
    else -> throw CorruptWorkoutWireValueException("actual_set.state", wire, rowId)
}

internal fun MutationType.wire(): String = when (this) {
    MutationType.StartWorkout -> "start_workout"
    MutationType.CompleteSet -> "complete_set"
    MutationType.EditSet -> "edit_set"
    MutationType.SkipSet -> "skip_set"
    MutationType.SkipExercise -> "skip_exercise"
    MutationType.AddExtraSet -> "add_extra_set"
    MutationType.UpdateNote -> "update_note"
    MutationType.LogRpeRir -> "log_rpe_rir"
    MutationType.CompleteWorkout -> "complete_workout"
    MutationType.AbandonWorkout -> "abandon_workout"
    MutationType.UndoSet -> "undo_set"
}

internal fun WeightUnit.wire(): String = when (this) {
    WeightUnit.Kg -> "kg"
    WeightUnit.Lb -> "lb"
}

internal fun weightUnitFromWire(wire: String, rowId: String): WeightUnit = when (wire) {
    "kg" -> WeightUnit.Kg
    "lb" -> WeightUnit.Lb
    else -> throw CorruptWorkoutWireValueException("weight unit", wire, rowId)
}

internal class CorruptWorkoutWireValueException(
    column: String,
    wireValue: String,
    rowId: String,
) : IllegalStateException(
    "$column carries unknown wire value '$wireValue' for rowId=$rowId",
)

private val CAVEATS_SERIALIZER = ListSerializer(String.serializer())
