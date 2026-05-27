package dev.liftorium.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.liftorium.app.ui.program.PendingReferencesDialog
import dev.liftorium.app.ui.program.ProgramDetailScreen
import dev.liftorium.app.ui.program.ProgramLibraryScreen
import dev.liftorium.app.ui.program.TodaySessionScreen
import dev.liftorium.app.ui.program.WeekVariantPicker
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.run.RuntimeReferenceValue
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * Outcome of an activation attempt surfaced by the nav host's
 * `onActivate` callback. `Success.today` becomes the next screen's
 * state; `Failure.message` is surfaced via `onActivationError`.
 */
public sealed interface NavActivateResult {
    public data class Success(val today: TodaySessionUi) : NavActivateResult
    public data class Failure(val message: String) : NavActivateResult
}

/**
 * Stateless navigation host for the program library / activation flow.
 *
 * Theming: callers MUST wrap this composable in `LiftoriumTheme {}`.
 *
 * Activate flow (vertical-real wiring landed in `android-workout-logging`
 * Slice 1):
 *  * Detail → Activate →
 *    if pending refs present → [PendingReferencesDialog] →
 *    if variant groups present → [WeekVariantPicker] →
 *    [onActivate] suspend (calls `StartProgramRun` via the library VM) →
 *    Today screen rendered from the seeded `ScheduleOccurrence`.
 *
 * The default no-op callbacks keep Paparazzi/Robolectric shells working
 * without wiring a live VM.
 */
@Composable
public fun LiftoriumNavHost(
    initial: LiftoriumNavState,
    modifier: Modifier = Modifier,
    onStartWorkout: (ProgramRunId, String) -> Unit = { _, _ -> },
    onActivate: suspend (
        ProgramVersionId,
        Map<String, RuntimeReferenceValue>,
        Map<WeekVariantGroupKey, String>,
    ) -> NavActivateResult = { _, _, _ ->
        NavActivateResult.Failure("Activation not wired in this build")
    },
    onActivationError: (String) -> Unit = {},
) {
    var state by remember(initial) { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()

    fun runActivate(
        detail: LiftoriumNavState.Detail,
        refs: Map<String, RuntimeReferenceValue>,
        variants: Map<WeekVariantGroupKey, String>,
    ) {
        scope.launch {
            when (val outcome = onActivate(detail.detail.programVersionId, refs, variants)) {
                is NavActivateResult.Success ->
                    state = LiftoriumNavState.Today(previous = detail.previous, today = outcome.today)
                is NavActivateResult.Failure ->
                    onActivationError(outcome.message)
            }
        }
    }

    when (val s = state) {
        is LiftoriumNavState.Library -> ProgramLibraryScreen(
            versions = s.versions,
            onSelectVersion = { id ->
                val detail = s.details[id] ?: return@ProgramLibraryScreen
                state = LiftoriumNavState.Detail(
                    previous = s,
                    detail = detail,
                    today = s.todays[id],
                    showPendingRefs = false,
                    showVariantPicker = false,
                )
            },
            onImportClick = { /* SAF wiring lives in MainActivity */ },
            modifier = modifier,
        )

        is LiftoriumNavState.Detail -> {
            ProgramDetailScreen(
                detail = s.detail,
                onBack = { state = s.previous },
                onActivate = {
                    when {
                        s.detail.pendingReferences.isNotEmpty() -> state = s.copy(showPendingRefs = true)
                        s.detail.variantGroups.isNotEmpty() -> state = s.copy(showVariantPicker = true)
                        else -> runActivate(s, emptyMap(), emptyMap())
                    }
                },
                modifier = modifier,
            )
            if (s.showPendingRefs) {
                PendingReferencesDialog(
                    references = s.detail.pendingReferences,
                    onConfirm = { resolved ->
                        val runtimeRefs = resolved.mapValues { (_, pending) ->
                            RuntimeReferenceValue(value = pending.value, unit = pending.unit)
                        }
                        if (s.detail.variantGroups.isNotEmpty()) {
                            state = s.copy(
                                showPendingRefs = false,
                                showVariantPicker = true,
                                pendingRefValues = runtimeRefs,
                            )
                        } else {
                            val nextDetail = s.copy(showPendingRefs = false, pendingRefValues = runtimeRefs)
                            state = nextDetail
                            runActivate(nextDetail, runtimeRefs, emptyMap())
                        }
                    },
                    onDismiss = { state = s.copy(showPendingRefs = false) },
                )
            } else if (s.showVariantPicker) {
                WeekVariantPicker(
                    groups = s.detail.variantGroups,
                    onConfirm = { chosen ->
                        val nextDetail = s.copy(showVariantPicker = false)
                        state = nextDetail
                        runActivate(nextDetail, s.pendingRefValues, chosen)
                    },
                    onDismiss = { state = s.copy(showVariantPicker = false) },
                )
            }
        }

        is LiftoriumNavState.Today -> TodaySessionScreen(
            today = s.today,
            onBack = { state = s.previous },
            onStartWorkout = { onStartWorkout(s.today.programRunId, s.today.plannedOccurrenceId) },
            modifier = modifier,
        )
    }
}

public sealed interface LiftoriumNavState {
    @Immutable
    public data class Library(
        val versions: ImmutableList<ProgramVersionRow>,
        val details: Map<ProgramVersionId, ProgramDetailUi>,
        val todays: Map<ProgramVersionId, TodaySessionUi> = emptyMap(),
    ) : LiftoriumNavState

    @Immutable
    public data class Detail(
        val previous: Library,
        val detail: ProgramDetailUi,
        val today: TodaySessionUi?,
        val showPendingRefs: Boolean = false,
        val showVariantPicker: Boolean = false,
        val pendingRefValues: Map<String, RuntimeReferenceValue> = emptyMap(),
    ) : LiftoriumNavState

    @Immutable
    public data class Today(
        val previous: Library,
        val today: TodaySessionUi,
    ) : LiftoriumNavState
}
