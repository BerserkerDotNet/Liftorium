package dev.liftorium.app.ui.program

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.liftorium.app.ui.LiftoriumNavState
import dev.liftorium.app.ui.TodaySessionUi
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.RuntimeReferenceValue
import dev.liftorium.domain.run.StartProgramRun
import dev.liftorium.domain.run.StartProgramRunCommand
import dev.liftorium.domain.run.StartProgramRunResult
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Library ViewModel: exposes the Room-backed program library + the
 * activate action that wraps [StartProgramRun]. The activate suspend
 * returns a typed [ActivateOutcome] so the Compose nav host can
 * transition straight to Today on Success or surface an error message
 * on Failure.
 *
 * Library content is loaded once on init via the injected
 * [librarySnapshotLoader]; subsequent activations refresh the snapshot
 * so the "todays" preview map stays in sync with any seeded
 * occurrences.
 */
public class ProgramLibraryViewModel(
    private val startProgramRun: StartProgramRun,
    private val librarySnapshotLoader: suspend () -> LiftoriumNavState.Library,
    private val todayForRunLoader: suspend (programRunId: dev.liftorium.domain.run.ProgramRunId) -> TodaySessionUi?,
) : ViewModel() {

    private val _state = MutableStateFlow<ProgramLibraryUiState>(ProgramLibraryUiState.Loading)
    public val state: StateFlow<ProgramLibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            val snapshot = librarySnapshotLoader()
            _state.value = ProgramLibraryUiState.Loaded(snapshot)
        }
    }

    public suspend fun activate(
        programVersionId: ProgramVersionId,
        runtimeReferenceValues: Map<String, RuntimeReferenceValue>,
        chosenWeekVariants: Map<WeekVariantGroupKey, String>,
    ): ActivateOutcome {
        val result = startProgramRun(
            StartProgramRunCommand(
                programVersionId = programVersionId,
                runtimeReferenceValues = runtimeReferenceValues,
                chosenWeekVariants = chosenWeekVariants,
            ),
        )
        return when (result) {
            is StartProgramRunResult.Success -> {
                val today = todayForRunLoader(result.run.programRunId)
                if (today == null) {
                    ActivateOutcome.Failure("Run started but no scheduled session found")
                } else {
                    // NOTE: do NOT call refresh() here — the nav host is
                    // keyed on the library snapshot via `remember(initial)`
                    // and would reset state back to Library, undoing the
                    // Detail→Today transition. Library list freshness
                    // after activation is a follow-up (see plan.md slice 2+).
                    ActivateOutcome.Success(today)
                }
            }
            is StartProgramRunResult.Failure.UnknownProgramVersion ->
                ActivateOutcome.Failure("Unknown program version ${result.programVersionId.value}")
            is StartProgramRunResult.Failure.MissingRuntimeReferences ->
                ActivateOutcome.Failure("Missing required values: ${result.referenceIds.joinToString()}")
            is StartProgramRunResult.Failure.MissingWeekVariantChoices ->
                ActivateOutcome.Failure("Choose a variant for: ${result.groups.joinToString { it.baseWeekId }}")
            is StartProgramRunResult.Failure.InvalidWeekVariantChoice ->
                ActivateOutcome.Failure("Invalid variant choice '${result.chosen}'")
            StartProgramRunResult.Failure.AlreadyActiveRun ->
                ActivateOutcome.Failure("Another program run is already active")
        }
    }
}

public sealed interface ProgramLibraryUiState {
    public data object Loading : ProgramLibraryUiState
    @Immutable
    public data class Loaded(val library: LiftoriumNavState.Library) : ProgramLibraryUiState
}

public sealed interface ActivateOutcome {
    public data class Success(val today: TodaySessionUi) : ActivateOutcome
    public data class Failure(val message: String) : ActivateOutcome
}
