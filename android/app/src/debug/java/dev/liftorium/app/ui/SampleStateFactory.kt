package dev.liftorium.app.ui

import dev.liftorium.app.ui.workout.ActiveWorkoutExerciseUi
import dev.liftorium.app.ui.workout.ActiveWorkoutSetUi
import dev.liftorium.app.ui.workout.ActiveWorkoutUiState
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.run.WeekVariantGroupKey
import dev.liftorium.domain.workout.ActualSetId
import dev.liftorium.domain.workout.SetRole
import dev.liftorium.domain.workout.SetState
import dev.liftorium.domain.workout.WorkoutSessionId
import dev.liftorium.domain.workout.WorkoutSessionStatus
import kotlinx.collections.immutable.persistentListOf

/**
 * Sample in-memory navigation state used by Paparazzi snapshots,
 * Robolectric semantics tests, and the launch-time placeholder in
 * `MainActivity`. Lives in the `debug` source set so it never ships
 * inside the release APK.
 *
 * Drawn entirely from in-memory data; no Room touch. Mirrors the
 * shape a real loader would emit after parsing a finalized JSON
 * resource.
 */
public object SampleStateFactory {

    public fun libraryWithMixedStatuses(): LiftoriumNavState.Library {
        val v1 = ProgramVersionRow(
            programVersionId = ProgramVersionId("5-3-1-bbb@v1"),
            displayName = "5/3/1 BBB",
            versionLabel = "1",
            authorAttribution = "Jim Wendler",
            validationStatus = "pending_runtime_references",
        )
        val v2 = ProgramVersionRow(
            programVersionId = ProgramVersionId("stronglifts@v1"),
            displayName = "StrongLifts 5x5",
            versionLabel = "1",
            authorAttribution = null,
            validationStatus = "activatable",
        )
        return LiftoriumNavState.Library(
            versions = persistentListOf(v1, v2),
            details = mapOf(
                v1.programVersionId to bbbDetail(),
                v2.programVersionId to slDetail(),
            ),
            todays = mapOf(
                v1.programVersionId to bbbToday(),
                v2.programVersionId to slToday(),
            ),
        )
    }

    public fun emptyLibrary(): LiftoriumNavState.Library = LiftoriumNavState.Library(
        versions = persistentListOf(),
        details = emptyMap(),
    )

    private fun bbbDetail() = ProgramDetailUi(
        programVersionId = ProgramVersionId("5-3-1-bbb@v1"),
        displayName = "5/3/1 BBB",
        versionLabel = "1",
        authorAttribution = "Jim Wendler",
        validationStatus = "pending_runtime_references",
        blocks = persistentListOf(
            BlockUi(
                blockId = "main",
                displayName = "Main wave",
                weeks = persistentListOf(
                    WeekUi("w1", "Week 1 · 5/5/5+", persistentListOf("Squat day", "Bench day", "Deadlift day", "Press day")),
                    WeekUi("w2", "Week 2 · 3/3/3+", persistentListOf("Squat day", "Bench day", "Deadlift day", "Press day")),
                    WeekUi("w3", "Week 3 · 5/3/1+", persistentListOf("Squat day", "Bench day", "Deadlift day", "Press day")),
                ),
            ),
        ),
        pendingReferences = persistentListOf(
            PendingReferenceRow("orm-squat", "1RM · Squat", "one_rep_max", WeightUnit.Kg),
            PendingReferenceRow("orm-bench", "1RM · Bench", "one_rep_max", WeightUnit.Kg),
            PendingReferenceRow("orm-deadlift", "1RM · Deadlift", "one_rep_max", WeightUnit.Kg),
            PendingReferenceRow("orm-press", "1RM · OHP", "one_rep_max", WeightUnit.Kg),
        ),
        variantGroups = persistentListOf(
            VariantGroupUi(
                key = WeekVariantGroupKey("main", "w1"),
                baseLabel = "Week 1 main lift focus",
                options = persistentListOf(
                    VariantOptionUi("w1-heavy", "Heavy"),
                    VariantOptionUi("w1-volume", "Volume"),
                ),
            ),
        ),
    )

    private fun slDetail() = ProgramDetailUi(
        programVersionId = ProgramVersionId("stronglifts@v1"),
        displayName = "StrongLifts 5x5",
        versionLabel = "1",
        authorAttribution = null,
        validationStatus = "activatable",
        blocks = persistentListOf(
            BlockUi(
                blockId = "main",
                displayName = "Linear progression",
                weeks = persistentListOf(
                    WeekUi("w1", "Week 1", persistentListOf("Workout A", "Workout B", "Workout A")),
                ),
            ),
        ),
        pendingReferences = persistentListOf(),
        variantGroups = persistentListOf(),
    )

