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
import kotlin.test.assertTrue

/**
 * VM-level happy-path integration test for the Activate + Start
 * workout buttons. Drives the real
 * [dev.liftorium.app.ui.program.ProgramLibraryViewModel] and
 * [dev.liftorium.app.ui.workout.WorkoutSessionViewModel] (via
 * [IntegrationTestHarness]) against the bundled BBB asset to lock in
 * the contracts that the earlier callback-stubbed
 * `ActivateFlowSemanticsTest` could not enforce.
 *
 * Regression coverage:
 *  * `activate()` returns `ActivateOutcome.Success` with a today UI for
 *    the bundled BBB version.
 *  * `activate()` does NOT mutate `state.value` away from `Loaded` —
 *    the `refresh()` regression that reset the nav host to Library
 *    would surface here.
 *  * `start()` transitions the shell from `NoActive` to `Active` with
 *    the pinned BBB program version and the seeded squat-day session.
 *
 * StrongLifts is mock-only (`SampleStateFactory.libraryWithMixedStatuses`)
 * and has no bundled asset, so this test exercises BBB only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ActivateAndStartWorkoutVmIntegrationTest {

    private lateinit var harness: IntegrationTestHarness

    @Before
    fun setUp() {
        // Robolectric defaults to a paused main looper; without a real
        // Main dispatcher, `viewModelScope.launch` calls (e.g. the
        // init-time refresh()) would never progress and the test would
        // hang. UnconfinedTestDispatcher dispatches eagerly on the
        // current thread so suspend chains drain inside `runBlocking`.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = IntegrationTestHarness(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        harness.close()
        Dispatchers.resetMain()
    }

    @Test
    fun activate_bbb_returnsTodayUi_andLibraryStaysLoaded() = runBlocking {
        harness.bootstrapBundledFixtures()
        val libraryVm = harness.newProgramLibraryViewModel()

        // Wait for the init-time refresh() to land before exercising activate().
        val loaded = libraryVm.state
            .first { it is ProgramLibraryUiState.Loaded } as ProgramLibraryUiState.Loaded
        val bbbId = ProgramVersionId("five-three-one-bbb-v1")
        assertNotNull(
            loaded.library.versions.firstOrNull { it.programVersionId == bbbId },
            "bundled BBB row should appear in library snapshot",
        )

        val outcome = libraryVm.activate(
            programVersionId = bbbId,
            runtimeReferenceValues = emptyMap(),
            chosenWeekVariants = emptyMap(),
        )

        val success = outcome as? ActivateOutcome.Success
        assertNotNull(success, "BBB activation should succeed; got=$outcome")
        assertEquals("5/3/1 BBB", success.today.programDisplayName)
        assertEquals("Squat day", success.today.sessionTitle)

        // Regression net: the previously-fixed `refresh()` bug would have
        // replaced state with a fresh Loaded snapshot, which the nav host
        // (keyed on `remember(initial)`) treats as a new library and
        // resets Detail→Today. Assert state is still the SAME instance.
        assertEquals(
            loaded,
            libraryVm.state.value,
            "activate() must not replace the library state after success",
        )
    }

    @Test
    fun start_bbbSquatDay_transitionsShellToActive() = runBlocking {
        harness.bootstrapBundledFixtures()
        val libraryVm = harness.newProgramLibraryViewModel()
        libraryVm.state.first { it is ProgramLibraryUiState.Loaded }

        val bbbId = ProgramVersionId("five-three-one-bbb-v1")
        val activated = libraryVm.activate(bbbId, emptyMap(), emptyMap())
            as? ActivateOutcome.Success
        assertNotNull(activated, "precondition: BBB must activate before Start workout")

        val workoutVm = harness.newWorkoutSessionViewModel()

        // Drain the initial Loading/NoActive emission so we can wait on
        // the post-start Active transition.
        workoutVm.state.first { it !is ActiveWorkoutShellState.Loading }

        workoutVm.start(
            programRunId = activated.today.programRunId,
            plannedOccurrenceId = activated.today.plannedOccurrenceId,
        )

        val active = workoutVm.state
            .first { it is ActiveWorkoutShellState.Active } as ActiveWorkoutShellState.Active
        assertEquals("five-three-one-bbb-v1", active.ui.pinnedProgramVersionId)
        assertEquals(activated.today.programRunId, active.ui.programRunId)
        assertEquals(activated.today.plannedOccurrenceId, active.ui.plannedOccurrenceId)
        assertEquals("5/3/1 BBB", active.ui.title)
        assertTrue(
            active.ui.exercises.isNotEmpty(),
            "active workout must surface seeded exercises (got=${active.ui.exercises})",
        )
    }
}
