package dev.liftorium.domain.workout

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.KoverIgnore
import dev.liftorium.core.TimeSource
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceIdProvider
import dev.liftorium.domain.common.SyncMetadata
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import java.time.ZoneId

/**
 * Start a new workout from a planned schedule occurrence.
 *
 * Orchestration:
 *  1. Load the seed [WorkoutPlan] from the repository.
 *  2. Stamp ids onto the [WorkoutSession], one [WorkoutExerciseLog]
 *     per [WorkoutPlanExercise], `warmupSetCount` warm-up
 *     [ActualSet]s (role = [SetRole.Warmup], `prescribedSetId = null`)
 *     followed by one [ActualSet] per [WorkoutPlanSet] (working
 *     and other roles).
 *  3. Snapshot every prescribed set's targets into a
 *     [PrescriptionCalculationSnapshot].
 *  4. Build the start-workout [LocalMutation].
 *  5. Hand the bundle to the repository, which writes everything in a
 *     single Room transaction.
 *
 * Conjunctive percent + RPE handling is implicit: every non-null field
 * on [WorkoutPlanTarget] is carried into the [PrescriptionCalculationSnapshot]
 * — neither percent NOR RPE is dropped.
 *
 * If the repository reports
 * [InsertWorkoutSessionOutcome.AlreadyActiveSession] the use case
 * propagates it as [StartWorkoutResult.Failure.AlreadyActiveSession];
 * the caller (the UI) routes to the existing in-progress session
 * instead of starting a new one.
 */
