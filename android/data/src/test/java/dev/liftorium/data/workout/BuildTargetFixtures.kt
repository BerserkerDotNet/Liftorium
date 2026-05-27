package dev.liftorium.data.workout

import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.resource.LoadedExerciseGroupEntity
import dev.liftorium.data.resource.LoadedPrescriptionItemEntity
import dev.liftorium.data.resource.LoadedPrescriptionTargetEntity
import dev.liftorium.data.resource.LoadedProgramBlockEntity
import dev.liftorium.data.resource.LoadedProgramVersionBundle
import dev.liftorium.data.resource.LoadedProgramVersionEntity
import dev.liftorium.data.resource.LoadedProgramWeekEntity
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.resource.LoadedSessionTemplateEntity
import dev.liftorium.data.resource.LoadedSetPrescriptionEntity

/**
 * Seed helpers for tests that exercise
 * [dev.liftorium.data.workout.RoomWorkoutLoggingRepository.loadWorkoutPlan]
 * against a hand-built program version.
 *
 * Each test wires:
 *  * one program version (loaded_program_version + block + week +
 *    session template + group + item + one set with N target rows),
 *  * optional requiredReferences (with `supplied=true` baked-in value
 *    when present, or `supplied=false` to require a run-scoped row),
 *  * one program_run + schedule_occurrence + optional
 *    program_run_reference_value rows.
 *
 * The fixture builders accept just the inputs that vary between the
 * tested branches (target rows, programDefaultsJson, refs); shared
 * skeleton (block/week/session/group/item) is hard-coded.
 */
internal const val FIX_PROGRAM_ID: String = "prog"
internal const val FIX_VERSION_ID: String = "prog@v1"
internal const val FIX_BLOCK_ID: String = "block-1"
internal const val FIX_WEEK_ID: String = "week-1"
internal const val FIX_SESSION_ID: String = "session-1"
internal const val FIX_GROUP_ID: String = "group-1"
internal const val FIX_ITEM_ID: String = "item-1"
internal const val FIX_SET_ID: String = "set-1"
internal const val FIX_RUN_ID: String = "run-1"
internal const val FIX_OCCURRENCE_ID: String = "occ-1"

internal data class BuildTargetSeed(
    val targets: List<LoadedPrescriptionTargetEntity>,
    val requiredReferences: List<LoadedRequiredReferenceEntity> = emptyList(),
    val programDefaultsJson: String? = null,
    val runScopedReferences: List<Pair<String, Pair<Double, String>>> = emptyList(),
)

internal suspend fun seedBuildTargetScenario(
    db: LiftoriumDatabase,
    seed: BuildTargetSeed,
) {
    val versionDao = db.loadedProgramVersionDao()
    val version = LoadedProgramVersionEntity(
        programVersionId = FIX_VERSION_ID,
        programId = FIX_PROGRAM_ID,
        versionLabel = "v1",
        displayName = "Test Program",
        authorAttribution = null,
        contentHash = "${FIX_VERSION_ID.hashCode()}".padStart(64, '0').take(64),
        schemaVersion = 1,
        validationStatus = "activatable",
        loadedAtEpochMillis = 1_000L,
        programDefaultsJson = seed.programDefaultsJson,
        programStructureRoundingOverrideJson = null,
        importAuditJson = "{}",
        validationIssuesJson = "[]",
    )
    val bundle = LoadedProgramVersionBundle(
        version = version,
        catalogEntries = emptyList(),
        requiredReferences = seed.requiredReferences,
        progressionRules = emptyList(),
        blocks = listOf(
            LoadedProgramBlockEntity(
                programVersionId = FIX_VERSION_ID,
                blockId = FIX_BLOCK_ID,
                blockOrder = 1,
                displayName = "Block 1",
                roundingOverrideJson = null,
            ),
        ),
        weeks = listOf(
            LoadedProgramWeekEntity(
                programVersionId = FIX_VERSION_ID,
                weekId = FIX_WEEK_ID,
                blockId = FIX_BLOCK_ID,
                weekIndex = 1,
                variantOf = null,
                variantLabel = null,
            ),
        ),
        sessions = listOf(
            LoadedSessionTemplateEntity(
                programVersionId = FIX_VERSION_ID,
                sessionId = FIX_SESSION_ID,
                weekId = FIX_WEEK_ID,
                sessionIndex = 1,
                dayLabel = null,
                displayName = "Session 1",
            ),
        ),
        groups = listOf(
            LoadedExerciseGroupEntity(
                programVersionId = FIX_VERSION_ID,
                groupId = FIX_GROUP_ID,
                sessionId = FIX_SESSION_ID,
                groupOrder = 1,
                kind = "main",
            ),
        ),
        items = listOf(
            LoadedPrescriptionItemEntity(
                programVersionId = FIX_VERSION_ID,
                itemId = FIX_ITEM_ID,
                groupId = FIX_GROUP_ID,
                itemOrder = 1,
                prescribedExerciseId = "ex-1",
                role = "main",
                perSide = false,
                restSecondsHint = null,
                restMaxSecondsHint = null,
                warmupSetCount = null,
                notesJson = "[]",
            ),
        ),
        sets = listOf(
            LoadedSetPrescriptionEntity(
                programVersionId = FIX_VERSION_ID,
                setId = FIX_SET_ID,
                itemId = FIX_ITEM_ID,
                setOrder = 1,
                setKind = "working",
            ),
        ),
        targets = seed.targets,
    )
    versionDao.loadFullVersion(bundle)

    val writable = db.openHelper.writableDatabase
    writable.execSQL(
        "INSERT INTO program_run (programRunId, programVersionId, pinnedContentHash, " +
            "startedAtEpochMillis, status, chosenWeekVariantsJson, activeRunSlot, updatedAtEpochMillis) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
            FIX_RUN_ID, FIX_VERSION_ID, version.contentHash,
            1_000L, "active", "{}", null, 1_000L,
        ),
    )
    writable.execSQL(
        "INSERT INTO schedule_occurrence (programRunId, occurrenceId, plannedEpochDay, " +
            "actualCompletionEpochDay, blockId, weekId, sessionId, sessionIndex, state, updatedAtEpochMillis) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
            FIX_RUN_ID, FIX_OCCURRENCE_ID, 0L, null,
            FIX_BLOCK_ID, FIX_WEEK_ID, FIX_SESSION_ID, 1, "planned", 1_000L,
        ),
    )
    for ((refId, valueUnit) in seed.runScopedReferences) {
        writable.execSQL(
            "INSERT INTO program_run_reference_value (programRunId, referenceId, value, unit, source, suppliedAtEpochMillis) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(FIX_RUN_ID, refId, valueUnit.first, valueUnit.second, "user", 1_000L),
        )
    }
}

