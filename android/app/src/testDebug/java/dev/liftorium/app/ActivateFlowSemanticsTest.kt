package dev.liftorium.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.liftorium.app.ui.LiftoriumNavHost
import dev.liftorium.app.ui.SampleStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Activate flow semantics test for the `android-program-runner` workstream.
 *
 * Covers the `activatable` status path:
 *  * `activatable` status with no variant groups → tapping Activate
 *    jumps directly to the Today screen.
 *
 * The `pending_runtime_references` flow (tapping Activate raises the
 * `PendingReferencesDialog`) is intentionally NOT asserted here:
 * Robolectric's AlertDialog window handling is unreliable for that
 * level of detail. Visual evidence comes from `ProgramRunnerPaparazziTest`;
 * the full dialog input + confirm flow lands in `connectedDebugAndroidTest`
 * in a follow-on android-program-runner slice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivateFlowSemanticsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activate_stronglifts_goesStraightToToday() {
        composeTestRule.setContent {
            LiftoriumNavHost(initial = SampleStateFactory.libraryWithMixedStatuses())
        }

        composeTestRule.onNodeWithText("StrongLifts 5x5").performClick()
        composeTestRule.onNodeWithTag("activate-button").performClick()

        composeTestRule.onNodeWithText("Week 1 · Workout A").assertIsDisplayed()
    }

    // NOTE: A "tapping Activate on a pending_runtime_references program opens
    // the PendingReferencesDialog" assertion was attempted here but Robolectric
    // does not idle on Compose AlertDialog windows in this configuration. The
    // dialog's visual evidence comes from `ProgramRunnerPaparazziTest`; full
    // input + confirm flow is reserved for connectedDebugAndroidTest in a
    // future slice.
}

