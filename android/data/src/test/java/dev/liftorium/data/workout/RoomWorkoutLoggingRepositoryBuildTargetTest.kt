package dev.liftorium.data.workout

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression tests for the weight-resolution rewrite in
 * [RoomWorkoutLoggingRepository.loadWorkoutPlan] / `buildTarget`.
 *
 * Each test seeds a minimal one-set program version plus a program run
 * with one schedule occurrence, then asserts the
 * [dev.liftorium.domain.workout.WorkoutPlanTarget] returned by
 * `loadWorkoutPlan`. Hits every bug the rewrite fixed:
 *
 *  * percent ÷ 100 (schema stores percent as 0-100 integer);
 *  * conjunctive target rows (percent + RPE companion);
 *  * supplied-ref fallback (BBB case where `program_run_reference_value`
 *    has no row but `LoadedRequiredReferenceEntity.value` does);
 *  * run-scoped override supersedes supplied required reference;
 *  * rounding precedence (target > programDefaults > per-unit fallback);
 *  * referenceType pass-through;
 *  * missing-reference → null displayLoad.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class RoomWorkoutLoggingRepositoryBuildTargetTest {

    private lateinit var db: LiftoriumDatabase
    private lateinit var repo: RoomWorkoutLoggingRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftoriumDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repo = RoomWorkoutLoggingRepository(
            workoutDao = db.workoutLoggingDao(),
            programRunDao = db.programRunDao(),
            versionDao = db.loadedProgramVersionDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bbb65PercentOf315Lb_suppliedRequiredReference_roundsTo205Lb() = runBlocking {
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(percentTarget(percent = 65.0, roundingIncrement = 5.0, roundingUnit = "lb")),
                requiredReferences = listOf(requiredRef(supplied = true, value = 315.0, unit = "lb")),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        // Regression: percent must be divided by 100 before multiplying.
        assertEquals(204.75, target.calculatedRawLoad!!, 1e-9)
        // Regression: MROUND must apply with the target's 5 lb increment.
        assertEquals(205.0, target.displayLoad!!, 1e-9)
        // Regression: referenceType must pass through from the required reference.
        assertEquals("one_rep_max", target.referenceType)
        assertEquals(315.0, target.referenceValue!!, 1e-9)
        assertEquals(WeightUnit.Lb, target.referenceUnit)
        assertEquals(WeightUnit.Lb, target.displayLoadUnit)
    }

    @Test
    fun conjunctivePercentAndRpeRows_bothFieldsSurfaceOnPlanTarget() = runBlocking {
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(
                    percentTarget(targetIndex = 0, percent = 75.0, rpeTarget = null),
                    rpeCompanion(targetIndex = 1, rpeTarget = 8.5),
                ),
                requiredReferences = listOf(requiredRef(supplied = true, value = 315.0, unit = "lb")),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        // Regression: firstNotNullOfOrNull must merge across sibling rows
        // so the RPE companion is not dropped when percent is present.
        assertEquals(75.0, target.percent!!, 1e-9)
        assertEquals(8.5, target.targetRpe!!, 1e-9)
        assertEquals(235.0, target.displayLoad!!, 1e-9)
    }

    @Test
    fun runScopedReferenceValue_supersedesSuppliedRequiredReference() = runBlocking {
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(percentTarget(percent = 65.0, roundingIncrement = 5.0, roundingUnit = "lb")),
                requiredReferences = listOf(requiredRef(supplied = true, value = 315.0, unit = "lb")),
                runScopedReferences = listOf("orm-ex-1" to (400.0 to "lb")),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertEquals(400.0, target.referenceValue!!, 1e-9)
        assertEquals(260.0, target.calculatedRawLoad!!, 1e-9)
        assertEquals(260.0, target.displayLoad!!, 1e-9)
    }

    @Test
    fun programDefaultsRoundingOverride_appliesWhenTargetRoundingIsMissing() = runBlocking {
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(
                    percentTarget(
                        percent = 76.0,
                        roundingIncrement = null,
                        roundingUnit = null,
                    ),
                ),
                requiredReferences = listOf(requiredRef(supplied = true, value = 100.0, unit = "kg")),
                // Choose an increment that DIVERGES from the per-unit
                // fallback (2.5 kg) so the assertion proves the default
                // override branch actually fired. raw 76 kg → mround(76, 10) = 80.
                programDefaultsJson = """{"roundingIncrement":10,"roundingUnit":"kg"}""",
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertEquals(76.0, target.calculatedRawLoad!!, 1e-9)
        assertEquals(80.0, target.displayLoad!!, 1e-9)
        assertEquals(WeightUnit.Kg, target.displayLoadUnit)
    }

    @Test
    fun targetRoundingOverrides_winOverProgramDefaults() = runBlocking {
        // Target says "5 lb" while programDefaults says "2.5 kg". Refunit is lb.
        // 80% of 315 lb = 252 lb → at 5 lb increment, rounds to 250.
        // (At 2.5 kg ≈ 5.5 lb increment, it would round to 253ish; the
        // assertion proves the target took precedence.)
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(
                    percentTarget(
                        percent = 80.0,
                        roundingIncrement = 5.0,
                        roundingUnit = "lb",
                    ),
                ),
                requiredReferences = listOf(requiredRef(supplied = true, value = 315.0, unit = "lb")),
                programDefaultsJson = """{"roundingIncrement":2.5,"roundingUnit":"kg"}""",
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertEquals(252.0, target.calculatedRawLoad!!, 1e-9)
        assertEquals(250.0, target.displayLoad!!, 1e-9)
    }

    @Test
    fun perUnitFallback_roundsKgAt2_5() = runBlocking {
        // Reference unit kg, no target rounding, no program defaults.
        // 76% of 100 kg = 76 → MROUND(76, 2.5) = 75.
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(
                    percentTarget(
                        percent = 76.0,
                        roundingIncrement = null,
                        roundingUnit = null,
                    ),
                ),
                requiredReferences = listOf(requiredRef(supplied = true, value = 100.0, unit = "kg")),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertEquals(75.0, target.displayLoad!!, 1e-9)
        assertEquals(WeightUnit.Kg, target.displayLoadUnit)
    }

    @Test
    fun perUnitFallback_roundsLbAt5() = runBlocking {
        // 65% of 317 lb = 206.05 → MROUND(206.05, 5) = 205.
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(
                    percentTarget(
                        percent = 65.0,
                        roundingIncrement = null,
                        roundingUnit = null,
                    ),
                ),
                requiredReferences = listOf(requiredRef(supplied = true, value = 317.0, unit = "lb")),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertEquals(205.0, target.displayLoad!!, 1e-9)
    }

    @Test
    fun missingReference_calculatedRawAndDisplayFieldsAreAllNull() = runBlocking {
        // Target points at a referenceId that doesn't exist in either
        // requiredReferences or program_run_reference_value.
        seedBuildTargetScenario(
            db,
            BuildTargetSeed(
                targets = listOf(percentTarget(percent = 65.0, referenceId = "ghost-ref")),
                requiredReferences = emptyList(),
            ),
        )

        val plan = repo.loadWorkoutPlan(ProgramRunId(FIX_RUN_ID), FIX_OCCURRENCE_ID)
        assertNotNull(plan)
        val target = plan.exercises.single().sets.single().target

        assertNull(target.calculatedRawLoad)
        assertNull(target.displayLoad)
        assertNull(target.referenceValue)
        assertNull(target.referenceUnit)
        assertNull(target.referenceType)
    }
}
