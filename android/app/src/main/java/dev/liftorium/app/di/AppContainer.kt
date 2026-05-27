package dev.liftorium.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.liftorium.app.data.AssetFixtureLoader
import dev.liftorium.app.data.ProgramLibraryRepository
import dev.liftorium.app.ui.program.ProgramLibraryViewModel
import dev.liftorium.app.ui.workout.WorkoutSessionViewModel
import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.LiftoriumDatabaseFactory
import dev.liftorium.data.resource.ProgramResourceLoader
import dev.liftorium.data.run.RoomProgramRunRepository
import dev.liftorium.data.workout.RoomDeviceIdProvider
import dev.liftorium.data.workout.RoomWorkoutLoggingRepository
import dev.liftorium.domain.common.DeviceIdProvider
import dev.liftorium.domain.run.ProgramRunRepository
import dev.liftorium.domain.run.StartProgramRun
import dev.liftorium.domain.workout.StartWorkoutFromOccurrence
import dev.liftorium.domain.workout.WorkoutLoggingRepository

/**
 * Manual DI container for the Slice 1 vertical wiring of
 * `android-workout-logging`.
 *
 * Lives in `dev.liftorium.app.di` (NOT `dev.liftorium.app.ui`) so it
 * may legally import `:data`. The architecture-fitness task
 * `verifyAppUiBoundary` only scans the `ui` subpackage tree.
 *
 * Wires:
 *  * the persistent Room database `liftorium.db`;
 *  * Room-backed [WorkoutLoggingRepository] + [DeviceIdProvider] +
 *    [ProgramRunRepository] implementations from `:data`;
 *  * the [StartWorkoutFromOccurrence] and [StartProgramRun] domain use
 *    cases;
 *  * a [ProgramLibraryRepository] + [AssetFixtureLoader] for bundled
 *    fixture ingestion at app boot;
 *  * ViewModel factories for the workout shell and program library.
 */
public class AppContainer private constructor(
    private val context: Context,
    private val database: LiftoriumDatabase,
    public val timeSource: TimeSource,
    public val idGenerator: IdGenerator,
    public val deviceIdProvider: DeviceIdProvider,
    public val workoutLoggingRepository: WorkoutLoggingRepository,
    public val startWorkoutFromOccurrence: StartWorkoutFromOccurrence,
    public val programResourceLoader: ProgramResourceLoader,
    public val programRunRepository: ProgramRunRepository,
    public val startProgramRun: StartProgramRun,
    public val programLibraryRepository: ProgramLibraryRepository,
) {

    private val assetFixtureLoader: AssetFixtureLoader =
        AssetFixtureLoader(context = context.applicationContext, loader = programResourceLoader)

    /**
     * Idempotently ingest every bundled program fixture under
     * `assets/programs/` into Room. Safe to invoke on every app boot;
     * [ProgramResourceLoader] short-circuits on matching contentHash.
     */
    public suspend fun bootstrapBundledFixtures() {
        assetFixtureLoader.loadAll()
    }

    public fun workoutSessionViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == WorkoutSessionViewModel::class.java) {
                    "workout factory only creates WorkoutSessionViewModel, got $modelClass"
                }
                return WorkoutSessionViewModel(
                    workoutLoggingRepository = workoutLoggingRepository,
                    startWorkoutFromOccurrence = startWorkoutFromOccurrence,
                ) as T
            }
        }

    public fun programLibraryViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ProgramLibraryViewModel::class.java) {
                    "library factory only creates ProgramLibraryViewModel, got $modelClass"
                }
                return ProgramLibraryViewModel(
                    startProgramRun = startProgramRun,
                    librarySnapshotLoader = { programLibraryRepository.snapshot() },
                    todayForRunLoader = { programLibraryRepository.todayForRun(it) },
                ) as T
            }
        }

    public companion object {

        public fun create(context: Context): AppContainer {
            val database = LiftoriumDatabaseFactory.create(context)

            val timeSource = TimeSource.system()
            val idGenerator = IdGenerator.random()
            val deviceIdProvider = RoomDeviceIdProvider(
                dao = database.deviceIdentityDao(),
                timeSource = timeSource,
            )
            val workoutRepository = RoomWorkoutLoggingRepository(
                workoutDao = database.workoutLoggingDao(),
                programRunDao = database.programRunDao(),
                versionDao = database.loadedProgramVersionDao(),
            )
            val startWorkout = StartWorkoutFromOccurrence(
                repository = workoutRepository,
                timeSource = timeSource,
                idGenerator = idGenerator,
                deviceIdProvider = deviceIdProvider,
            )

            val resourceLoader = ProgramResourceLoader(
                database = database,
                dao = database.loadedProgramVersionDao(),
                timeSource = timeSource,
            )
            val runRepository = RoomProgramRunRepository(
                runDao = database.programRunDao(),
                versionDao = database.loadedProgramVersionDao(),
                timeSource = timeSource,
            )
            val startProgramRun = StartProgramRun(
                repository = runRepository,
                timeSource = timeSource,
                idGenerator = idGenerator,
            )
            val libraryRepository = ProgramLibraryRepository(
                versionDao = database.loadedProgramVersionDao(),
                runDao = database.programRunDao(),
            )

            return AppContainer(
                context = context.applicationContext,
                database = database,
                timeSource = timeSource,
                idGenerator = idGenerator,
                deviceIdProvider = deviceIdProvider,
                workoutLoggingRepository = workoutRepository,
                startWorkoutFromOccurrence = startWorkout,
                programResourceLoader = resourceLoader,
                programRunRepository = runRepository,
                startProgramRun = startProgramRun,
                programLibraryRepository = libraryRepository,
            )
        }
    }
}
