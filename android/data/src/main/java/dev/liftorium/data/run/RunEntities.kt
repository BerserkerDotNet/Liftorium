package dev.liftorium.data.run

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.liftorium.core.KoverIgnore
import dev.liftorium.data.resource.LoadedProgramVersionEntity

/**
 * android-program-runner Room schema for program runs.
 *
 * Design notes:
 *
 * * `activeRunSlot` is the one-active-program-run invariant carrier
 *   (see `docs/decisions.md` 2026-05-17 "One active program run
 *   enforced by DB unique index on activeRunSlot"). The column is
 *   `INTEGER NULL` with a unique index; the use case writes `1` only
 *   when `status = Active`, and `null` for `Completed` / `Abandoned`.
 *   SQLite treats multiple `NULL`s as distinct, so any number of
 *   non-Active rows coexist, but a second Active insert raises a
 *   constraint failure that the repository translates into a typed
 *   `AlreadyActiveRun` outcome.
 * * `programVersionId` is `ON DELETE RESTRICT`: you cannot delete a
 *   loaded program version that still has runs (history-preserving).
 * * `ScheduleOccurrenceEntity` and `ProgramRunReferenceValueEntity`
 *   are `ON DELETE CASCADE` against the run row.
 * * `pinnedContentHash` is stored on the run row itself (not joined
 *   from the loaded version) so that even if a future migration alters
 *   the canonicalisation, this run's identity is anchored to the hash
 *   it was started with.
 * * `chosenWeekVariantsJson` carries the user's variant picks as a
 *   JSON-encoded map `{blockId → {baseWeekId → chosenWeekId}}`; the
 *   schema is small and write-once at run start.
 */

@Entity(
    tableName = "program_run",
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramVersionEntity::class,
            parentColumns = ["programVersionId"],
            childColumns = ["programVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["programVersionId"]),
        Index(value = ["activeRunSlot"], unique = true),
        Index(value = ["startedAtEpochMillis"]),
    ],
)
@KoverIgnore
public data class ProgramRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "programRunId") val programRunId: String,
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "pinnedContentHash") val pinnedContentHash: String,
    @ColumnInfo(name = "startedAtEpochMillis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "chosenWeekVariantsJson") val chosenWeekVariantsJson: String,
    @ColumnInfo(name = "activeRunSlot") val activeRunSlot: Long?,
    @ColumnInfo(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "schedule_occurrence",
    primaryKeys = ["programRunId", "occurrenceId"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramRunEntity::class,
            parentColumns = ["programRunId"],
            childColumns = ["programRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["programRunId", "plannedEpochDay", "sessionIndex"]),
    ],
)
@KoverIgnore
public data class ScheduleOccurrenceEntity(
    @ColumnInfo(name = "programRunId") val programRunId: String,
    @ColumnInfo(name = "occurrenceId") val occurrenceId: String,
    @ColumnInfo(name = "plannedEpochDay") val plannedEpochDay: Long,
    @ColumnInfo(name = "actualCompletionEpochDay") val actualCompletionEpochDay: Long?,
    @ColumnInfo(name = "blockId") val blockId: String,
    @ColumnInfo(name = "weekId") val weekId: String,
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "sessionIndex") val sessionIndex: Int,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "program_run_reference_value",
    primaryKeys = ["programRunId", "referenceId"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramRunEntity::class,
            parentColumns = ["programRunId"],
            childColumns = ["programRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programRunId"])],
)
@KoverIgnore
public data class ProgramRunReferenceValueEntity(
    @ColumnInfo(name = "programRunId") val programRunId: String,
    @ColumnInfo(name = "referenceId") val referenceId: String,
    @ColumnInfo(name = "value") val value: Double,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "suppliedAtEpochMillis") val suppliedAtEpochMillis: Long,
)
