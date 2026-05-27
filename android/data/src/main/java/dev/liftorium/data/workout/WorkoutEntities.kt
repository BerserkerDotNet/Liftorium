package dev.liftorium.data.workout

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.liftorium.core.KoverIgnore
import dev.liftorium.data.run.ProgramRunEntity

/**
 * android-workout-logging Room schema (introduced in v3).
 *
 * Design notes:
 *
 * * Every user-state entity embeds [SyncMetadataEmbeddable] inline so
 *   future sync-engine work has the full createdAt / updatedAt /
 *   deletedAt / deviceId / localRevision / clientMutationId surface
 *   from day one. The persistence shape uses raw `String` for
 *   `deviceId` / `clientMutationId`; mappers translate to/from the
 *   domain `DeviceId` / `ClientMutationId` value classes.
 * * [WorkoutSessionEntity.activeWorkoutSlot] is the one-in-progress
 *   workout-session invariant carrier (see ADR `docs/decisions.md`
 *   2026-05-25 "One in-progress workout session enforced by DB unique
 *   index on activeWorkoutSlot"). The column is `INTEGER NULL` with a
 *   unique index; mappers write `1` only when `status = InProgress`
 *   and `null` for terminal states. SQLite treats multiple `NULL`s as
 *   distinct, so any number of non-InProgress rows coexist; a second
 *   InProgress insert raises a constraint failure that
 *   [RoomWorkoutLoggingRepository] translates into a typed
 *   `AlreadyActiveSession` outcome.
 * * Foreign keys chain parent → child with `ON DELETE CASCADE`
 *   (program_run → workout_session → workout_exercise_log → actual_set
 *   → prescription_calculation_snapshot). Deleting a program run
 *   removes every dependent workout row in one statement.
 * * `LocalMutationEntity` rows are NOT cascaded — they outlive their
 *   target rows so a future sync engine can replay the mutation log
 *   even if the target was soft-deleted or rebuilt.
 * * `DeviceIdentityEntity` is a single-row table (PK = constant
 *   [DeviceIdentityEntity.SINGLETON_ID]) that stores the lazily-
 *   generated device UUID. See ADR 2026-05-25 "DeviceId is
 *   self-generated UUID, not Settings.Secure.ANDROID_ID".
 */

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = ProgramRunEntity::class,
            parentColumns = ["programRunId"],
            childColumns = ["programRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["programRunId"]),
        Index(value = ["status"]),
        Index(value = ["activeWorkoutSlot"], unique = true),
        Index(value = ["startedAtEpochMillis"]),
    ],
)
@KoverIgnore
public data class WorkoutSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "workoutSessionId") val workoutSessionId: String,
    @ColumnInfo(name = "programRunId") val programRunId: String,
    @ColumnInfo(name = "plannedOccurrenceId") val plannedOccurrenceId: String,
    @ColumnInfo(name = "pinnedProgramVersionId") val pinnedProgramVersionId: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "startedAtEpochMillis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "eventZoneId") val eventZoneId: String,
    @ColumnInfo(name = "localDateEpochDay") val localDateEpochDay: Long,
    @ColumnInfo(name = "completedAtEpochMillis") val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "abandonedAtEpochMillis") val abandonedAtEpochMillis: Long?,
    @ColumnInfo(name = "lastSavedMutationId") val lastSavedMutationId: String,
    @ColumnInfo(name = "activeWorkoutSlot") val activeWorkoutSlot: Long?,
    @Embedded val syncMetadata: SyncMetadataEmbeddable,
)

@Entity(
    tableName = "workout_exercise_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["workoutSessionId"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutSessionId", "displayOrder"]),
    ],
)
@KoverIgnore
public data class WorkoutExerciseLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "workoutExerciseLogId") val workoutExerciseLogId: String,
    @ColumnInfo(name = "workoutSessionId") val workoutSessionId: String,
    @ColumnInfo(name = "prescriptionItemId") val prescriptionItemId: String,
    @ColumnInfo(name = "exerciseGroupId") val exerciseGroupId: String,
    @ColumnInfo(name = "displayOrder") val displayOrder: Int,
    @ColumnInfo(name = "prescribedExerciseId") val prescribedExerciseId: String,
    @ColumnInfo(name = "performedExerciseId") val performedExerciseId: String,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "isCompleted") val isCompleted: Boolean,
    @ColumnInfo(name = "isSkipped") val isSkipped: Boolean,
    @Embedded val syncMetadata: SyncMetadataEmbeddable,
)

