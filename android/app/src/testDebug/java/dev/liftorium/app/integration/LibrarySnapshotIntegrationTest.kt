package dev.liftorium.app.integration

import androidx.test.core.app.ApplicationProvider
import dev.liftorium.app.ui.program.ActivateOutcome
import dev.liftorium.app.ui.program.ProgramLibraryUiState
import dev.liftorium.domain.common.ProgramVersionId
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Library snapshot integration coverage:
 *  * Empty DB → snapshot resolves to a `Loaded(empty Library)` instead
 *    of hanging in Loading.
 *  * After activation, a fresh `programLibraryRepository.snapshot()`
 *    contains the activated program in `todays`, proving the
 *    "Today preview after activate" data path the nav host comment
 *    in ProgramLibraryViewModel calls out as a follow-up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LibrarySnapshotIntegrationTest {

    private val bbbId = ProgramVersionId("five-three-one-bbb-v1")
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
    fun emptyDatabase_resolvesToEmptyLoadedLibrary() = runBlocking {
        // Intentionally do NOT call bootstrapBundledFixtures().
        val vm = harness.newProgramLibraryViewModel()
        val loaded = vm.state
            .first { it is ProgramLibraryUiState.Loaded } as ProgramLibraryUiState.Loaded
        assertTrue(loaded.library.versions.isEmpty(), "expected no versions, got=${loaded.library.versions}")
        assertTrue(loaded.library.details.isEmpty())
        assertTrue(loaded.library.todays.isEmpty())
    }

    @Test
    fun snapshotAfterActivate_includesActivatedProgramInTodays() = runBlocking<Unit> {
        harness.bootstrapBundledFixtures()

        // The library snapshot itself always carries a *preview* today
        // (synthetic `preview:...` run id) — the real run id is resolved
        // by `todayForRun(runId)` once a run exists. Verify both halves
        // of that contract.
        val before = harness.programLibraryRepository.snapshot()
        val previewToday = before.todays[bbbId]
        assertNotNull(previewToday, "preview today must be present")
        assertTrue(
            previewToday.programRunId.value.startsWith("preview:"),
            "preview entry should be synthetic, got=${previewToday.programRunId}",
        )

        val vm = harness.newProgramLibraryViewModel()
        vm.state.first { it is ProgramLibraryUiState.Loaded }
        val activated = vm.activate(bbbId, emptyMap(), emptyMap()) as ActivateOutcome.Success

        val realToday = harness.programLibraryRepository.todayForRun(activated.today.programRunId)
        assertNotNull(realToday, "todayForRun must resolve real occurrence after activation")
        assertEquals(activated.today.programRunId, realToday.programRunId)
        assertEquals(activated.today.plannedOccurrenceId, realToday.plannedOccurrenceId)
        assertTrue(
            !realToday.programRunId.value.startsWith("preview:"),
            "todayForRun must NOT return preview id, got=${realToday.programRunId}",
        )
    }
}
