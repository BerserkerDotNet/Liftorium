package dev.liftorium.app.integration

import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.app.ui.workout.ActiveWorkoutShellState
import dev.liftorium.app.ui.workout.StartWorkoutErrorUi
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
 * VM-level integration coverage for the activation and start-workout
 * guard rails that the happy-path tests do not exercise:
 *  * second activate while a run is active → AlreadyActiveRun failure.
 *  * second start while a session is active → invariant: existing
 *    session is preserved (no swap).
 *  * start with an unknown occurrence id → NoActive shell with
 *    UnknownOccurrence error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ActivationAndStartGuardsTest {

    private val bbbId = ProgramVersionId("five-three-one-bbb-v1")
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
    fun activate_twice_returnsAlreadyActiveRunFailure() = runBlocking {
        harness.bootstrapBundledFixtures()
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val first = vm.activate(bbbId, emptyMap(), emptyMap())
        assertTrue(first is ActivateOutcome.Success, "first activation must succeed; got=$first")

        val second = vm.activate(bbbId, emptyMap(), emptyMap())
        val failure = second as? ActivateOutcome.Failure
        assertNotNull(failure, "second activation must fail; got=$second")
        assertEquals("Another program run is already active", failure.message)
    }

    @Test
    fun start_unknownOccurrence_emitsUnknownOccurrenceError() = runBlocking {
        harness.bootstrapBundledFixtures()
        val libraryVm = harness.newProgramLibraryViewModel()
        libraryVm.state.first { it is ProgramLibraryUiState.Loaded }
        val activated = libraryVm.activate(bbbId, emptyMap(), emptyMap())
            as ActivateOutcome.Success

        val workoutVm = harness.newWorkoutSessionViewModel()
        workoutVm.state.first { it is ActiveWorkoutShellState.NoActive }

        workoutVm.start(
            programRunId = activated.today.programRunId,
            plannedOccurrenceId = "occurrence-does-not-exist",
        )

        val noActive = workoutVm.state
            .first {
                it is ActiveWorkoutShellState.NoActive &&
                    it.pendingError is StartWorkoutErrorUi.UnknownOccurrence
            } as ActiveWorkoutShellState.NoActive
        val error = noActive.pendingError as StartWorkoutErrorUi.UnknownOccurrence
        assertEquals("occurrence-does-not-exist", error.plannedOccurrenceId)
    }

    @Test
    fun start_twice_preservesFirstActiveSession() = runBlocking {
        harness.bootstrapBundledFixtures()
        val libraryVm = harness.newProgramLibraryViewModel()
        libraryVm.state.first { it is ProgramLibraryUiState.Loaded }
        val activated = libraryVm.activate(bbbId, emptyMap(), emptyMap())
            as ActivateOutcome.Success
        val firstOccurrenceId = activated.today.plannedOccurrenceId

        val workoutVm = harness.newWorkoutSessionViewModel()
        workoutVm.state.first { it is ActiveWorkoutShellState.NoActive }
        workoutVm.start(activated.today.programRunId, firstOccurrenceId)
        val firstActive = workoutVm.state
            .first { it is ActiveWorkoutShellState.Active } as ActiveWorkoutShellState.Active
        assertEquals(firstOccurrenceId, firstActive.ui.plannedOccurrenceId)

        // Pick a different scheduled occurrence to attempt a swap.
        val secondOccurrenceId = harness.database.programRunDao()
            .listOccurrences(activated.today.programRunId.value)
            .first { it.occurrenceId != firstOccurrenceId }
            .occurrenceId

        workoutVm.start(activated.today.programRunId, secondOccurrenceId)
        // Active session in shell must remain the FIRST one — the
        // already-active guard in `StartWorkoutFromOccurrence` blocks
        // the swap. We tolerate the second start being a no-op as long
        // as the shell does not switch sessions.
        val afterSecond = workoutVm.state.value
        assertTrue(afterSecond is ActiveWorkoutShellState.Active, "shell should still be Active; got=$afterSecond")
        assertEquals(
            firstOccurrenceId,
            afterSecond.ui.plannedOccurrenceId,
            "AlreadyActiveSession guard must keep the original session",
        )
    }
}
