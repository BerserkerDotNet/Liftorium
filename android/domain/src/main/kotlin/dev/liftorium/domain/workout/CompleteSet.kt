package dev.liftorium.domain.workout

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.KoverIgnore
import dev.liftorium.core.TimeSource
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceIdProvider
import dev.liftorium.domain.common.SyncMetadata
import dev.liftorium.domain.common.WeightUnit
import java.time.ZoneId

/**
 * Mark a pending [ActualSet] as completed with user-entered values.
 *
 * The conjunctive percent + RPE contract is preserved here: both
 * [CompleteSetCommand.actualLoad] and [CompleteSetCommand.actualRpe]
 * (and/or [CompleteSetCommand.actualRir]) are forwarded to the
 * repository independently. The repository's transactional write
 * persists all non-null fields.
 *
 * Builds the matching [LocalMutation] (type [MutationType.CompleteSet],
 * entityType [ACTUAL_SET_ENTITY_TYPE], entityId = the set id) and the
 * stamped [SyncMetadata] for the audit row before delegating.
 */
public class CompleteSet(
    private val repository: WorkoutLoggingRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    public suspend operator fun invoke(command: CompleteSetCommand): CompleteSetOutcome {
        val nowInstant = timeSource.now()
        val nowMillis = nowInstant.toEpochMilli()
        val localDateEpochDay = nowInstant.atZone(zoneId).toLocalDate().toEpochDay()
        val deviceId = deviceIdProvider.current()
        val mutationId = ClientMutationId(idGenerator.newId())

        val mutation = LocalMutation(
            clientMutationId = mutationId,
            type = MutationType.CompleteSet,
            entityType = ACTUAL_SET_ENTITY_TYPE,
            entityId = command.actualSetId.value,
            createdAtEpochMillis = nowMillis,
            eventZoneId = zoneId.id,
            localDateEpochDay = localDateEpochDay,
            syncMetadata = SyncMetadata.forInsert(nowMillis, deviceId, mutationId),
        )

        return repository.completeSet(
            actualSetId = command.actualSetId,
            actualLoad = command.actualLoad,
            actualLoadUnit = command.actualLoadUnit,
            actualReps = command.actualReps,
            actualRpe = command.actualRpe,
            actualRir = command.actualRir,
            notes = command.notes,
            mutation = mutation,
        )
    }
}

@KoverIgnore
public data class CompleteSetCommand(
    val actualSetId: ActualSetId,
    val actualLoad: Double?,
    val actualLoadUnit: WeightUnit?,
    val actualReps: Int?,
    val actualRpe: Double?,
    val actualRir: Int?,
    val notes: String?,
) {
    init {
        require((actualLoad == null) == (actualLoadUnit == null)) {
            "actualLoad and actualLoadUnit must be both null or both non-null"
        }
        require(actualRpe == null || actualRpe in 0.0..10.0) {
            "actualRpe must be within [0.0, 10.0], got $actualRpe"
        }
        require(actualRir == null || actualRir >= 0) {
            "actualRir must be >= 0, got $actualRir"
        }
    }
}
