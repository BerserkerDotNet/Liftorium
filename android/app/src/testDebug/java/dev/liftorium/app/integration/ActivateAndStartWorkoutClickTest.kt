package dev.liftorium.app.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.LiftoriumNavHost
import dev.liftorium.app.ui.LiftoriumNavState
import dev.liftorium.app.ui.NavActivateResult
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryViewModel
import dev.liftorium.app.ui.theme.LiftoriumTheme
import dev.liftorium.app.ui.workout.ActiveWorkoutShellState
import dev.liftorium.app.ui.workout.WorkoutSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Compose click-level integration test for the Activate + Start
 * workout buttons. Mirrors `ActivateFlowSemanticsTest` but wires the
 * NavHost callbacks to the REAL [ProgramLibraryViewModel] /
 * [WorkoutSessionViewModel] over an in-memory Room database
 * (via [IntegrationTestHarness]) so a regression in the VM layer
 * surfaces through the button click instead of being papered over by
 * a stub lambda.
 *
 * BBB is the only program with a bundled JSON asset, so this is the
 * only program exercisable through the live pipeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivateAndStartWorkoutClickTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var harness: IntegrationTestHarness
    private lateinit var librarySnapshot: LiftoriumNavState.Library
    private lateinit var libraryVm: ProgramLibraryViewModel
    private lateinit var workoutVm: WorkoutSessionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val collectorScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private var collectorJob: Job? = null

    @Before
    fun setUp() = runBlocking {
        // See VM-level test for rationale; UnconfinedTestDispatcher
        // makes viewModelScope launches drain eagerly under Robolectric.
        Dispatchers.setMain(testDispatcher)
        harness = IntegrationTestHarness(ApplicationProvider.getApplicationContext())
        harness.bootstrapBundledFixtures()
        librarySnapshot = harness.programLibraryRepository.snapshot()
        libraryVm = harness.newProgramLibraryViewModel()
        workoutVm = harness.newWorkoutSessionViewModel()
        // Keep WorkoutSessionViewModel.state subscribed throughout the
        // test. SharingStarted.WhileSubscribed never starts the upstream
        // observeOpenSession() flow without an active subscriber, so
        // state.value would remain Loading.
        collectorJob = collectorScope.launch { workoutVm.state.collect { } }
    }

    @After
    fun tearDown() {
        collectorJob?.cancel()
        collectorScope.cancel()
        harness.close()
        Dispatchers.resetMain()
    }

    @Test
    fun activateButton_routesDetailToToday_throughRealViewModel() {
        composeTestRule.setContent {
            LiftoriumTheme {
                LiftoriumNavHost(
                    initial = librarySnapshot,
                    onActivate = { vid, refs, variants ->
                        when (val outcome = libraryVm.activate(vid, refs, variants)) {
                            is ActivateOutcome.Success -> NavActivateResult.Success(outcome.today)
                            is ActivateOutcome.Failure -> NavActivateResult.Failure(outcome.message)
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag("version-row-five-three-one-bbb-v1").performClick()
        composeTestRule.onNodeWithTag("activate-button").performClick()

        // The Today screen renders the session display name in the
        // top app bar; assert that to prove Detail→Today fired.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Squat day").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Squat day").assertIsDisplayed()
    }

    @Test
    fun startWorkoutButton_firesRealViewModel_andShellGoesActive() {        composeTestRule.setContent {
            LiftoriumTheme {
                LiftoriumNavHost(
                    initial = librarySnapshot,
                    onActivate = { vid, refs, variants ->
                        when (val outcome = libraryVm.activate(vid, refs, variants)) {
                            is ActivateOutcome.Success -> NavActivateResult.Success(outcome.today)
                            is ActivateOutcome.Failure -> NavActivateResult.Failure(outcome.message)
                        }
                    },
                    onStartWorkout = { runId, occId -> workoutVm.start(runId, occId) },
                )
            }
        }

        composeTestRule.onNodeWithTag("version-row-five-three-one-bbb-v1").performClick()
        composeTestRule.onNodeWithTag("activate-button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Start workout").performClick()

        // Start workout is a fire-and-forget callback; verify the
        // workout VM observed the Active emission through observeOpenSession.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            workoutVm.state.value is ActiveWorkoutShellState.Active
        }
        val active = workoutVm.state.value as ActiveWorkoutShellState.Active
        assertEquals("five-three-one-bbb-v1", active.ui.pinnedProgramVersionId)
        assertEquals("5/3/1 BBB", active.ui.title)
    }

    @Test
    fun backNavigation_today_goesDirectlyToLibrary() {
        composeTestRule.setContent {
            LiftoriumTheme {
                LiftoriumNavHost(
                    initial = librarySnapshot,
                    onActivate = { vid, refs, variants ->
                        when (val outcome = libraryVm.activate(vid, refs, variants)) {
                            is ActivateOutcome.Success -> NavActivateResult.Success(outcome.today)
                            is ActivateOutcome.Failure -> NavActivateResult.Failure(outcome.message)
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag("version-row-five-three-one-bbb-v1").performClick()
        composeTestRule.onNodeWithTag("activate-button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Squat day").fetchSemanticsNodes().isNotEmpty()
        }

        // Back from Today is wired to `detail.previous`, which is the
        // Library (Detail itself is skipped on the back stack — pulling
        // back to a pre-activation Detail card after a successful
        // activate has no product value). Pressing Back must surface
        // the library row again in one step.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag("version-row-five-three-one-bbb-v1")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("version-row-five-three-one-bbb-v1").assertIsDisplayed()
    }

    @Test
    fun backNavigation_detail_returnsToLibrary() {
        composeTestRule.setContent {
            LiftoriumTheme {
                LiftoriumNavHost(initial = librarySnapshot)
            }
        }

        composeTestRule.onNodeWithTag("version-row-five-three-one-bbb-v1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("activate-button").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag("version-row-five-three-one-bbb-v1")
                .fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("activate-button").fetchSemanticsNodes().isEmpty()
        }
    }
}
