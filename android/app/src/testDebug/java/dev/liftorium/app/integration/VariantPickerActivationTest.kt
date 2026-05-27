package dev.liftorium.app.integration

import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.WeekVariantGroupKey
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
 * Integration coverage for week-variant activation paths.
 *
 *  * No choice supplied → Failure naming the base week.
 *  * Invalid choice (week id outside the group) → Failure naming the
 *    rejected choice.
 *  * Valid choice (either the base or the variant member) → Success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VariantPickerActivationTest {

    private val versionId = ProgramVersionId("variant-fixture-v1")
    private val group = WeekVariantGroupKey(blockId = "b1", baseWeekId = "w1-base")
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
    fun activate_withoutVariantChoice_fails_promptingForBaseWeek() = runBlocking {
        TestProgramFixtures.loadWeekVariantVersion(harness.database)
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val outcome = vm.activate(versionId, emptyMap(), emptyMap())

        assertTrue(outcome is ActivateOutcome.Failure, "expected Failure, got=$outcome")
        assertTrue(
            outcome.message.contains("w1-base"),
            "failure must name the unresolved variant group, got=${outcome.message}",
        )
    }

    @Test
    fun activate_withInvalidVariantChoice_fails_namingTheBadChoice() = runBlocking {
        TestProgramFixtures.loadWeekVariantVersion(harness.database)
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val outcome = vm.activate(
            versionId,
            emptyMap(),
            mapOf(group to "w1-light-DOES-NOT-EXIST"),
        )

        assertTrue(outcome is ActivateOutcome.Failure, "expected Failure, got=$outcome")
        assertTrue(
            outcome.message.contains("w1-light-DOES-NOT-EXIST"),
            "failure must name the invalid choice, got=${outcome.message}",
        )
    }

    @Test
    fun activate_withValidVariantChoice_succeeds() = runBlocking {
        TestProgramFixtures.loadWeekVariantVersion(harness.database)
        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }

        val outcome = vm.activate(
            versionId,
            emptyMap(),
            mapOf(group to "w1-heavy"),
        )

        assertTrue(outcome is ActivateOutcome.Success, "expected Success, got=$outcome")
    }
}
