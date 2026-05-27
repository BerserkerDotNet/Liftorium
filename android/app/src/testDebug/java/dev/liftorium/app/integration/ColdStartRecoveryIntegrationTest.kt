package dev.liftorium.app.integration

import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.app.ui.workout.ActiveWorkoutShellState
import dev.liftorium.domain.common.ProgramVersionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Cold-start recovery proof: when the process is reborn against a
 * Room database that already contains an open workout session, a
 * freshly-constructed [WorkoutSessionViewModel] must surface that
 * session as `Active` via `observeOpenSession` — without any extra
 * recovery dance. A regression here would silently drop the user's
 * in-flight workout on process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ColdStartRecoveryIntegrationTest {

    private lateinit var harness: IntegrationTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = IntegrationTestHarness(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        harness.close()
        Dispatchers.resetMain()
    }

    @Test
    fun freshVm_surfacesPersistedOpenSession_asActive() = runBlocking<Unit> {
        harness.bootstrapBundledFixtures()
        val libraryVm = harness.newProgramLibraryViewModel()
        libraryVm.state.first { it is ProgramLibraryUiState.Loaded }
        val activated = libraryVm.activate(
            ProgramVersionId("five-three-one-bbb-v1"), emptyMap(), emptyMap(),
        ) as ActivateOutcome.Success

        // Start the workout through the first VM, then discard it.
        run {
            val firstVm = harness.newWorkoutSessionViewModel()
            firstVm.state.first { it is ActiveWorkoutShellState.NoActive }
            firstVm.start(activated.today.programRunId, activated.today.plannedOccurrenceId)
            firstVm.state.first { it is ActiveWorkoutShellState.Active }
        }

        // Simulate process death by constructing a brand-new VM against
        // the same DB. observeOpenSession should rehydrate the session.
        val rebornVm = harness.newWorkoutSessionViewModel()
        val active = rebornVm.state
            .first { it is ActiveWorkoutShellState.Active } as ActiveWorkoutShellState.Active

        assertEquals(activated.today.plannedOccurrenceId, active.ui.plannedOccurrenceId)
        assertEquals("five-three-one-bbb-v1", active.ui.pinnedProgramVersionId)
        assertNotNull(active.ui.workoutSessionId, "reborn session must carry a workout session id")
    }
}