    private fun bbbToday() = TodaySessionUi(
        programRunId = ProgramRunId("run-1"),
        plannedOccurrenceId = "occ-bbb-1",
        programDisplayName = "5/3/1 BBB",
        sessionTitle = "Week 1 · Squat day",
        plannedEpochDay = 0L,
        items = persistentListOf(
            TodayItemUi(
                itemId = "main-squat",
                exerciseName = "Squat",
                role = "main",
                setLines = persistentListOf(
                    "Warm-up · 5 × bar",
                    "Set 1 · 5 reps · 65% 1RM",
                    "Set 2 · 5 reps · 75% 1RM",
                    "Set 3 · 5+ reps · 85% 1RM",
                ),
            ),
            TodayItemUi(
                itemId = "bbb-squat",
                exerciseName = "Squat (BBB)",
                role = "accessory",
                setLines = persistentListOf("5 × 10 · 50% 1RM"),
            ),
            TodayItemUi(
                itemId = "row",
                exerciseName = "DB row",
                role = "accessory",
                setLines = persistentListOf("5 × 10 · RPE 7"),
            ),
        ),
    )

    private fun slToday() = TodaySessionUi(
        programRunId = ProgramRunId("run-2"),
        plannedOccurrenceId = "occ-sl-1",
        programDisplayName = "StrongLifts 5x5",
        sessionTitle = "Week 1 · Workout A",
        plannedEpochDay = 0L,
        items = persistentListOf(
            TodayItemUi("squat", "Squat", "main", persistentListOf("5 × 5")),
            TodayItemUi("bench", "Bench press", "main", persistentListOf("5 × 5")),
            TodayItemUi("row", "Barbell row", "main", persistentListOf("5 × 5")),
        ),
    )

    public fun activeWorkoutJustStarted(): ActiveWorkoutUiState = ActiveWorkoutUiState(
        workoutSessionId = WorkoutSessionId("session-1"),
        programRunId = ProgramRunId("run-1"),
        plannedOccurrenceId = "occ-bbb-1",
        pinnedProgramVersionId = "5-3-1-bbb@v1",
        status = WorkoutSessionStatus.InProgress,
        startedAtEpochMillis = 1_700_000_000_000L,
        lastSavedMutationId = "mut-start",
        title = "5/3/1 BBB",
        subtitle = "Cycle 1 · Week 1 · Squat day",
        exercises = persistentListOf(
            ActiveWorkoutExerciseUi(
                workoutExerciseLogId = "log-squat",
                displayOrder = 0,
                displayName = "Squat",
                isCompleted = false,
                isSkipped = false,
                sets = persistentListOf(
                    pendingWarmup("w1-1", 0, "Warm-up 1", "Bar · 5 reps"),
                    pendingWarmup("w1-2", 1, "Warm-up 2", "60 kg · 5 reps"),
                    pendingWorking("s1-1", 2, "Set 1", "100 kg · 5 reps · 65% 1RM"),
                    pendingWorking("s1-2", 3, "Set 2", "115 kg · 5 reps · 75% 1RM"),
                    pendingWorking("s1-3", 4, "Set 3", "130 kg · 5+ reps · 85% 1RM"),
                ),
            ),
            ActiveWorkoutExerciseUi(
                workoutExerciseLogId = "log-bbb",
                displayOrder = 1,
                displayName = "Squat (BBB)",
                isCompleted = false,
                isSkipped = false,
                sets = persistentListOf(
                    pendingWorking("s2-1", 0, "Set 1", "75 kg · 10 reps · 50% 1RM"),
                    pendingWorking("s2-2", 1, "Set 2", "75 kg · 10 reps · 50% 1RM"),
                    pendingWorking("s2-3", 2, "Set 3", "75 kg · 10 reps · 50% 1RM"),
                ),
            ),
            ActiveWorkoutExerciseUi(
                workoutExerciseLogId = "log-row",
                displayOrder = 2,
                displayName = "DB row",
                isCompleted = false,
                isSkipped = false,
                sets = persistentListOf(
                    pendingWorking("s3-1", 0, "Set 1", "10 reps · RPE 7"),
                    pendingWorking("s3-2", 1, "Set 2", "10 reps · RPE 7"),
                ),
            ),
        ),
    )

    private fun pendingWarmup(id: String, sequence: Int, label: String, summary: String) =
        ActiveWorkoutSetUi(
            actualSetId = ActualSetId(id),
            sequence = sequence,
            role = SetRole.Warmup,
            state = SetState.Pending,
            label = label,
            targetSummary = summary,
            actualSummary = null,
            perSide = false,
        )

    private fun pendingWorking(id: String, sequence: Int, label: String, summary: String) =
        ActiveWorkoutSetUi(
            actualSetId = ActualSetId(id),
            sequence = sequence,
            role = SetRole.Working,
            state = SetState.Pending,
            label = label,
            targetSummary = summary,
            actualSummary = null,
            perSide = false,
        )
}

/**
 * Variant-aware shim used by `MainActivity`. In the `debug` source
 * set, returns the in-memory sample library so the launch-time
 * screen has something to render before real DI lands; the release
 * source set returns an empty library so no sample data ships.
 */
public fun bootstrapState(): LiftoriumNavState.Library =
    SampleStateFactory.libraryWithMixedStatuses()
