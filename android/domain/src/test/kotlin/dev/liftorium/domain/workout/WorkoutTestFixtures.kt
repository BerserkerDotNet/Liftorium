package dev.liftorium.domain.workout

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceId
import dev.liftorium.domain.common.DeviceIdProvider
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal object WorkoutTestFixtures {

    const val DEFAULT_DEVICE_ID: String = "device-test-0001"
    const val FIRST_MUTATION_ID: String = "00000000-0000-0000-0000-000000000001"
    val FIXED_INSTANT: Instant = Instant.parse("2026-06-01T08:00:00Z")

    fun fixedTimeSource(instant: Instant = FIXED_INSTANT): TimeSource = TimeSource.fixed(instant)

    fun sequentialIdGenerator(prefix: String = "id"): IdGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "$prefix-${(++counter).toString().padStart(8, '0')}"
    }

    fun fixedDeviceIdProvider(value: String = DEFAULT_DEVICE_ID): DeviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun current(): DeviceId = DeviceId(value)
        }

    fun planExercise(
        prescriptionItemId: String = "item-1",
        exerciseGroupId: String = "group-1",
        displayOrder: Int = 0,
        prescribedExerciseId: String = "ex-squat",
        warmupSetCount: Int = 0,
        sets: List<WorkoutPlanSet> = listOf(workingSet()),
    ): WorkoutPlanExercise = WorkoutPlanExercise(
        prescriptionItemId = prescriptionItemId,
        exerciseGroupId = exerciseGroupId,
        displayOrder = displayOrder,
        prescribedExerciseId = prescribedExerciseId,
        warmupSetCount = warmupSetCount,
        sets = sets,
    )

    fun workingSet(
        prescribedSetId: String = "set-1",
        percent: Double? = 0.75,
        displayLoad: Double? = 100.0,
        targetReps: Int? = 5,
        targetRpe: Double? = null,
        targetRir: Int? = null,
        referenceType: String? = "one_rep_max",
        referenceValue: Double? = 133.0,
    ): WorkoutPlanSet = WorkoutPlanSet(
        prescribedSetId = prescribedSetId,
        roleFromResource = SetRole.Working,
        perSide = false,
        target = WorkoutPlanTarget(
            referenceType = referenceType,
            referenceExerciseId = "ex-squat",
            referenceValue = referenceValue,
            referenceUnit = WeightUnit.Kg,
            percent = percent,
            roundingRule = "round_nearest_2_5_kg",
            calculatedRawLoad = displayLoad,
            displayLoad = displayLoad,
            displayLoadUnit = WeightUnit.Kg,
            targetReps = targetReps,
            targetRpe = targetRpe,
            targetRir = targetRir,
            caveats = emptyList(),
        ),
    )

    fun plan(
        programRunId: String = "run-1",
        plannedOccurrenceId: String = "occ-1",
        pinnedProgramVersionId: String = "pv-1",
        sessionTemplateId: String = "st-1",
        exercises: List<WorkoutPlanExercise> = listOf(planExercise()),
    ): WorkoutPlan = WorkoutPlan(
        programRunId = ProgramRunId(programRunId),
        plannedOccurrenceId = plannedOccurrenceId,
        pinnedProgramVersionId = pinnedProgramVersionId,
        sessionTemplateId = sessionTemplateId,
        exercises = exercises,
    )
}

/**
 * In-memory fake [WorkoutLoggingRepository] used by domain use-case
 * tests. Records the last insert/finish/complete invocation so tests
 * can assert on the bundle the use case built before delegating.
 *
 * The fake intentionally does NOT enforce the activeWorkoutSlot
 * unique invariant or any DAO behaviour — those are responsibilities
 * of the `:data` layer's `RoomWorkoutLoggingRepository` and tested
 * separately at the DAO level.
 */
internal class FakeWorkoutLoggingRepository(
    private val plan: WorkoutPlan? = WorkoutTestFixtures.plan(),
    private var insertOutcome: InsertWorkoutSessionOutcome = InsertWorkoutSessionOutcome.Success,
    private var completeOutcome: CompleteSetOutcome = CompleteSetOutcome.Success,
    private var finishOutcome: FinishSessionOutcome = FinishSessionOutcome.Success,
    private val openSession: WorkoutSessionAggregate? = null,
) : WorkoutLoggingRepository {

    data class InsertCapture(
        val session: WorkoutSession,
        val exercises: List<WorkoutExerciseLog>,
        val sets: List<ActualSet>,
        val snapshots: List<PrescriptionCalculationSnapshot>,
        val startMutation: LocalMutation,
    )

    data class CompleteCapture(
        val actualSetId: ActualSetId,
        val actualLoad: Double?,
        val actualLoadUnit: WeightUnit?,
        val actualReps: Int?,
        val actualRpe: Double?,
        val actualRir: Int?,
        val notes: String?,
        val mutation: LocalMutation,
    )

    data class FinishCapture(
        val workoutSessionId: WorkoutSessionId,
        val finalStatus: WorkoutSessionStatus,
        val nowEpochMillis: Long,
        val mutation: LocalMutation,
    )

    var lastInsert: InsertCapture? = null
        private set
    var lastComplete: CompleteCapture? = null
        private set
    var lastFinish: FinishCapture? = null
        private set

    override suspend fun loadWorkoutPlan(
        programRunId: ProgramRunId,
        plannedOccurrenceId: String,
    ): WorkoutPlan? = plan

    override suspend fun insertNewSession(
        session: WorkoutSession,
        exercises: List<WorkoutExerciseLog>,
        sets: List<ActualSet>,
        snapshots: List<PrescriptionCalculationSnapshot>,
        startMutation: LocalMutation,
    ): InsertWorkoutSessionOutcome {
        lastInsert = InsertCapture(session, exercises, sets, snapshots, startMutation)
        return insertOutcome
    }

    override suspend fun completeSet(
        actualSetId: ActualSetId,
        actualLoad: Double?,
        actualLoadUnit: WeightUnit?,
        actualReps: Int?,
        actualRpe: Double?,
        actualRir: Int?,
        notes: String?,
        mutation: LocalMutation,
    ): CompleteSetOutcome {
        lastComplete = CompleteCapture(
            actualSetId, actualLoad, actualLoadUnit, actualReps, actualRpe, actualRir, notes, mutation,
        )
        return completeOutcome
    }

    override suspend fun finishSession(
        workoutSessionId: WorkoutSessionId,
        finalStatus: WorkoutSessionStatus,
        nowEpochMillis: Long,
        mutation: LocalMutation,
    ): FinishSessionOutcome {
        lastFinish = FinishCapture(workoutSessionId, finalStatus, nowEpochMillis, mutation)
        return finishOutcome
    }

    override fun observeOpenSession(): Flow<WorkoutSessionAggregate?> = flowOf(openSession)

    override suspend fun findOpenSession(): WorkoutSessionAggregate? = openSession

    fun setInsertOutcome(outcome: InsertWorkoutSessionOutcome) {
        insertOutcome = outcome
    }

    @Suppress("unused")
    fun setCompleteOutcome(outcome: CompleteSetOutcome) {
        completeOutcome = outcome
    }

    @Suppress("unused")
    fun setFinishOutcome(outcome: FinishSessionOutcome) {
        finishOutcome = outcome
    }
}

@Suppress("unused")
internal fun mutationId(value: String = WorkoutTestFixtures.FIRST_MUTATION_ID): ClientMutationId =
    ClientMutationId(value)
