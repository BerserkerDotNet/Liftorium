package dev.liftorium.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.liftorium.app.ui.LiftoriumNavHost
import dev.liftorium.app.ui.SampleStateFactory
import dev.liftorium.app.ui.theme.LiftoriumTheme
import dev.liftorium.app.ui.program.PendingReferencesDialog
import dev.liftorium.app.ui.program.ProgramDetailScreen
import dev.liftorium.app.ui.program.ProgramLibraryScreen
import dev.liftorium.app.ui.program.TodaySessionScreen
import dev.liftorium.app.ui.program.WeekVariantPicker
import dev.liftorium.app.ui.workout.ActiveWorkoutScreen
import dev.liftorium.domain.common.ProgramVersionId
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

/**
 * Dark-theme parity for [ProgramRunnerPaparazziTest]. Same scenarios,
 * `darkTheme = true`. Snapshot filenames are distinct because Paparazzi
 * keys them by FQ test class + method.
 */
class ProgramRunnerPaparazziDarkTest {
    @get:Rule
    val paparazzi: Paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Composable
    private fun themed(content: @Composable () -> Unit) {
        LiftoriumTheme(darkTheme = true) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }

    @Test
    fun library_empty() {
        paparazzi.snapshot {
            themed {
                ProgramLibraryScreen(
                    versions = persistentListOf(),
                    onSelectVersion = {},
                    onImportClick = {},
                )
            }
        }
    }

    @Test
    fun library_populated() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        paparazzi.snapshot {
            themed {
                ProgramLibraryScreen(
                    versions = state.versions,
                    onSelectVersion = {},
                    onImportClick = {},
                )
            }
        }
    }

    @Test
    fun detail_pendingReferences() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        val detail = state.details.getValue(ProgramVersionId("5-3-1-bbb@v1"))
        paparazzi.snapshot {
            themed {
                ProgramDetailScreen(detail = detail, onBack = {}, onActivate = {})
            }
        }
    }

    @Test
    fun detail_activatable() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        val detail = state.details.getValue(ProgramVersionId("stronglifts@v1"))
        paparazzi.snapshot {
            themed {
                ProgramDetailScreen(detail = detail, onBack = {}, onActivate = {})
            }
        }
    }

    @Test
    fun dialog_pendingReferences() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        val detail = state.details.getValue(ProgramVersionId("5-3-1-bbb@v1"))
        paparazzi.snapshot {
            themed {
                PendingReferencesDialog(
                    references = detail.pendingReferences,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun dialog_variantPicker() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        val detail = state.details.getValue(ProgramVersionId("5-3-1-bbb@v1"))
        paparazzi.snapshot {
            themed {
                WeekVariantPicker(
                    groups = detail.variantGroups,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun today_session() {
        val state = SampleStateFactory.libraryWithMixedStatuses()
        val today = state.todays.getValue(ProgramVersionId("5-3-1-bbb@v1"))
        paparazzi.snapshot {
            themed {
                TodaySessionScreen(today = today, onBack = {})
            }
        }
    }

    @Test
    fun navHost_initialLibrary() {
        paparazzi.snapshot {
            themed {
                LiftoriumNavHost(initial = SampleStateFactory.libraryWithMixedStatuses())
            }
        }
    }

    @Test
    fun navHost_emptyLibrary() {
        paparazzi.snapshot {
            themed {
                LiftoriumNavHost(initial = SampleStateFactory.emptyLibrary())
            }
        }
    }

    @Test
    fun activeWorkout_justStarted() {
        paparazzi.snapshot {
            themed {
                ActiveWorkoutScreen(state = SampleStateFactory.activeWorkoutJustStarted())
            }
        }
    }
}
