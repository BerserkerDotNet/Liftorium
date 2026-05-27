package dev.liftorium.app.integration

import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.RuntimeReferenceValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Integration coverage for pending-runtime-reference activation
 * (acceptance row A4 "Activate a program with first-week refs").
 *
 *  * Activating without supplying the required reference must surface
 *    `ActivateOutcome.Failure` whose message names the missing
 *    referenceId — proves StartProgramRun + ViewModel + outcome
 *    translation are wired end-to-end.
 *  * Activating with a runtime value must produce `Success`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PendingReferencesActivationTest {

    private val versionId = ProgramVersionId("pending-ref-fixture-v1")
    private lateinit var harness: IntegrationTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = IntegrationTestHarness(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        harness.close()
        Dispatchers.resetMain()
    }

    @Test
    fun activate_withoutSuppliedReference_fails_naming_referenceId() = runBlocking {
        TestProgramFixtures.loadPendingReferenceVersion(harness.database)
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val outcome = vm.activate(versionId, emptyMap(), emptyMap())

        assertTrue(outcome is ActivateOutcome.Failure, "expected Failure, got=$outcome")
        assertTrue(
            outcome.message.contains("orm-squat"),
            "failure must name the missing reference id, got=${outcome.message}",
        )
    }

    @Test
    fun activate_withSuppliedReference_succeeds() = runBlocking {
        TestProgramFixtures.loadPendingReferenceVersion(harness.database)
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val outcome = vm.activate(
            versionId,
            mapOf("orm-squat" to RuntimeReferenceValue(value = 315.0, unit = WeightUnit.Lb)),
            emptyMap(),
        )

        assertTrue(outcome is ActivateOutcome.Success, "expected Success, got=$outcome")
    }
}
