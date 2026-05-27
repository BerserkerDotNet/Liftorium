package dev.liftorium.app.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertNotNull

/**
 * Process-boot smoke test: launching [MainActivity] under Robolectric
 * with the production [dev.liftorium.app.LiftoriumApplication] must
 * resolve [dev.liftorium.app.di.AppContainer], hit `setContent` and
 * render without throwing. Catches regressions in the boot wiring
 * (Application → AppContainer → ViewModelFactory → LiftoriumNavHost)
 * that VM-only tests skip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {

    @Test
    fun launchingMainActivity_doesNotCrash() {
        // Robolectric provides an isolated app data dir per test, so the
        // production Room database file created by AppContainer.create
        // stays scoped to this test process.
        ApplicationProvider.getApplicationContext<android.app.Application>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity, "MainActivity should launch through Robolectric")
            }
        }
    }
}
