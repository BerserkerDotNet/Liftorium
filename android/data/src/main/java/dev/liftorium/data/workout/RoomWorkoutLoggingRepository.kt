package dev.liftorium.data.workout

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import dev.liftorium.data.resource.LoadedProgramVersionDao
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.run.ProgramRunDao
import dev.liftorium.data.run.ProgramRunReferenceValueEntity
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.resource.RoundingOverride
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.weight.mround
import dev.liftorium.domain.workout.ActualSet
import dev.liftorium.domain.workout.ActualSetId
import dev.liftorium.domain.workout.CompleteSetOutcome
import dev.liftorium.domain.workout.FinishSessionOutcome
import dev.liftorium.domain.workout.InsertWorkoutSessionOutcome
import dev.liftorium.domain.workout.LocalMutation
import dev.liftorium.domain.workout.PrescriptionCalculationSnapshot
import dev.liftorium.domain.workout.SetRole
import dev.liftorium.domain.workout.WorkoutBreadcrumb
import dev.liftorium.domain.workout.WorkoutExerciseLog
import dev.liftorium.domain.workout.WorkoutLoggingRepository
import dev.liftorium.domain.workout.WorkoutPlan
import dev.liftorium.domain.workout.WorkoutPlanExercise
import dev.liftorium.domain.workout.WorkoutPlanSet
import dev.liftorium.domain.workout.WorkoutPlanTarget
import dev.liftorium.domain.workout.WorkoutSession
import dev.liftorium.domain.workout.WorkoutSessionAggregate
import dev.liftorium.domain.workout.WorkoutSessionId
import dev.liftorium.domain.workout.WorkoutSessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Room-backed [WorkoutLoggingRepository] for Slice 1 (start workout).
 *
 * Slice 1 implements:
 *  * [loadWorkoutPlan] — resolves the schedule occurrence against the
 *    loaded resource tree (session template → exercise groups →
 *    prescription items → set prescriptions → prescription targets) and
 *    the run's reference-value snapshot, producing a [WorkoutPlan] that
 *    the use case stamps into a [WorkoutSession].
 *  * [insertNewSession] — delegates the whole aggregate to
 *    [WorkoutLoggingDao.insertSessionBundle] and translates the
 *    `activeWorkoutSlot` unique-index violation into
 *    [InsertWorkoutSessionOutcome.AlreadyActiveSession].
 *  * [observeOpenSession] / [findOpenSession] — recovery surfaces fed
 *    from the same DAO so cold-start re-renders the last persisted
 *    aggregate without going through the start use case again.
 *
 * Slices 2/3/4 implement `completeSet` / `finishSession`; for Slice 1
 * those throw `NotImplementedError` so the contract is enforced by
 * tests as the slices land.
 *
 * Mirrors the constraint-exception-translation pattern from
 * `RoomProgramRunRepository.insertNewRun`.
 */
