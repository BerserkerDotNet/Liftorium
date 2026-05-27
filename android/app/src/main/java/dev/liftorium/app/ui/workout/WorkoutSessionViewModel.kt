package dev.liftorium.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.workout.StartWorkoutCommand
import dev.liftorium.domain.workout.StartWorkoutFromOccurrence
import dev.liftorium.domain.workout.StartWorkoutResult
import dev.liftorium.domain.workout.WorkoutLoggingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shell ViewModel for the workout-logging surface.
 *
 * Wires:
 *  * [WorkoutLoggingRepository.observeOpenSession] — long-lived Flow
 *    that drives the [ActiveWorkoutShellState]. Used for both
 *    in-process updates AND cold-start recovery: when the process is
 *    killed and reopened, the Flow's first emission is the persisted
 *    open session, which routes the shell to [ActiveWorkoutShellState.Active]
 *    without any extra recovery dance.
 *  * [StartWorkoutFromOccurrence] — the use case fired when the user
 *    taps Start workout on `TodaySessionScreen`.
 *
 * Slices 2/3 will extend this ViewModel with `completeSet` /
 * `finishSession` actions; for Slice 1 the screen is read-only.
 */
public class WorkoutSessionViewModel(
    private val workoutLoggingRepository: WorkoutLoggingRepository,
    private val startWorkoutFromOccurrence: StartWorkoutFromOccurrence,
) : ViewModel() {

    private val errorState = MutableStateFlow<StartWorkoutErrorUi?>(null)

    public val state: StateFlow<ActiveWorkoutShellState> =
        combine(
            workoutLoggingRepository.observeOpenSession(),
            errorState.asStateFlow(),
        ) { aggregate, pendingError ->
            if (aggregate != null) {
                ActiveWorkoutShellState.Active(aggregate.toUiState())
            } else {
                ActiveWorkoutShellState.NoActive(pendingError = pendingError)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ActiveWorkoutShellState.Loading,
        )

    public fun start(programRunId: ProgramRunId, plannedOccurrenceId: String) {
        errorState.value = null
        viewModelScope.launch {
            val result = startWorkoutFromOccurrence(
                StartWorkoutCommand(
                    programRunId = programRunId,
                    plannedOccurrenceId = plannedOccurrenceId,
                ),
            )
            when (result) {
                is StartWorkoutResult.Success -> {
                    // Active state will be emitted by observeOpenSession; nothing else to do.
                }
                StartWorkoutResult.Failure.AlreadyActiveSession ->
                    errorState.value = StartWorkoutErrorUi.AlreadyActiveSession
                is StartWorkoutResult.Failure.UnknownOccurrence ->
                    errorState.value = StartWorkoutErrorUi.UnknownOccurrence(result.plannedOccurrenceId)
                is StartWorkoutResult.Failure.EmptyPlan ->
                    errorState.value = StartWorkoutErrorUi.EmptyPlan(result.plannedOccurrenceId)
            }
        }
    }

    public fun dismissStartError() {
        errorState.value = null
    }

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
