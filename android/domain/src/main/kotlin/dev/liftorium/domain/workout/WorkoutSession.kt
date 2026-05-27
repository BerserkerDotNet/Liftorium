package dev.liftorium.domain.workout

import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.SyncMetadata
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId

/**
 * The actual attempt at a planned schedule occurrence. The MVP
 * invariant is that every workout session is born from a
 * `ScheduleOccurrence`; ad-hoc sessions are out of MVP scope.
 *
 * `pinnedProgramVersionId` is the version the session was started
 * against, snapshotted so that re-imports or later program edits
 * cannot retroactively change historical workouts.
 *
 * `lastSavedMutationId` is the [ClientMutationId] of the most recent
 * user-visible mutation that touched this session OR any of its child
 * rows. Recovery uses it as the "last visible saved state" anchor.
 */
@KoverIgnore
public data class WorkoutSession(
    val workoutSessionId: WorkoutSessionId,
    val programRunId: ProgramRunId,
    val plannedOccurrenceId: String,
    val pinnedProgramVersionId: String,
    val status: WorkoutSessionStatus,
    val startedAtEpochMillis: Long,
    val eventZoneId: String,
    val localDateEpochDay: Long,
    val completedAtEpochMillis: Long?,
    val abandonedAtEpochMillis: Long?,
    val lastSavedMutationId: ClientMutationId,
    val syncMetadata: SyncMetadata,
)

@KoverIgnore
public data class WorkoutExerciseLog(
    val workoutExerciseLogId: WorkoutExerciseLogId,
    val workoutSessionId: WorkoutSessionId,
    val prescriptionItemId: String,
    val exerciseGroupId: String,
    val displayOrder: Int,
    val prescribedExerciseId: String,
    val performedExerciseId: String,
    val notes: String?,
    val isCompleted: Boolean,
    val isSkipped: Boolean,
    val syncMetadata: SyncMetadata,
) {
    init {
        require(!(isCompleted && isSkipped)) {
            "WorkoutExerciseLog cannot be both completed and skipped"
        }
    }
}

/**
 * One set inside one exercise log. Sets are created in [SetState.Pending]
 * at workout start and transition via mutations.
 *
 * Conjunctive percent + RPE: both `actualLoad` and `actualRpe`/`actualRir`
 * are independently nullable; saving a set with a percent load AND an
 * RPE companion writes both fields — neither is dropped (the test
 * `prescription with percent and rpe preserves both on save` enforces).
 */
@KoverIgnore
public data class ActualSet(
    val actualSetId: ActualSetId,
    val workoutExerciseLogId: WorkoutExerciseLogId,
    val prescribedSetId: String?,
    val role: SetRole,
    val state: SetState,
    val sequence: Int,
    val performedExerciseId: String,
    val perSide: Boolean,
    val actualLoad: Double?,
    val actualLoadUnit: WeightUnit?,
    val actualReps: Int?,
    val actualRpe: Double?,
    val actualRir: Int?,
    val notes: String?,
    val calculationSnapshotId: PrescriptionCalculationSnapshotId?,
    val sourceSubstitutionEventId: String?,
    val syncMetadata: SyncMetadata,
) {
    init {
        require((actualLoad == null) == (actualLoadUnit == null)) {
            "actualLoad and actualLoadUnit must be both null or both non-null"
        }
        require(actualRpe == null || actualRpe in 0.0..10.0) {
            "actualRpe must be within [0.0, 10.0] when set, got $actualRpe"
        }
        require(actualRir == null || actualRir >= 0) {
            "actualRir must be >= 0 when set, got $actualRir"
        }
    }
}

@KoverIgnore
public data class PrescriptionCalculationSnapshot(
    val snapshotId: PrescriptionCalculationSnapshotId,
    val actualSetId: ActualSetId,
    val referenceType: String?,
    val referenceExerciseId: String?,
    val referenceValue: Double?,
    val referenceUnit: WeightUnit?,
    val percent: Double?,
    val roundingRule: String?,
    val calculatedRawLoad: Double?,
    val displayLoad: Double?,
    val displayLoadUnit: WeightUnit?,
    val targetReps: Int?,
    val targetRpe: Double?,
    val targetRir: Int?,
    val caveats: List<String>,
)

@KoverIgnore
public data class LocalMutation(
    val clientMutationId: ClientMutationId,
    val type: MutationType,
    val entityType: String,
    val entityId: String,
    val createdAtEpochMillis: Long,
    val eventZoneId: String,
    val localDateEpochDay: Long,
    val syncMetadata: SyncMetadata,
)

@KoverIgnore
public data class WorkoutSessionAggregate(
    val session: WorkoutSession,
    val exercises: List<WorkoutExerciseLogWithSets>,
    val breadcrumb: WorkoutBreadcrumb? = null,
)

/**
 * Human-readable contextual title for an active workout. Derived from
 * the run's pinned program version + the schedule occurrence's block /
 * week / session. Computed in the data layer when the aggregate is
 * loaded so that cold-start recovery shows the same title — every
 * field is sourced from already-persisted Room rows, not from
 * in-memory ViewModel state.
 */
@KoverIgnore
public data class WorkoutBreadcrumb(
    val programDisplayName: String,
    val cycleIndex: Int,
    val weekIndex: Int,
    val sessionDisplayName: String?,
)

@KoverIgnore
public data class WorkoutExerciseLogWithSets(
    val log: WorkoutExerciseLog,
    val sets: List<ActualSetWithSnapshot>,
)

@KoverIgnore
public data class ActualSetWithSnapshot(
    val set: ActualSet,
    val snapshot: PrescriptionCalculationSnapshot?,
)
