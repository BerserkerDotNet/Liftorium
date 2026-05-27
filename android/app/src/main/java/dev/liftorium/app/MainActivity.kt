package dev.liftorium.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.liftorium.app.di.AppContainer
import dev.liftorium.app.ui.LiftoriumNavHost
import dev.liftorium.app.ui.LiftoriumNavState
import dev.liftorium.app.ui.NavActivateResult
import dev.liftorium.app.ui.bootstrapState
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.app.ui.program.ProgramLibraryViewModel
import dev.liftorium.app.ui.theme.LiftoriumTheme
import dev.liftorium.app.ui.workout.ActiveWorkoutScreen
import dev.liftorium.app.ui.workout.ActiveWorkoutShellState
import dev.liftorium.app.ui.workout.StartWorkoutErrorUi
import dev.liftorium.app.ui.workout.WorkoutSessionViewModel
import kotlinx.collections.immutable.persistentListOf

/**
 * App entry point.
 *
 * Resolves the singleton [AppContainer] from [LiftoriumApplication] and
 * hands it to [LiftoriumApp]. The shell collects
 * [WorkoutSessionViewModel.state] and [ProgramLibraryViewModel.state],
 * routing the user between the Room-backed library/activation flow and
 * the active workout screen. Errors (start-workout failures and
 * activation failures) surface as Snackbars over the Scaffold.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LiftoriumApplication).container
        setContent {
            LiftoriumApp(container = container)
        }
    }
}

@Composable
fun LiftoriumApp(container: AppContainer) {
    LiftoriumTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        var snackbarMessage by remember { mutableStateOf<String?>(null) }
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                val workoutVm: WorkoutSessionViewModel = viewModel(
                    factory = container.workoutSessionViewModelFactory(),
                )
                val libraryVm: ProgramLibraryViewModel = viewModel(
                    factory = container.programLibraryViewModelFactory(),
                )
                val shellState by workoutVm.state.collectAsStateWithLifecycle()
                val libraryState by libraryVm.state.collectAsStateWithLifecycle()

                val pendingStartError = (shellState as? ActiveWorkoutShellState.NoActive)?.pendingError
                LaunchedEffect(pendingStartError) {
                    if (pendingStartError != null) {
                        snackbarHostState.showSnackbar(pendingStartError.toUserMessage())
                        workoutVm.dismissStartError()
                    }
                }
                LaunchedEffect(snackbarMessage) {
                    val msg = snackbarMessage
                    if (msg != null) {
                        snackbarHostState.showSnackbar(msg)
                        snackbarMessage = null
                    }
                }

                when (val s = shellState) {
                    ActiveWorkoutShellState.Loading -> CenteredProgress()
                    is ActiveWorkoutShellState.NoActive -> {
                        val resolvedInitial = when (val ls = libraryState) {
                            ProgramLibraryUiState.Loading ->
                                LiftoriumNavState.Library(persistentListOf(), emptyMap(), emptyMap())
                            is ProgramLibraryUiState.Loaded -> ls.library
                        }
                        LiftoriumNavHost(
                            initial = resolvedInitial,
                            onStartWorkout = { runId, occId -> workoutVm.start(runId, occId) },
                            onActivate = { versionId, refs, variants ->
                                when (val outcome = libraryVm.activate(versionId, refs, variants)) {
                                    is ActivateOutcome.Success -> NavActivateResult.Success(outcome.today)
                                    is ActivateOutcome.Failure -> NavActivateResult.Failure(outcome.message)
                                }
                            },
                            onActivationError = { message -> snackbarMessage = message },
                        )
                    }
                    is ActiveWorkoutShellState.Active -> ActiveWorkoutScreen(state = s.ui)
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun StartWorkoutErrorUi.toUserMessage(): String = when (this) {
    StartWorkoutErrorUi.AlreadyActiveSession -> "Another workout session is already in progress."
    is StartWorkoutErrorUi.UnknownOccurrence -> "Could not find scheduled session $plannedOccurrenceId."
    is StartWorkoutErrorUi.EmptyPlan -> "The session plan is empty."
    is StartWorkoutErrorUi.Unexpected -> "Could not start workout: $message"
}

/**
 * No-container overload used by Compose UI tests and Paparazzi
 * snapshots of the app shell. Renders the program-library nav host
 * against the [bootstrapState] shim and skips the workout-logging
 * subscription (no Room database is constructed in the test process).
 */
@Composable
fun LiftoriumApp() {
    LiftoriumTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LiftoriumNavHost(initial = bootstrapState())
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiftoriumAppPreview() {
    LiftoriumTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LiftoriumNavHost(initial = bootstrapState())
        }
    }
}

