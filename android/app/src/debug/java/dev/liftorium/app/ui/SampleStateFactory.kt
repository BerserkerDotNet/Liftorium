package dev.liftorium.app.ui

import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.WeekVariantGroupKey
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
            PendingReferenceRow("tm-squat", "Training max · Squat", "training_max", WeightUnit.Kg),
            PendingReferenceRow("tm-bench", "Training max · Bench", "training_max", WeightUnit.Kg),
            PendingReferenceRow("tm-deadlift", "Training max · Deadlift", "training_max", WeightUnit.Kg),
            PendingReferenceRow("tm-press", "Training max · OHP", "training_max", WeightUnit.Kg),
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
                    "Set 1 · 5 reps · 65% TM",
                    "Set 2 · 5 reps · 75% TM",
                    "Set 3 · 5+ reps · 85% TM",
                ),
            ),
            TodayItemUi(
                itemId = "bbb-squat",
                exerciseName = "Squat (BBB)",
                role = "accessory",
                setLines = persistentListOf("5 × 10 · 50% TM"),
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
        programDisplayName = "StrongLifts 5x5",
        sessionTitle = "Week 1 · Workout A",
        plannedEpochDay = 0L,
        items = persistentListOf(
            TodayItemUi("squat", "Squat", "main", persistentListOf("5 × 5")),
            TodayItemUi("bench", "Bench press", "main", persistentListOf("5 × 5")),
            TodayItemUi("row", "Barbell row", "main", persistentListOf("5 × 5")),
        ),
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
