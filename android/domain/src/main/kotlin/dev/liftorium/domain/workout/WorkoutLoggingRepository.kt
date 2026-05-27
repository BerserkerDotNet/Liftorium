package dev.liftorium.domain.workout

import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for the workout-logging aggregate. Implementations
 * live in `:data` and own the Room transactions that mutate workout
 * state and write the matching [LocalMutation] audit row atomically.
 *
 * All write operations are atomic at the repository boundary; partial
 * failures must roll back. Read operations are flow-based so the
 * Compose ViewModel can collect state without polling.
 *
 * Recovery contract (`docs/architecture.md` Recovery flow): after a
 * process death, [observeOpenSession] re-emits the last persisted
 * aggregate from Room. The aggregate's [WorkoutSession.lastSavedMutationId]
 * matches the [ClientMutationId] of the most recent successfully
 * committed mutation, and is the anchor the UI uses to verify
 * "last visible saved state" was recovered.
 */
public interface WorkoutLoggingRepository {

    /**
     * Builds the seed plan for the schedule occurrence, or `null` when
     * the occurrence is not found, its program-version pin is missing,
     * or its session template is empty. The data layer resolves the
     * pinned chosen-week variant against the loaded resource tables.
     */
    public suspend fun loadWorkoutPlan(
        programRunId: ProgramRunId,
        plannedOccurrenceId: String,
    ): WorkoutPlan?

    /**
     * Inserts a new in-progress [WorkoutSession] plus its seeded child
     * rows ([WorkoutExerciseLog], [ActualSet], [PrescriptionCalculationSnapshot])
     * and the start-workout [LocalMutation] audit row in a single Room
     * transaction.
     *
     * The `activeWorkoutSlot` partial-unique index on
     * `WorkoutSessionEntity` enforces the one-in-progress-session
     * invariant; if a concurrent insert wins the race, this returns
     * [InsertWorkoutSessionOutcome.AlreadyActiveSession].
     */
    public suspend fun insertNewSession(
        session: WorkoutSession,
        exercises: List<WorkoutExerciseLog>,
        sets: List<ActualSet>,
        snapshots: List<PrescriptionCalculationSnapshot>,
        startMutation: LocalMutation,
    ): InsertWorkoutSessionOutcome

    /**
     * Persists a "complete set" mutation: updates the matching
     * [ActualSet] row with the user-entered values + `SetState.Completed`
     * + bumped [SyncMetadata], updates the parent [WorkoutSession]'s
     * `lastSavedMutationId` + `updatedAtEpochMillis`, and writes the
     * [LocalMutation] audit row — all in one Room transaction.
     *
     * Returns [CompleteSetOutcome.UnknownSet] if [ActualSet] does not
     * exist, [CompleteSetOutcome.SessionNotActive] if the parent
     * session is not [WorkoutSessionStatus.InProgress].
     */
    public suspend fun completeSet(
        actualSetId: ActualSetId,
        actualLoad: Double?,
        actualLoadUnit: WeightUnit?,
        actualReps: Int?,
        actualRpe: Double?,
        actualRir: Int?,
        notes: String?,
        mutation: LocalMutation,
    ): CompleteSetOutcome

    /**
     * Marks the workout session [WorkoutSessionStatus.Completed] (or
     * [WorkoutSessionStatus.Abandoned]). Both transitions are
     * permanent. Writes the corresponding [LocalMutation] audit row in
     * the same transaction.
     */
    public suspend fun finishSession(
        workoutSessionId: WorkoutSessionId,
        finalStatus: WorkoutSessionStatus,
        nowEpochMillis: Long,
        mutation: LocalMutation,
    ): FinishSessionOutcome

    /**
     * Returns the in-progress workout aggregate as a flow. Emits `null`
     * when no session is `InProgress`. Recovery from process death
     * subscribes to this on app launch.
     */
    public fun observeOpenSession(): Flow<WorkoutSessionAggregate?>

    /**
     * One-shot snapshot of [observeOpenSession]. Used by use cases that
     * need the current state without setting up a long-lived collector.
     */
    public suspend fun findOpenSession(): WorkoutSessionAggregate?
}

public sealed interface InsertWorkoutSessionOutcome {
    public data object Success : InsertWorkoutSessionOutcome
    public data object AlreadyActiveSession : InsertWorkoutSessionOutcome
}

public sealed interface CompleteSetOutcome {
    public data object Success : CompleteSetOutcome
    public data class UnknownSet(val actualSetId: ActualSetId) : CompleteSetOutcome
    public data class SessionNotActive(
        val workoutSessionId: WorkoutSessionId,
        val status: WorkoutSessionStatus,
    ) : CompleteSetOutcome
}

public sealed interface FinishSessionOutcome {
    public data object Success : FinishSessionOutcome
    public data class UnknownSession(val workoutSessionId: WorkoutSessionId) : FinishSessionOutcome
    public data class AlreadyTerminal(
        val workoutSessionId: WorkoutSessionId,
        val status: WorkoutSessionStatus,
    ) : FinishSessionOutcome
}
