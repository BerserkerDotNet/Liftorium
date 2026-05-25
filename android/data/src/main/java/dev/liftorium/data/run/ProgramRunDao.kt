package dev.liftorium.data.run

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Aggregate write/read surface for the program-run aggregate. The
 * `insertRunBundle` method is the only entry point that creates a new
 * run row; it inserts the run, the run-scoped reference values, and
 * the seeded schedule occurrences inside a Room transaction so the
 * whole aggregate becomes visible at once.
 *
 * The active-run invariant is enforced at the SQL layer by the unique
 * index on `program_run.activeRunSlot` (see `docs/decisions.md`
 * 2026-05-17). A second insert with `activeRunSlot = 1` while another
 * Active row exists raises a `SQLiteConstraintException`; the
 * repository turns that into a typed
 * [dev.liftorium.domain.run.InsertRunOutcome.AlreadyActiveRun].
 */
@Dao
public abstract class ProgramRunDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertRun(run: ProgramRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertReferenceValues(values: List<ProgramRunReferenceValueEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertOccurrences(occurrences: List<ScheduleOccurrenceEntity>)

    @Query("SELECT * FROM program_run WHERE programRunId = :programRunId")
    public abstract suspend fun findById(programRunId: String): ProgramRunEntity?

    @Query("SELECT * FROM program_run WHERE activeRunSlot IS NOT NULL LIMIT 1")
    public abstract suspend fun findActiveRun(): ProgramRunEntity?

    @Query("SELECT * FROM program_run ORDER BY startedAtEpochMillis DESC")
    public abstract suspend fun listAllRuns(): List<ProgramRunEntity>

    @Query("SELECT * FROM schedule_occurrence WHERE programRunId = :programRunId ORDER BY plannedEpochDay ASC, sessionIndex ASC")
    public abstract suspend fun listOccurrences(programRunId: String): List<ScheduleOccurrenceEntity>

    @Query("SELECT * FROM program_run_reference_value WHERE programRunId = :programRunId")
    public abstract suspend fun listReferenceValues(programRunId: String): List<ProgramRunReferenceValueEntity>

    @Query(
        "UPDATE program_run SET status = :status, activeRunSlot = NULL, updatedAtEpochMillis = :updatedAtEpochMillis WHERE programRunId = :programRunId",
    )
    public abstract suspend fun markAbandoned(
        programRunId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * Marks the run [programRunId] as `Abandoned` and clears its
     * `activeRunSlot` sentinel, then returns the updated row, all in
     * one transaction. Returns null if no row matched the id.
     */
    @Transaction
    public open suspend fun markAbandonedAndReturn(
        programRunId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): ProgramRunEntity? {
        val rows = markAbandoned(programRunId, status, updatedAtEpochMillis)
        if (rows == 0) return null
        return findById(programRunId)
    }

    /**
     * Inserts a run + its reference values + its seeded occurrences in
     * one transaction. The unique index on `activeRunSlot` is the gate;
     * if another Active row exists this call throws
     * [android.database.sqlite.SQLiteConstraintException] and the whole
     * transaction rolls back (repository catches it).
     */
    @Transaction
    public open suspend fun insertRunBundle(
        run: ProgramRunEntity,
        referenceValues: List<ProgramRunReferenceValueEntity>,
        occurrences: List<ScheduleOccurrenceEntity>,
    ) {
        insertRun(run)
        if (referenceValues.isNotEmpty()) insertReferenceValues(referenceValues)
        if (occurrences.isNotEmpty()) insertOccurrences(occurrences)
    }
}
