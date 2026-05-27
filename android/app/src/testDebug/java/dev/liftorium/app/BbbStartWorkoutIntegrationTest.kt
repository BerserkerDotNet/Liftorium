package dev.liftorium.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.data.AssetFixtureLoader
import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.resource.ProgramResourceLoader
import dev.liftorium.data.run.RoomProgramRunRepository
import dev.liftorium.data.workout.RoomDeviceIdProvider
import dev.liftorium.data.workout.RoomWorkoutLoggingRepository
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.StartProgramRun
import dev.liftorium.domain.run.StartProgramRunCommand
import dev.liftorium.domain.run.StartProgramRunResult
import dev.liftorium.domain.workout.StartWorkoutCommand
import dev.liftorium.domain.workout.StartWorkoutFromOccurrence
import dev.liftorium.domain.workout.StartWorkoutResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration test that exercises the BBB squat day from
 * bundled-asset bootstrap through StartProgramRun and
 * StartWorkoutFromOccurrence. Asserts the snapshot weights that the
 * Slice 1 runtime evidence captured visually on the emulator:
 * working sets at 205 / 235 / 270 lb (65/75/85% × 315 lb 1RM),
 * and BBB back-off sets at 160 lb (50% × 315 lb).
 *
 * Why this test sits in `:app` and not `:data`: it has to prove the
 * full vertical — asset directory + AssetFixtureLoader + Room +
 * resource loader + StartProgramRun + StartWorkoutFromOccurrence all
 * agree on the squat day's first-week prescription. A `:data`-only
 * test couldn't read the bundled asset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BbbStartWorkoutIntegrationTest {

    private lateinit var db: LiftoriumDatabase

    @Before
    fun setUp() {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LiftoriumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bbbSquatDay_loadsAt205_235_270_workingAnd160BackOff() = runBlocking {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val timeSource = TimeSource.system()
        val idGenerator = IdGenerator.random()

        val resourceLoader = ProgramResourceLoader(
            database = db,
            dao = db.loadedProgramVersionDao(),
            timeSource = timeSource,
        )
        val assetLoader = AssetFixtureLoader(context = context, loader = resourceLoader)
        assetLoader.loadAll()

        // Blocking-finding: AssetFixtureLoader swallows exceptions per
        // file. Assert the BBB row really exists before continuing.
        val bbbVersionId = ProgramVersionId("five-three-one-bbb-v1")
        val bbbRow = db.loadedProgramVersionDao().findById(bbbVersionId.value)
        assertNotNull(bbbRow) { "BBB version row should be loaded from bundled asset" }

        val runRepo = RoomProgramRunRepository(
            runDao = db.programRunDao(),
            versionDao = db.loadedProgramVersionDao(),
            timeSource = timeSource,
        )
        val startProgramRun = StartProgramRun(
            repository = runRepo,
            timeSource = timeSource,
            idGenerator = idGenerator,
        )
        val workoutRepo = RoomWorkoutLoggingRepository(
            workoutDao = db.workoutLoggingDao(),
            programRunDao = db.programRunDao(),
            versionDao = db.loadedProgramVersionDao(),
        )
        val startWorkout = StartWorkoutFromOccurrence(
            repository = workoutRepo,
            timeSource = timeSource,
            idGenerator = idGenerator,
            deviceIdProvider = RoomDeviceIdProvider(
                dao = db.deviceIdentityDao(),
                timeSource = timeSource,
            ),
        )

        val runResult = startProgramRun(
            StartProgramRunCommand(
                programVersionId = bbbVersionId,
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = emptyMap(),
            ),
        )
        val runSuccess = assertSuccess(runResult)
        val squatOccurrence = runSuccess.seededOccurrences
            .first { it.sessionId == "w1-d1" }

        val workoutResult = startWorkout(
            StartWorkoutCommand(
                programRunId = runSuccess.run.programRunId,
                plannedOccurrenceId = squatOccurrence.occurrenceId,
            ),
        )
        val workoutSuccess = assertWorkoutSuccess(workoutResult)

        // Snapshots carry the resolved displayLoad per actual set.
        val snapshots = workoutSuccess.snapshots
        // Squat day in BBB week 1 has 3 working sets + 2 back-off sets in
        // the bundled asset. Assert exact shape so extra/missing sets
        // would surface as a clear failure.
        assertEquals(5, snapshots.size, "BBB squat day should emit exactly 5 prescription snapshots")

        // Every snapshot for the first squat day must reference the
        // one-rep-max type, with the lb unit (BBB uses lb throughout).
        for (snap in snapshots) {
            assertEquals("one_rep_max", snap.referenceType, "all squat-day snapshots referenceType")
            assertEquals(WeightUnit.Lb, snap.displayLoadUnit, "all squat-day snapshots displayLoadUnit")
            assertEquals(315.0, snap.referenceValue!!, 1e-9)
        }

        val percentMultiset = snapshots.mapNotNull { it.percent?.toInt() }.sorted()
        assertEquals(listOf(50, 50, 65, 75, 85), percentMultiset, "exact squat-day percents")

        val workingLoads = snapshots
            .filter { it.percent?.toInt() in setOf(65, 75, 85) }
            .mapNotNull { it.displayLoad }
            .sorted()
        assertEquals(listOf(205.0, 235.0, 270.0), workingLoads, "working-set displayLoads")

        val backOffLoads = snapshots
            .filter { it.percent?.toInt() == 50 }
            .mapNotNull { it.displayLoad }
        assertEquals(2, backOffLoads.size, "BBB squat day has 2 back-off sets in the bundled asset")
        assertTrue(
            backOffLoads.all { it == 160.0 },
            "every BBB back-off set should round to 160 lb, got=$backOffLoads",
        )
    }

    private fun assertSuccess(result: StartProgramRunResult): StartProgramRunResult.Success {
        if (result is StartProgramRunResult.Success) return result
        error("StartProgramRun failed: $result")
    }

    private fun assertWorkoutSuccess(result: StartWorkoutResult): StartWorkoutResult.Success {
        if (result is StartWorkoutResult.Success) return result
        error("StartWorkoutFromOccurrence failed: $result")
    }
}

