package dev.liftorium.data.workout

import dev.liftorium.data.LiftoriumDatabase

/**
 * Test helpers for the workout-logging DAO suite. Kept separate from
 * the test classes so multiple test files (Slice 2/3 will follow) can
 * reuse the same seed builders without duplicating fixture code.
 */

internal val FIXED_METADATA: SyncMetadataEmbeddable = SyncMetadataEmbeddable(
    createdAtEpochMillis = 1_000L,
    updatedAtEpochMillis = 1_000L,
    deletedAtEpochMillis = null,
    deviceId = "dev-1",
    localRevision = 1L,
    clientMutationId = "mut-ws-1",
)

internal fun seedProgramRun(
    db: LiftoriumDatabase,
    programRunId: String,
    programVersionId: String,
) {
    // Use direct SQL inserts to avoid pulling the whole program-resource
    // loader into this slice's tests. The FK from workout_session →
    // program_run requires the parent row to exist.
    val writable = db.openHelper.writableDatabase
    writable.execSQL(
        "INSERT OR IGNORE INTO loaded_program_version (" +
            "programVersionId, programId, versionLabel, displayName, " +
            "authorAttribution, contentHash, schemaVersion, validationStatus, " +
            "loadedAtEpochMillis, programDefaultsJson, programStructureRoundingOverrideJson, " +
            "importAuditJson, validationIssuesJson) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
            programVersionId, "p", "1", "Program", "Author",
            "hash-$programVersionId", 3, "activatable",
            100, null, null, "{}", "[]",
        ),
    )
    writable.execSQL(
        "INSERT INTO program_run (" +
            "programRunId, programVersionId, pinnedContentHash, startedAtEpochMillis, " +
            "status, chosenWeekVariantsJson, activeRunSlot, updatedAtEpochMillis) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
            programRunId, programVersionId, "hash-$programVersionId",
            1_000L, "active", "{}", null, 1_000L,
        ),
    )
}

internal fun buildBundle(
    sessionId: String,
    programRunId: String,
    programVersionId: String,
    activeSlot: Long?,
): WorkoutSessionInsertBundle {
    val mutationIdValue = "mut-$sessionId"
    val sessionMetadata = FIXED_METADATA.copy(clientMutationId = mutationIdValue)
    val session = WorkoutSessionEntity(
        workoutSessionId = sessionId,
        programRunId = programRunId,
        plannedOccurrenceId = "occ-$sessionId",
        pinnedProgramVersionId = programVersionId,
        status = "in_progress",
        startedAtEpochMillis = 1_000L,
        eventZoneId = "UTC",
        localDateEpochDay = 0L,
        completedAtEpochMillis = null,
        abandonedAtEpochMillis = null,
        lastSavedMutationId = mutationIdValue,
        activeWorkoutSlot = activeSlot,
        syncMetadata = sessionMetadata,
    )
    val logs = (0 until 2).map { idx ->
        WorkoutExerciseLogEntity(
            workoutExerciseLogId = "$sessionId-log-$idx",
            workoutSessionId = sessionId,
            prescriptionItemId = "item-$idx",
            exerciseGroupId = "group-$idx",
            displayOrder = idx,
            prescribedExerciseId = "ex-$idx",
            performedExerciseId = "ex-$idx",
            notes = null,
            isCompleted = false,
            isSkipped = false,
            syncMetadata = sessionMetadata,
        )
    }
    val sets = logs.flatMap { log ->
        (0 until 2).map { seq ->
            ActualSetEntity(
                actualSetId = "${log.workoutExerciseLogId}-set-$seq",
                workoutExerciseLogId = log.workoutExerciseLogId,
                prescribedSetId = "pset-${log.workoutExerciseLogId}-$seq",
                role = if (seq == 0) "warmup" else "working",
                state = "pending",
                sequence = seq,
                performedExerciseId = log.prescribedExerciseId,
                perSide = false,
                actualLoad = null,
                actualLoadUnit = null,
                actualReps = null,
                actualRpe = null,
                actualRir = null,
                notes = null,
                calculationSnapshotId = if (seq == 0) null else "snap-${log.workoutExerciseLogId}-$seq",
                sourceSubstitutionEventId = null,
                syncMetadata = sessionMetadata,
            )
        }
    }
    val snapshots = sets
        .filter { it.calculationSnapshotId != null }
        .map { set ->
            PrescriptionCalculationSnapshotEntity(
                snapshotId = set.calculationSnapshotId!!,
                actualSetId = set.actualSetId,
                referenceType = "one_rep_max",
                referenceExerciseId = "ref-1",
                referenceValue = 100.0,
                referenceUnit = "kg",
                percent = 0.75,
                roundingRule = null,
                calculatedRawLoad = 75.0,
                displayLoad = 75.0,
                displayLoadUnit = "kg",
                targetReps = 5,
                targetRpe = null,
                targetRir = null,
                caveatsJson = "[]",
            )
        }
    val startMutation = LocalMutationEntity(
        clientMutationId = mutationIdValue,
        type = "start_workout",
        entityType = "workout_session",
        entityId = sessionId,
        createdAtEpochMillis = 1_000L,
        eventZoneId = "UTC",
        localDateEpochDay = 0L,
        syncMetadata = sessionMetadata,
    )
    return WorkoutSessionInsertBundle(
        session = session,
        exerciseLogs = logs,
        actualSets = sets,
        snapshots = snapshots,
        startMutation = startMutation,
    )
}