@Entity(
    tableName = "actual_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseLogEntity::class,
            parentColumns = ["workoutExerciseLogId"],
            childColumns = ["workoutExerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutExerciseLogId", "sequence"]),
    ],
)
@KoverIgnore
public data class ActualSetEntity(
    @PrimaryKey
    @ColumnInfo(name = "actualSetId") val actualSetId: String,
    @ColumnInfo(name = "workoutExerciseLogId") val workoutExerciseLogId: String,
    @ColumnInfo(name = "prescribedSetId") val prescribedSetId: String?,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "sequence") val sequence: Int,
    @ColumnInfo(name = "performedExerciseId") val performedExerciseId: String,
    @ColumnInfo(name = "perSide") val perSide: Boolean,
    @ColumnInfo(name = "actualLoad") val actualLoad: Double?,
    @ColumnInfo(name = "actualLoadUnit") val actualLoadUnit: String?,
    @ColumnInfo(name = "actualReps") val actualReps: Int?,
    @ColumnInfo(name = "actualRpe") val actualRpe: Double?,
    @ColumnInfo(name = "actualRir") val actualRir: Int?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "calculationSnapshotId") val calculationSnapshotId: String?,
    @ColumnInfo(name = "sourceSubstitutionEventId") val sourceSubstitutionEventId: String?,
    @Embedded val syncMetadata: SyncMetadataEmbeddable,
)

@Entity(
    tableName = "prescription_calculation_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = ActualSetEntity::class,
            parentColumns = ["actualSetId"],
            childColumns = ["actualSetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["actualSetId"], unique = true),
    ],
)
@KoverIgnore
public data class PrescriptionCalculationSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "snapshotId") val snapshotId: String,
    @ColumnInfo(name = "actualSetId") val actualSetId: String,
    @ColumnInfo(name = "referenceType") val referenceType: String?,
    @ColumnInfo(name = "referenceExerciseId") val referenceExerciseId: String?,
    @ColumnInfo(name = "referenceValue") val referenceValue: Double?,
    @ColumnInfo(name = "referenceUnit") val referenceUnit: String?,
    @ColumnInfo(name = "percent") val percent: Double?,
    @ColumnInfo(name = "roundingRule") val roundingRule: String?,
    @ColumnInfo(name = "calculatedRawLoad") val calculatedRawLoad: Double?,
    @ColumnInfo(name = "displayLoad") val displayLoad: Double?,
    @ColumnInfo(name = "displayLoadUnit") val displayLoadUnit: String?,
    @ColumnInfo(name = "targetReps") val targetReps: Int?,
    @ColumnInfo(name = "targetRpe") val targetRpe: Double?,
    @ColumnInfo(name = "targetRir") val targetRir: Int?,
    @ColumnInfo(name = "caveatsJson") val caveatsJson: String,
)

@Entity(
    tableName = "local_mutation",
    indices = [
        Index(value = ["entityType", "entityId", "createdAtEpochMillis"]),
    ],
)
@KoverIgnore
public data class LocalMutationEntity(
    @PrimaryKey
    @ColumnInfo(name = "clientMutationId") val clientMutationId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "entityType") val entityType: String,
    @ColumnInfo(name = "entityId") val entityId: String,
    @ColumnInfo(name = "createdAtEpochMillis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "eventZoneId") val eventZoneId: String,
    @ColumnInfo(name = "localDateEpochDay") val localDateEpochDay: Long,
    @Embedded(prefix = "audit_") val syncMetadata: SyncMetadataEmbeddable,
)

@Entity(tableName = "device_identity")
@KoverIgnore
public data class DeviceIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "deviceId") val deviceId: String,
    @ColumnInfo(name = "createdAtEpochMillis") val createdAtEpochMillis: Long,
) {
    public companion object {
        /** Single-row table — only one row exists, with PK = [SINGLETON_ID]. */
        public const val SINGLETON_ID: Int = 1
    }
}
