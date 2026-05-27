package dev.liftorium.domain.workout

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceIdProvider
import dev.liftorium.domain.common.SyncMetadata
import java.time.ZoneId

/**
 * Transition an in-progress workout session to a terminal state
 * ([WorkoutSessionStatus.Completed] or [WorkoutSessionStatus.Abandoned]).
 *
 * Both transitions are user-visible, durable mutations:
 *  * Builds the corresponding [LocalMutation] entry
 *    ([MutationType.CompleteWorkout] / [MutationType.AbandonWorkout]).
 *  * Delegates to [WorkoutLoggingRepository.finishSession], which
 *    updates the session row + writes the audit row atomically.
 *
 * `Abandoned` MUST NOT delete child rows; raw logs are preserved per
 * `docs/architecture.md` Durability contract.
 */
public class FinishWorkoutSession(
    private val repository: WorkoutLoggingRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    public suspend fun complete(workoutSessionId: WorkoutSessionId): FinishSessionOutcome =
        finish(workoutSessionId, WorkoutSessionStatus.Completed, MutationType.CompleteWorkout)

    public suspend fun abandon(workoutSessionId: WorkoutSessionId): FinishSessionOutcome =
        finish(workoutSessionId, WorkoutSessionStatus.Abandoned, MutationType.AbandonWorkout)

    private suspend fun finish(
        workoutSessionId: WorkoutSessionId,
        finalStatus: WorkoutSessionStatus,
        type: MutationType,
    ): FinishSessionOutcome {
        require(finalStatus == WorkoutSessionStatus.Completed || finalStatus == WorkoutSessionStatus.Abandoned) {
            "finalStatus must be terminal (Completed or Abandoned), got $finalStatus"
        }
        val nowInstant = timeSource.now()
        val nowMillis = nowInstant.toEpochMilli()
        val localDateEpochDay = nowInstant.atZone(zoneId).toLocalDate().toEpochDay()
        val deviceId = deviceIdProvider.current()
        val mutationId = ClientMutationId(idGenerator.newId())

        val mutation = LocalMutation(
            clientMutationId = mutationId,
            type = type,
            entityType = WORKOUT_SESSION_ENTITY_TYPE,
            entityId = workoutSessionId.value,
            createdAtEpochMillis = nowMillis,
            eventZoneId = zoneId.id,
            localDateEpochDay = localDateEpochDay,
            syncMetadata = SyncMetadata.forInsert(nowMillis, deviceId, mutationId),
        )

        return repository.finishSession(workoutSessionId, finalStatus, nowMillis, mutation)
    }
}