public class StartWorkoutFromOccurrence(
    private val repository: WorkoutLoggingRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    public suspend operator fun invoke(
        command: StartWorkoutCommand,
    ): StartWorkoutResult {
        val plan = repository.loadWorkoutPlan(command.programRunId, command.plannedOccurrenceId)
            ?: return StartWorkoutResult.Failure.UnknownOccurrence(command.plannedOccurrenceId)
        if (plan.exercises.isEmpty()) {
            return StartWorkoutResult.Failure.EmptyPlan(command.plannedOccurrenceId)
        }

        val nowInstant = timeSource.now()
        val nowMillis = nowInstant.toEpochMilli()
        val localDateEpochDay = nowInstant.atZone(zoneId).toLocalDate().toEpochDay()
        val deviceId = deviceIdProvider.current()
        val mutationId = ClientMutationId(idGenerator.newId())

        val sessionId = WorkoutSessionId(idGenerator.newId())
        val baseMetadata = SyncMetadata.forInsert(nowMillis, deviceId, mutationId)

        val session = WorkoutSession(
            workoutSessionId = sessionId,
            programRunId = plan.programRunId,
            plannedOccurrenceId = plan.plannedOccurrenceId,
            pinnedProgramVersionId = plan.pinnedProgramVersionId,
            status = WorkoutSessionStatus.InProgress,
            startedAtEpochMillis = nowMillis,
            eventZoneId = zoneId.id,
            localDateEpochDay = localDateEpochDay,
            completedAtEpochMillis = null,
            abandonedAtEpochMillis = null,
            lastSavedMutationId = mutationId,
            syncMetadata = baseMetadata,
        )

        val logs = ArrayList<WorkoutExerciseLog>(plan.exercises.size)
        val sets = ArrayList<ActualSet>(plan.exercises.sumOf { it.warmupSetCount + it.sets.size })
        val snapshots = ArrayList<PrescriptionCalculationSnapshot>(plan.exercises.sumOf { it.sets.size })

        for (planExercise in plan.exercises) {
            val logId = WorkoutExerciseLogId(idGenerator.newId())
            logs += WorkoutExerciseLog(
                workoutExerciseLogId = logId,
                workoutSessionId = sessionId,
                prescriptionItemId = planExercise.prescriptionItemId,
                exerciseGroupId = planExercise.exerciseGroupId,
                displayOrder = planExercise.displayOrder,
                prescribedExerciseId = planExercise.prescribedExerciseId,
                performedExerciseId = planExercise.prescribedExerciseId,
                notes = null,
                isCompleted = false,
                isSkipped = false,
                syncMetadata = baseMetadata,
            )

            var sequence = 0
            repeat(planExercise.warmupSetCount) {
                val warmupId = ActualSetId(idGenerator.newId())
                sets += ActualSet(
                    actualSetId = warmupId,
                    workoutExerciseLogId = logId,
                    prescribedSetId = null,
                    role = SetRole.Warmup,
                    state = SetState.Pending,
                    sequence = sequence++,
                    performedExerciseId = planExercise.prescribedExerciseId,
                    perSide = false,
                    actualLoad = null,
                    actualLoadUnit = null,
                    actualReps = null,
                    actualRpe = null,
                    actualRir = null,
                    notes = null,
                    calculationSnapshotId = null,
                    sourceSubstitutionEventId = null,
                    syncMetadata = baseMetadata,
                )
            }

            for (planSet in planExercise.sets) {
                val setId = ActualSetId(idGenerator.newId())
                val snapshotId = PrescriptionCalculationSnapshotId(idGenerator.newId())
                val target = planSet.target
                snapshots += PrescriptionCalculationSnapshot(
                    snapshotId = snapshotId,
                    actualSetId = setId,
                    referenceType = target.referenceType,
                    referenceExerciseId = target.referenceExerciseId,
                    referenceValue = target.referenceValue,
                    referenceUnit = target.referenceUnit,
                    percent = target.percent,
                    roundingRule = target.roundingRule,
                    calculatedRawLoad = target.calculatedRawLoad,
                    displayLoad = target.displayLoad,
                    displayLoadUnit = target.displayLoadUnit,
                    targetReps = target.targetReps,
                    targetRpe = target.targetRpe,
                    targetRir = target.targetRir,
                    caveats = target.caveats,
                )
                sets += ActualSet(
                    actualSetId = setId,
                    workoutExerciseLogId = logId,
                    prescribedSetId = planSet.prescribedSetId,
                    role = planSet.roleFromResource,
                    state = SetState.Pending,
                    sequence = sequence++,
                    performedExerciseId = planExercise.prescribedExerciseId,
                    perSide = planSet.perSide,
                    actualLoad = null,
                    actualLoadUnit = null,
                    actualReps = null,
                    actualRpe = null,
                    actualRir = null,
                    notes = null,
                    calculationSnapshotId = snapshotId,
                    sourceSubstitutionEventId = null,
                    syncMetadata = baseMetadata,
                )
            }
        }

        val startMutation = LocalMutation(
            clientMutationId = mutationId,
            type = MutationType.StartWorkout,
            entityType = WORKOUT_SESSION_ENTITY_TYPE,
            entityId = sessionId.value,
            createdAtEpochMillis = nowMillis,
            eventZoneId = zoneId.id,
            localDateEpochDay = localDateEpochDay,
            syncMetadata = baseMetadata,
        )

        return when (repository.insertNewSession(session, logs, sets, snapshots, startMutation)) {
            InsertWorkoutSessionOutcome.Success ->
                StartWorkoutResult.Success(session, logs, sets, snapshots, startMutation)
            InsertWorkoutSessionOutcome.AlreadyActiveSession ->
                StartWorkoutResult.Failure.AlreadyActiveSession
        }
    }
}

@KoverIgnore
public data class StartWorkoutCommand(
    val programRunId: ProgramRunId,
    val plannedOccurrenceId: String,
)

public sealed interface StartWorkoutResult {
    public data class Success(
        val session: WorkoutSession,
        val exercises: List<WorkoutExerciseLog>,
        val sets: List<ActualSet>,
        val snapshots: List<PrescriptionCalculationSnapshot>,
        val startMutation: LocalMutation,
    ) : StartWorkoutResult

    public sealed interface Failure : StartWorkoutResult {
        public data class UnknownOccurrence(val plannedOccurrenceId: String) : Failure
        public data class EmptyPlan(val plannedOccurrenceId: String) : Failure
        public data object AlreadyActiveSession : Failure
    }
}

/** Stable string discriminator used in [LocalMutation.entityType] for the workout session row. */
public const val WORKOUT_SESSION_ENTITY_TYPE: String = "workout_session"

/** Stable string discriminator used in [LocalMutation.entityType] for the workout exercise log row. */
public const val WORKOUT_EXERCISE_LOG_ENTITY_TYPE: String = "workout_exercise_log"

/** Stable string discriminator used in [LocalMutation.entityType] for the actual set row. */
public const val ACTUAL_SET_ENTITY_TYPE: String = "actual_set"