@Suppress("LongParameterList", "TooManyFunctions")
public class RoomWorkoutLoggingRepository(
    private val workoutDao: WorkoutLoggingDao,
    private val programRunDao: ProgramRunDao,
    private val versionDao: LoadedProgramVersionDao,
    private val json: Json = DEFAULT_JSON,
) : WorkoutLoggingRepository {

    override suspend fun loadWorkoutPlan(
        programRunId: ProgramRunId,
        plannedOccurrenceId: String,
    ): WorkoutPlan? {
        val rawRunId = programRunId.value
        val run = programRunDao.findById(rawRunId) ?: return null
        val occurrence = programRunDao.listOccurrences(rawRunId)
            .firstOrNull { it.occurrenceId == plannedOccurrenceId }
            ?: return null

        val programVersionId = run.programVersionId
        val sessionId = occurrence.sessionId

        val sessionTemplate = versionDao.listSessions(programVersionId)
            .firstOrNull { it.sessionId == sessionId }
            ?: return null

        val groups = versionDao.listGroups(programVersionId)
            .filter { it.sessionId == sessionId }
            .sortedBy { it.groupOrder }
        if (groups.isEmpty()) return null

        val items = versionDao.listItems(programVersionId)
        val itemsByGroup = items.groupBy { it.groupId }
        val sets = versionDao.listSets(programVersionId)
        val setsByItem = sets.groupBy { it.itemId }
        val targets = versionDao.listTargets(programVersionId)
        val targetsBySet = targets.groupBy { it.setId }

        val referenceValues = programRunDao.listReferenceValues(rawRunId)
            .associateBy { it.referenceId }
        val requiredReferences = versionDao.listRequiredReferences(programVersionId)
            .associateBy { it.referenceId }

        val versionRow = versionDao.findById(programVersionId)
        val programDefaults: RoundingOverride? = versionRow?.programDefaultsJson
            ?.let { runCatching { json.decodeFromString(RoundingOverride.serializer(), it) }.getOrNull() }
        val resolution = WeightResolution(
            referenceValues = referenceValues,
            requiredReferences = requiredReferences,
            programDefaults = programDefaults,
        )

        val planExercises = ArrayList<WorkoutPlanExercise>()
        var displayOrder = 0
        for (group in groups) {
            val groupItems = itemsByGroup[group.groupId]
                .orEmpty()
                .sortedBy { it.itemOrder }
            for (item in groupItems) {
                val itemSets = setsByItem[item.itemId]
                    .orEmpty()
                    .sortedBy { it.setOrder }
                val planSets = itemSets.map { set ->
                    val targetRows = targetsBySet[set.setId].orEmpty().sortedBy { it.targetIndex }
                    WorkoutPlanSet(
                        prescribedSetId = set.setId,
                        roleFromResource = roleFromPrescription(item.role),
                        perSide = item.perSide == true,
                        target = buildTarget(targetRows, resolution),
                    )
                }
                planExercises += WorkoutPlanExercise(
                    prescriptionItemId = item.itemId,
                    exerciseGroupId = group.groupId,
                    displayOrder = displayOrder++,
                    prescribedExerciseId = item.prescribedExerciseId,
                    warmupSetCount = item.warmupSetCount ?: 0,
                    sets = planSets,
                )
            }
        }
        if (planExercises.isEmpty()) return null

        return WorkoutPlan(
            programRunId = programRunId,
            plannedOccurrenceId = plannedOccurrenceId,
            pinnedProgramVersionId = programVersionId,
            sessionTemplateId = sessionTemplate.sessionId,
            exercises = planExercises,
        )
    }

    override suspend fun insertNewSession(
        session: WorkoutSession,
        exercises: List<WorkoutExerciseLog>,
        sets: List<ActualSet>,
        snapshots: List<PrescriptionCalculationSnapshot>,
        startMutation: LocalMutation,
    ): InsertWorkoutSessionOutcome {
        val bundle = WorkoutSessionInsertBundle(
            session = session.toEntity(),
            exerciseLogs = exercises.map { it.toEntity() },
            actualSets = sets.map { it.toEntity() },
            snapshots = snapshots.map { it.toEntity(json) },
            startMutation = startMutation.toEntity(),
        )
        return try {
            workoutDao.insertSessionBundle(bundle)
            InsertWorkoutSessionOutcome.Success
        } catch (constraintFailure: SQLiteConstraintException) {
            val existing = workoutDao.findActiveSession()
            if (existing != null && existing.workoutSessionId != session.workoutSessionId.value) {
                InsertWorkoutSessionOutcome.AlreadyActiveSession
            } else {
                Log.e(LOG_TAG, "insertNewSession: unexpected constraint failure", constraintFailure)
                throw constraintFailure
            }
        }
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
        throw NotImplementedError("completeSet lands in Slice 2")
    }

    override suspend fun finishSession(
        workoutSessionId: WorkoutSessionId,
        finalStatus: WorkoutSessionStatus,
        nowEpochMillis: Long,
        mutation: LocalMutation,
    ): FinishSessionOutcome {
        throw NotImplementedError("finishSession lands in Slice 3")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeOpenSession(): Flow<WorkoutSessionAggregate?> =
        workoutDao.observeActiveSession()
            .distinctUntilChanged { old, new ->
                old?.workoutSessionId == new?.workoutSessionId &&
                    old?.lastSavedMutationId == new?.lastSavedMutationId &&
                    old?.status == new?.status
            }
            .flatMapLatest { sessionRow ->
                flow {
                    if (sessionRow == null) {
                        emit(null)
                    } else {
                        emit(loadAggregateWithBreadcrumb())
                    }
                }
            }

    override suspend fun findOpenSession(): WorkoutSessionAggregate? =
        loadAggregateWithBreadcrumb()

    private suspend fun loadAggregateWithBreadcrumb(): WorkoutSessionAggregate? {
        val row = workoutDao.loadOpenSessionAggregate() ?: return null
        val aggregate = row.toDomain(json)
        val breadcrumb = runCatching { resolveBreadcrumb(aggregate.session) }.getOrNull()
        return if (breadcrumb == null) aggregate else aggregate.copy(breadcrumb = breadcrumb)
    }

    private suspend fun resolveBreadcrumb(session: WorkoutSession): WorkoutBreadcrumb? {
        val versionId = session.pinnedProgramVersionId
        val version = versionDao.findById(versionId) ?: return null
        val occurrence = programRunDao.listOccurrences(session.programRunId.value)
            .firstOrNull { it.occurrenceId == session.plannedOccurrenceId } ?: return null
        val block = versionDao.listBlocks(versionId)
            .firstOrNull { it.blockId == occurrence.blockId }
        val week = versionDao.listWeeks(versionId)
            .firstOrNull { it.weekId == occurrence.weekId }
        val sessionTemplate = versionDao.listSessions(versionId)
            .firstOrNull { it.sessionId == occurrence.sessionId }
        return WorkoutBreadcrumb(
            programDisplayName = version.displayName ?: version.programId,
            cycleIndex = block?.blockOrder ?: 1,
            weekIndex = week?.weekIndex ?: 1,
            sessionDisplayName = sessionTemplate?.displayName ?: sessionTemplate?.dayLabel,
        )
    }

    private data class WeightResolution(
        val referenceValues: Map<String, ProgramRunReferenceValueEntity>,
        val requiredReferences: Map<String, LoadedRequiredReferenceEntity>,
        val programDefaults: RoundingOverride?,
    )

    @Suppress("ComplexMethod", "LongMethod")
    private fun buildTarget(
        targets: List<dev.liftorium.data.resource.LoadedPrescriptionTargetEntity>,
        ctx: WeightResolution,
    ): WorkoutPlanTarget {
        if (targets.isEmpty()) {
            return WorkoutPlanTarget(
                referenceType = null,
                referenceExerciseId = null,
                referenceValue = null,
                referenceUnit = null,
                percent = null,
                roundingRule = null,
                calculatedRawLoad = null,
                displayLoad = null,
                displayLoadUnit = null,
                targetReps = null,
                targetRpe = null,
                targetRir = null,
                caveats = emptyList(),
            )
        }
        // Merge conjunctive target rows: pick the first non-null for each
        // schema-orthogonal field. Percent/load/reps live on one row; RPE
        // and RIR companions may live on sibling rows.
        val percent = targets.firstNotNullOfOrNull { it.percent }
        val referenceId = targets.firstNotNullOfOrNull { it.referenceId }
        val loadValue = targets.firstNotNullOfOrNull { it.loadValue }
        val loadUnitWire = targets.firstNotNullOfOrNull { it.loadUnit }
        val reps = targets.firstNotNullOfOrNull { it.reps ?: it.repMin }
        val rpeTarget = targets.firstNotNullOfOrNull { it.rpeTarget ?: it.rpeCap }
        val rirTarget = targets.firstNotNullOfOrNull { it.rirTarget ?: it.rirFloor }
        val targetRoundingIncrement = targets.firstNotNullOfOrNull { it.roundingIncrement }
        val targetRoundingUnit = targets.firstNotNullOfOrNull { it.roundingUnit }
        val setIdForErrors = targets.first().setId

        // Reference resolution: run-scoped runtime value, then supplied
        // value baked into the program's requiredReferences row.
        val runScopedRef = referenceId?.let(ctx.referenceValues::get)
        val requiredRef = referenceId?.let(ctx.requiredReferences::get)
        val referenceType = requiredRef?.referenceType
        val refValue = runScopedRef?.value ?: requiredRef?.value ?: loadValue
        val refUnitWire = runScopedRef?.unit ?: requiredRef?.unit ?: loadUnitWire
        val refUnit = refUnitWire?.let { weightUnitFromWire(it, setIdForErrors) }

        // Compute raw load: percent is stored as a 0-100 integer percent
        // (matching the schema), so divide by 100 before multiplying.
        val calculatedRaw = if (refValue != null && percent != null) {
            refValue * (percent / 100.0)
        } else {
            null
        }

        // Round using precedence: target override -> program default ->
        // per-unit fallback (5 lb / 2.5 kg).
        val unitForRounding = refUnit
        val (increment, roundingUnitOut) = resolveRounding(
            targetIncrement = targetRoundingIncrement,
            targetUnitWire = targetRoundingUnit,
            programDefaults = ctx.programDefaults,
            referenceUnit = unitForRounding,
            setIdForErrors = setIdForErrors,
        )
        val displayLoad = when {
            loadValue != null -> loadValue
            calculatedRaw != null && increment != null -> mround(calculatedRaw, increment)
            else -> calculatedRaw
        }
        return WorkoutPlanTarget(
            referenceType = referenceType,
            referenceExerciseId = requiredRef?.exerciseId,
            referenceValue = refValue,
            referenceUnit = refUnit,
            percent = percent,
            roundingRule = roundingUnitOut?.wire(),
            calculatedRawLoad = calculatedRaw,
            displayLoad = displayLoad,
            displayLoadUnit = unitForRounding ?: roundingUnitOut,
            targetReps = reps,
            targetRpe = rpeTarget,
            targetRir = rirTarget,
            caveats = emptyList(),
        )
    }

    private fun resolveRounding(
        targetIncrement: Double?,
        targetUnitWire: String?,
        programDefaults: RoundingOverride?,
        referenceUnit: WeightUnit?,
        setIdForErrors: String,
    ): Pair<Double?, WeightUnit?> {
        val targetUnit = targetUnitWire?.let { weightUnitFromWire(it, setIdForErrors) }
        if (targetIncrement != null) return targetIncrement to (targetUnit ?: referenceUnit)
        val defaultIncrement = programDefaults?.roundingIncrement
        val defaultUnit = programDefaults?.roundingUnit
        if (defaultIncrement != null) return defaultIncrement to (defaultUnit ?: referenceUnit)
        // Per-unit fallback: 5 lb / 2.5 kg matches the user-locked Excel
        // formula `=IF(unit="kg", MROUND(x, 2.5), MROUND(x, 5))`.
        val unit = referenceUnit ?: return null to null
        val increment = when (unit) {
            WeightUnit.Kg -> 2.5
            WeightUnit.Lb -> 5.0
        }
        return increment to unit
    }

    private fun roleFromPrescription(wire: String): SetRole = when (wire.lowercase()) {
        "warmup" -> SetRole.Warmup
        "working" -> SetRole.Working
        "top_set", "top" -> SetRole.TopSet
        "back_off", "backoff" -> SetRole.BackOff
        "amrap" -> SetRole.Amrap
        "optional" -> SetRole.Optional
        else -> SetRole.Working
    }

    public companion object {
        private const val LOG_TAG = "RoomWorkoutLoggingRepo"
        public val DEFAULT_JSON: Json = Json {
            prettyPrint = false
            encodeDefaults = true
        }
    }
}
