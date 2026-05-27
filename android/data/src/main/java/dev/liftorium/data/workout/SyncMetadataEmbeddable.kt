package dev.liftorium.data.workout

import androidx.room.ColumnInfo
import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceId
import dev.liftorium.domain.common.SyncMetadata

/**
 * Room-side embeddable mirror of `dev.liftorium.domain.common.SyncMetadata`.
 *
 * Why a mirror instead of `@Embedded` on the domain type directly:
 *  * The domain type uses Kotlin value classes (`DeviceId`,
 *    `ClientMutationId`) which Room 2.6 cannot resolve as primitive
 *    columns without per-class `@TypeConverter` wiring. Mirroring as
 *    raw `String` keeps the persistence column shape obvious and the
 *    domain type independent of Room conventions.
 *  * The column names defined here are the canonical wire layout for
 *    every user-state table that embeds [SyncMetadataEmbeddable]. The
 *    `Migration2To3` SQL references these column names verbatim.
 *
 * Convention used by every user-state entity (workout_session,
 * workout_exercise_log, actual_set, local_mutation):
 *
 *   @Embedded val syncMetadata: SyncMetadataEmbeddable
 *
 * `LocalMutationEntity` uses `@Embedded(prefix = "audit_")` because
 * its own `clientMutationId` PK column would otherwise collide with
 * the embeddable's `clientMutationId` column.
 */
@KoverIgnore
public data class SyncMetadataEmbeddable(
    @ColumnInfo(name = "createdAtEpochMillis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deletedAtEpochMillis") val deletedAtEpochMillis: Long?,
    @ColumnInfo(name = "deviceId") val deviceId: String,
    @ColumnInfo(name = "localRevision") val localRevision: Long,
    @ColumnInfo(name = "clientMutationId") val clientMutationId: String,
)

internal fun SyncMetadata.toEmbeddable(): SyncMetadataEmbeddable = SyncMetadataEmbeddable(
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    deviceId = deviceId.value,
    localRevision = localRevision,
    clientMutationId = clientMutationId.value,
)

internal fun SyncMetadataEmbeddable.toDomain(): SyncMetadata = SyncMetadata(
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    deviceId = DeviceId(deviceId),
    localRevision = localRevision,
    clientMutationId = ClientMutationId(clientMutationId),
)
