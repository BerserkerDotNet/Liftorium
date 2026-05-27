package dev.liftorium.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.liftorium.core.KoverIgnore
import kotlinx.coroutines.flow.Flow

/**
 * Aggregate write/read surface for the workout-logging aggregate.
 *
 * Slice 1 (start workout) ships [insertSessionBundle] only. Subsequent
 * slices extend this DAO with `completeSet`, `finishSession`, and
 * undo flows; the contract is "every user-visible mutation writes its
 * row mutation(s) AND the matching `LocalMutationEntity` in one
 * `@Transaction` so partial states are impossible on disk".
 *
 * The in-progress invariant is enforced at the SQL layer by the
 * unique index on `workout_session.activeWorkoutSlot`. A second
 * insert with `activeWorkoutSlot = 1` while another InProgress row
 * exists raises a `SQLiteConstraintException`; the repository turns
 * that into a typed
 * [dev.liftorium.domain.workout.InsertWorkoutSessionOutcome.AlreadyActiveSession].
 */
@Dao
public abstract class WorkoutLoggingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertExerciseLogs(logs: List<WorkoutExerciseLogEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertActualSets(sets: List<ActualSetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertSnapshots(snapshots: List<PrescriptionCalculationSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertMutation(mutation: LocalMutationEntity)

    @Query("SELECT * FROM workout_session WHERE workoutSessionId = :workoutSessionId")
    public abstract suspend fun findSessionById(workoutSessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE activeWorkoutSlot IS NOT NULL LIMIT 1")
    public abstract suspend fun findActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE activeWorkoutSlot IS NOT NULL LIMIT 1")
    public abstract fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_exercise_log WHERE workoutSessionId = :workoutSessionId ORDER BY displayOrder ASC")
    public abstract suspend fun listExerciseLogs(workoutSessionId: String): List<WorkoutExerciseLogEntity>

    @Query(
        "SELECT * FROM actual_set " +
            "WHERE workoutExerciseLogId IN (:workoutExerciseLogIds) " +
            "ORDER BY workoutExerciseLogId ASC, sequence ASC",
    )
    public abstract suspend fun listSetsForLogs(workoutExerciseLogIds: List<String>): List<ActualSetEntity>

    @Query(
        "SELECT * FROM prescription_calculation_snapshot WHERE actualSetId IN (:actualSetIds)",
    )
    public abstract suspend fun listSnapshotsForSets(actualSetIds: List<String>): List<PrescriptionCalculationSnapshotEntity>

    @Query("SELECT * FROM local_mutation WHERE clientMutationId = :clientMutationId")
    public abstract suspend fun findMutationById(clientMutationId: String): LocalMutationEntity?

    /**
     * Inserts the workout-session aggregate in one transaction:
     * session row, all exercise-log rows, all actual-set rows, all
     * prescription-calculation-snapshot rows, and the start-workout
     * [LocalMutationEntity] audit row. The whole thing rolls back if
     * any single insert fails (including a constraint violation on
     * the `activeWorkoutSlot` unique index — see the class doc).
     */
    @Transaction
    public open suspend fun insertSessionBundle(bundle: WorkoutSessionInsertBundle) {
        insertSession(bundle.session)
        if (bundle.exerciseLogs.isNotEmpty()) insertExerciseLogs(bundle.exerciseLogs)
        if (bundle.actualSets.isNotEmpty()) insertActualSets(bundle.actualSets)
        if (bundle.snapshots.isNotEmpty()) insertSnapshots(bundle.snapshots)
        insertMutation(bundle.startMutation)
    }

    /**
     * Loads the open-session aggregate (session + logs + sets +
     * snapshots) in one transaction so the returned graph is internally
     * consistent. Returns null when no session is InProgress.
     */
    @Transaction
    public open suspend fun loadOpenSessionAggregate(): WorkoutSessionAggregateRow? {
        val session = findActiveSession() ?: return null
        val logs = listExerciseLogs(session.workoutSessionId)
        val sets = if (logs.isEmpty()) {
            emptyList()
        } else {
            listSetsForLogs(logs.map { it.workoutExerciseLogId })
        }
        val snapshots = if (sets.isEmpty()) {
            emptyList()
        } else {
            listSnapshotsForSets(sets.map { it.actualSetId })
        }
        return WorkoutSessionAggregateRow(
            session = session,
            exerciseLogs = logs,
            actualSets = sets,
            snapshots = snapshots,
        )
    }
}

@KoverIgnore
public data class WorkoutSessionInsertBundle(
    val session: WorkoutSessionEntity,
    val exerciseLogs: List<WorkoutExerciseLogEntity>,
    val actualSets: List<ActualSetEntity>,
    val snapshots: List<PrescriptionCalculationSnapshotEntity>,
    val startMutation: LocalMutationEntity,
)

@KoverIgnore
public data class WorkoutSessionAggregateRow(
    val session: WorkoutSessionEntity,
    val exerciseLogs: List<WorkoutExerciseLogEntity>,
    val actualSets: List<ActualSetEntity>,
    val snapshots: List<PrescriptionCalculationSnapshotEntity>,
)
