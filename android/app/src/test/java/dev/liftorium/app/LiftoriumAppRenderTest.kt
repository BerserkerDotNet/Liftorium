package dev.liftorium.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-driven Compose render test. Asserts the semantics tree, not
 * pixels — paired with [LiftoriumAppScreenshotTest] for visual evidence.
 *
 * Runs as part of `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiftoriumAppRenderTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liftoriumApp_displaysSampleProgramLibrary() {
        composeTestRule.setContent {
            LiftoriumApp()
        }

        composeTestRule
            .onNodeWithText("Programs")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("5/3/1 BBB")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("StrongLifts 5x5")
            .assertIsDisplayed()
    }
}
