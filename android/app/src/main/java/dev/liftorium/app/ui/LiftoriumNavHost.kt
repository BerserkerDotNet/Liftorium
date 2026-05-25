package dev.liftorium.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.liftorium.domain.common.ProgramVersionId
import kotlinx.collections.immutable.ImmutableList

/**
 * android-program-runner stateless navigation host. Switches between Library / Detail /
 * Today screens using simple state, no Navigation Compose dependency.
 *
 * Activate flow:
 *  * Detail screen → Activate button →
 *    if pending refs present → [PendingReferencesDialog] →
 *    if variant groups present → [WeekVariantPicker] →
 *    Today screen (with stubbed seeded session).
 *
 * Sample sample-state (for Paparazzi/Robolectric/launch-time placeholder)
 * lives in the `debug` source set under `SampleStateFactory`. Release
 * APKs receive an empty library via the variant-aware
 * `bootstrapState()` shim; the in-memory sample data therefore never
 * ships to release builds. Real use-case wiring lands when the
 * android-program-runner follow-on slice adds a manual DI container.
 */
@Composable
public fun LiftoriumNavHost(
    initial: LiftoriumNavState,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(initial) }

    MaterialTheme {
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
                        state = when {
                            s.detail.pendingReferences.isNotEmpty() -> s.copy(showPendingRefs = true)
                            s.detail.variantGroups.isNotEmpty() -> s.copy(showVariantPicker = true)
                            s.today != null -> LiftoriumNavState.Today(s.previous, s.today)
                            else -> s
                        }
                    },
                    modifier = modifier,
                )
                if (s.showPendingRefs) {
                    PendingReferencesDialog(
                        references = s.detail.pendingReferences,
                        onConfirm = {
                            state = when {
                                s.detail.variantGroups.isNotEmpty() ->
                                    s.copy(showPendingRefs = false, showVariantPicker = true)
                                s.today != null -> LiftoriumNavState.Today(s.previous, s.today)
                                else -> s.copy(showPendingRefs = false)
                            }
                        },
                        onDismiss = { state = s.copy(showPendingRefs = false) },
                    )
                } else if (s.showVariantPicker) {
                    WeekVariantPicker(
                        groups = s.detail.variantGroups,
                        onConfirm = {
                            state = s.today?.let { LiftoriumNavState.Today(s.previous, it) }
                                ?: s.copy(showVariantPicker = false)
                        },
                        onDismiss = { state = s.copy(showVariantPicker = false) },
                    )
                }
            }

            is LiftoriumNavState.Today -> TodaySessionScreen(
                today = s.today,
                onBack = { state = s.previous },
                modifier = modifier,
            )
        }
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
    ) : LiftoriumNavState

    @Immutable
    public data class Today(
        val previous: Library,
        val today: TodaySessionUi,
    ) : LiftoriumNavState
}
