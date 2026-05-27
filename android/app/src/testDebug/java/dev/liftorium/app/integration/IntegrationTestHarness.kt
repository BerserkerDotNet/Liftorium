package dev.liftorium.app.integration

import android.content.Context
import androidx.room.Room
import dev.liftorium.app.data.AssetFixtureLoader
import dev.liftorium.app.data.ProgramLibraryRepository
import dev.liftorium.app.ui.program.ProgramLibraryViewModel
import dev.liftorium.app.ui.workout.WorkoutSessionViewModel
import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.resource.ProgramResourceLoader
import dev.liftorium.data.run.RoomProgramRunRepository
import dev.liftorium.data.workout.RoomDeviceIdProvider
import dev.liftorium.data.workout.RoomWorkoutLoggingRepository
import dev.liftorium.domain.run.StartProgramRun
import dev.liftorium.domain.workout.StartWorkoutFromOccurrence

/**
 * Real-wiring test harness for the Slice 1 happy-path integration
 * tests. Mirrors [dev.liftorium.app.di.AppContainer.create] but
 * substitutes an in-memory [LiftoriumDatabase] so the suite can run
 * under Robolectric without writing `liftorium.db` to the test process.
 *
 * Why this exists: stubbing the activate / start-workout callbacks (as
 * `ActivateFlowSemanticsTest` does) cannot catch regressions in the
 * ViewModel logic that sits between the button and the use case. The
 * harness wires the production VMs against the production use cases so
 * a single integration test can prove both `ProgramLibraryViewModel.activate`
 * and `WorkoutSessionViewModel.start` work end-to-end against the real
 * BBB bundled asset.
 *
 * Caveat: StrongLifts 5x5 only exists as in-memory mock data in
 * `SampleStateFactory`; no bundled asset means it cannot be exercised
 * through this harness. Adding a StrongLifts JSON fixture is the only
 * way to extend the matrix to a second program.
 */
internal class IntegrationTestHarness(context: Context) {
    val database: LiftoriumDatabase = Room.inMemoryDatabaseBuilder(
        context,
        LiftoriumDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()

    private val timeSource: TimeSource = TimeSource.system()
    private val idGenerator: IdGenerator = IdGenerator.random()

    val resourceLoader: ProgramResourceLoader = ProgramResourceLoader(
        database = database,
        dao = database.loadedProgramVersionDao(),
        timeSource = timeSource,
    )
    val assetFixtureLoader: AssetFixtureLoader = AssetFixtureLoader(
        context = context,
        loader = resourceLoader,
    )
    val workoutLoggingRepository: RoomWorkoutLoggingRepository = RoomWorkoutLoggingRepository(
        workoutDao = database.workoutLoggingDao(),
        programRunDao = database.programRunDao(),
        versionDao = database.loadedProgramVersionDao(),
    )
    val deviceIdProvider: RoomDeviceIdProvider = RoomDeviceIdProvider(
        dao = database.deviceIdentityDao(),
        timeSource = timeSource,
    )
    val startWorkoutFromOccurrence: StartWorkoutFromOccurrence = StartWorkoutFromOccurrence(
        repository = workoutLoggingRepository,
        timeSource = timeSource,
        idGenerator = idGenerator,
        deviceIdProvider = deviceIdProvider,
    )
    val programRunRepository: RoomProgramRunRepository = RoomProgramRunRepository(
        runDao = database.programRunDao(),
        versionDao = database.loadedProgramVersionDao(),
        timeSource = timeSource,
    )
    val startProgramRun: StartProgramRun = StartProgramRun(
        repository = programRunRepository,
        timeSource = timeSource,
        idGenerator = idGenerator,
    )
    val programLibraryRepository: ProgramLibraryRepository = ProgramLibraryRepository(
        versionDao = database.loadedProgramVersionDao(),
        runDao = database.programRunDao(),
    )

    suspend fun bootstrapBundledFixtures() {
        assetFixtureLoader.loadAll()
    }

    fun newProgramLibraryViewModel(): ProgramLibraryViewModel = ProgramLibraryViewModel(
        startProgramRun = startProgramRun,
        librarySnapshotLoader = { programLibraryRepository.snapshot() },
        todayForRunLoader = { programLibraryRepository.todayForRun(it) },
    )

    fun newWorkoutSessionViewModel(): WorkoutSessionViewModel = WorkoutSessionViewModel(
        workoutLoggingRepository = workoutLoggingRepository,
        startWorkoutFromOccurrence = startWorkoutFromOccurrence,
    )

    fun close() {
        database.close()
    }
}