internal fun percentTarget(
    targetIndex: Int = 0,
    setId: String = FIX_SET_ID,
    referenceId: String? = "orm-ex-1",
    percent: Double? = 65.0,
    reps: Int? = 5,
    roundingIncrement: Double? = 5.0,
    roundingUnit: String? = "lb",
    rpeTarget: Double? = null,
    loadValue: Double? = null,
    loadUnit: String? = null,
): LoadedPrescriptionTargetEntity = LoadedPrescriptionTargetEntity(
    programVersionId = FIX_VERSION_ID,
    setId = setId,
    targetIndex = targetIndex,
    kind = "percent",
    referenceId = referenceId,
    loadValue = loadValue,
    loadUnit = loadUnit,
    reps = reps,
    repMin = null,
    repMax = null,
    percent = percent,
    percentMin = null,
    percentMax = null,
    amrap = null,
    roundingIncrement = roundingIncrement,
    roundingUnit = roundingUnit,
    rpeTarget = rpeTarget,
    rpeRangeMin = null,
    rpeRangeMax = null,
    rpeCap = null,
    rirTarget = null,
    rirRangeMin = null,
    rirRangeMax = null,
    rirFloor = null,
)

internal fun rpeCompanion(
    targetIndex: Int,
    setId: String = FIX_SET_ID,
    rpeTarget: Double = 8.0,
): LoadedPrescriptionTargetEntity = LoadedPrescriptionTargetEntity(
    programVersionId = FIX_VERSION_ID,
    setId = setId,
    targetIndex = targetIndex,
    kind = "rpe",
    referenceId = null,
    loadValue = null,
    loadUnit = null,
    reps = null,
    repMin = null,
    repMax = null,
    percent = null,
    percentMin = null,
    percentMax = null,
    amrap = null,
    roundingIncrement = null,
    roundingUnit = null,
    rpeTarget = rpeTarget,
    rpeRangeMin = null,
    rpeRangeMax = null,
    rpeCap = null,
    rirTarget = null,
    rirRangeMin = null,
    rirRangeMax = null,
    rirFloor = null,
)

internal fun requiredRef(
    referenceId: String = "orm-ex-1",
    referenceType: String = "one_rep_max",
    exerciseId: String? = "ex-1",
    supplied: Boolean = true,
    value: Double? = 315.0,
    unit: String? = "lb",
): LoadedRequiredReferenceEntity = LoadedRequiredReferenceEntity(
    programVersionId = FIX_VERSION_ID,
    referenceId = referenceId,
    referenceType = referenceType,
    exerciseId = exerciseId,
    firstRunnableWeekIndex = 1,
    supplied = supplied,
    value = value,
    unit = unit,
)
