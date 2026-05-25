package dev.liftorium.data.run

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.resource.LoadedProgramBlockEntity
import dev.liftorium.data.resource.LoadedProgramVersionBundle
import dev.liftorium.data.resource.LoadedProgramVersionEntity
import dev.liftorium.data.resource.LoadedProgramWeekEntity
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.resource.LoadedSessionTemplateEntity
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.InsertRunOutcome
import dev.liftorium.domain.run.OccurrenceState
import dev.liftorium.domain.run.ProgramRun
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.ProgramRunReferenceValue
import dev.liftorium.domain.run.ProgramRunStatus
import dev.liftorium.domain.run.ReferenceValueSource
import dev.liftorium.domain.run.ScheduleOccurrence
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [RoomProgramRunRepository] against an in-memory
 * Room database. Exercises:
 *
 *  * prerequisites computed from the loaded program version's required
 *    references and week tree (filters non-runtime types, supplied refs,
 *    and refs gated to later weeks);
 *  * variant-group detection (only groups with > 1 member surface);
 *  * happy-path insert that round-trips through [findRun];
 *  * the `activeRunSlot` unique index gating a second Active insert;
 *  * abandon flow that nulls `activeRunSlot` and frees the slot.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class RoomProgramRunRepositoryTest {

    private lateinit var database: LiftoriumDatabase
    private lateinit var repository: RoomProgramRunRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftoriumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomProgramRunRepository(
            runDao = database.programRunDao(),
            versionDao = database.loadedProgramVersionDao(),
            timeSource = TimeSource.fixed(java.time.Instant.ofEpochMilli(123L)),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `loadPrerequisites returns null for unknown program version`() = runBlocking {
        assertNull(repository.loadPrerequisites(ProgramVersionId("missing@v1")))
    }

    @Test
    fun `loadPrerequisites filters supplied refs and refs gated to later weeks`() = runBlocking {
        loadVersion(
            programVersionId = "p@v1",
            requiredReferences = listOf(
                requiredRef("p@v1", "tm-bench", supplied = false, firstWeek = 1, type = "training_max"),
                requiredRef("p@v1", "tm-squat", supplied = true, firstWeek = 1, type = "training_max"),
                requiredRef("p@v1", "tm-row", supplied = false, firstWeek = 2, type = "training_max"),
                requiredRef("p@v1", "macro-cal", supplied = false, firstWeek = 1, type = "macro_calorie"),
            ),
            blocks = listOf(LoadedProgramBlockEntity("p@v1", "b1", 1, null, null)),
            weeks = listOf(week("p@v1", "b1", "w1", 1, variantOf = null)),
            sessions = listOf(session("p@v1", "s1", "w1", 1)),
        )

        val prereqs = repository.loadPrerequisites(ProgramVersionId("p@v1"))
        assertNotNull(prereqs)
        assertEquals(setOf("tm-bench"), prereqs.requiredFirstWeekReferenceIds)
        assertTrue(prereqs.weekVariantGroups.isEmpty())
        assertEquals(1, prereqs.weekOrder.size)
        assertEquals(listOf("s1"), prereqs.sessionsByWeek["w1"]?.map { it.sessionId })
    }

    @Test
    fun `loadPrerequisites groups week variants under their base`() = runBlocking {
        loadVersion(
            programVersionId = "p@v2",
            requiredReferences = emptyList(),
            blocks = listOf(LoadedProgramBlockEntity("p@v2", "b1", 1, null, null)),
            weeks = listOf(
                week("p@v2", "b1", "w1-base", 1, variantOf = null),
                week("p@v2", "b1", "w1-heavy", 1, variantOf = "w1-base"),
                week("p@v2", "b1", "w2", 2, variantOf = null),
            ),
            sessions = listOf(
                session("p@v2", "s1a", "w1-base", 1),
                session("p@v2", "s1b", "w1-heavy", 1),
                session("p@v2", "s2", "w2", 1),
            ),
        )

        val prereqs = repository.loadPrerequisites(ProgramVersionId("p@v2"))
        assertNotNull(prereqs)
        val key = WeekVariantGroupKey("b1", "w1-base")
        assertEquals(setOf(key), prereqs.weekVariantGroups.keys)
        assertEquals(setOf("w1-base", "w1-heavy"), prereqs.weekVariantGroups[key])
        assertEquals(2, prereqs.weekOrder.size)
        assertEquals("w1-base", prereqs.weekOrder[0].baseWeekId)
        assertEquals("w2", prereqs.weekOrder[1].baseWeekId)
    }

    @Test
    fun `insertNewRun persists run and reference values and occurrences`() = runBlocking {
        loadMinimalVersion("p@v3")
        val outcome = repository.insertNewRun(
            run = activeRun("run-1", "p@v3"),
            runtimeReferenceValues = listOf(
                ProgramRunReferenceValue(
                    programRunId = ProgramRunId("run-1"),
                    referenceId = "tm-bench",
                    value = 100.0,
                    unit = WeightUnit.Kg,
                    source = ReferenceValueSource.RuntimeInjection,
                    suppliedAtEpochMillis = 5L,
                ),
            ),
            seededOccurrences = listOf(scheduledOccurrence("run-1", "occ-1", 0L)),
        )

        assertEquals(InsertRunOutcome.Success, outcome)
        val stored = repository.findRun(ProgramRunId("run-1"))
        assertNotNull(stored)
        assertEquals(ProgramVersionId("p@v3"), stored.programVersionId)
        assertEquals(ProgramRunStatus.Active, stored.status)
        assertEquals(1, database.programRunDao().listOccurrences("run-1").size)
        assertEquals(1, database.programRunDao().listReferenceValues("run-1").size)
    }

    @Test
    fun `insertNewRun returns AlreadyActiveRun when another Active row exists`() = runBlocking {
        loadMinimalVersion("p@v4")
        repository.insertNewRun(
            run = activeRun("run-a", "p@v4"),
            runtimeReferenceValues = emptyList(),
            seededOccurrences = emptyList(),
        )

        val outcome = repository.insertNewRun(
            run = activeRun("run-b", "p@v4"),
            runtimeReferenceValues = emptyList(),
            seededOccurrences = emptyList(),
        )

        assertEquals(InsertRunOutcome.AlreadyActiveRun, outcome)
        assertNull(repository.findRun(ProgramRunId("run-b")))
    }

    @Test
    fun `markAbandoned nulls activeRunSlot so a new run can start`() = runBlocking {
        loadMinimalVersion("p@v5")
        repository.insertNewRun(
            run = activeRun("run-a", "p@v5"),
            runtimeReferenceValues = emptyList(),
            seededOccurrences = emptyList(),
        )

        val updated = repository.markAbandoned(ProgramRunId("run-a"))
        assertNotNull(updated)
        assertEquals(ProgramRunStatus.Abandoned, updated.status)
        assertNull(database.programRunDao().findActiveRun())

        val outcome = repository.insertNewRun(
            run = activeRun("run-b", "p@v5"),
            runtimeReferenceValues = emptyList(),
            seededOccurrences = emptyList(),
        )
        assertEquals(InsertRunOutcome.Success, outcome)
    }

    @Test
    fun `markAbandoned returns null for unknown run`() = runBlocking {
        assertNull(repository.markAbandoned(ProgramRunId("never-existed")))
    }

    @Test
    fun `chosenWeekVariants survive round-trip through JSON`() = runBlocking {
        loadMinimalVersion("p@v6")
        val choices = mapOf(
            WeekVariantGroupKey("b1", "w1") to "w1-heavy",
            WeekVariantGroupKey("b1", "w2") to "w2-light",
            WeekVariantGroupKey("b2", "w1") to "w1-volume",
        )
        repository.insertNewRun(
            run = activeRun("run-vp", "p@v6").copy(chosenWeekVariants = choices),
            runtimeReferenceValues = emptyList(),
            seededOccurrences = emptyList(),
        )

        val stored = repository.findRun(ProgramRunId("run-vp"))
        assertNotNull(stored)
        assertEquals(choices, stored.chosenWeekVariants)
    }

    // --- helpers ---

    private fun activeRun(id: String, versionId: String) = ProgramRun(
        programRunId = ProgramRunId(id),
        programVersionId = ProgramVersionId(versionId),
        pinnedContentHash = "0".repeat(64),
        startedAtEpochMillis = 1L,
        status = ProgramRunStatus.Active,
        chosenWeekVariants = emptyMap(),
    )

    private fun scheduledOccurrence(runId: String, occId: String, dayOffset: Long) = ScheduleOccurrence(
        occurrenceId = occId,
        programRunId = ProgramRunId(runId),
        plannedEpochDay = dayOffset,
        actualCompletionEpochDay = null,
        blockId = "b1",
        weekId = "w1",
        sessionId = "s1",
        sessionIndex = 1,
        state = OccurrenceState.Planned,
    )

    private fun requiredRef(
        versionId: String,
        refId: String,
        supplied: Boolean,
        firstWeek: Int,
        type: String,
    ) = LoadedRequiredReferenceEntity(
        programVersionId = versionId,
        referenceId = refId,
        referenceType = type,
        exerciseId = null,
        firstRunnableWeekIndex = firstWeek,
        supplied = supplied,
        value = null,
        unit = null,
    )

    private fun week(
        versionId: String,
        blockId: String,
        weekId: String,
        weekIndex: Int,
        variantOf: String?,
    ) = LoadedProgramWeekEntity(versionId, weekId, blockId, weekIndex, variantOf, null)

    private fun session(versionId: String, sessionId: String, weekId: String, idx: Int) =
        LoadedSessionTemplateEntity(versionId, sessionId, weekId, idx, null, null)

    private suspend fun loadMinimalVersion(programVersionId: String) {
        loadVersion(
            programVersionId = programVersionId,
            requiredReferences = emptyList(),
            blocks = listOf(LoadedProgramBlockEntity(programVersionId, "b1", 1, null, null)),
            weeks = listOf(week(programVersionId, "b1", "w1", 1, variantOf = null)),
            sessions = listOf(session(programVersionId, "s1", "w1", 1)),
        )
    }

    private suspend fun loadVersion(
        programVersionId: String,
        requiredReferences: List<LoadedRequiredReferenceEntity>,
        blocks: List<LoadedProgramBlockEntity>,
        weeks: List<LoadedProgramWeekEntity>,
        sessions: List<LoadedSessionTemplateEntity>,
    ) {
        val version = LoadedProgramVersionEntity(
            programVersionId = programVersionId,
            programId = programVersionId.substringBefore('@'),
            versionLabel = programVersionId.substringAfter('@'),
            displayName = null,
            authorAttribution = null,
            // contentHash is unique per row (v2 schema enforces a unique
            // index); derive a deterministic distinct hash from the
            // programVersionId so each fixture row satisfies the
            // invariant without coupling tests to a real canonicaliser.
            contentHash = programVersionId.hashCode().toString().padStart(64, '0').take(64),
            schemaVersion = 1,
            validationStatus = "activatable",
            loadedAtEpochMillis = 1L,
            programDefaultsJson = null,
            programStructureRoundingOverrideJson = null,
            importAuditJson = "{}",
            validationIssuesJson = "[]",
        )
        val bundle = LoadedProgramVersionBundle(
            version = version,
            catalogEntries = emptyList(),
            requiredReferences = requiredReferences,
            progressionRules = emptyList(),
            blocks = blocks,
            weeks = weeks,
            sessions = sessions,
            groups = emptyList(),
            items = emptyList(),
            sets = emptyList(),
            targets = emptyList(),
        )
        database.loadedProgramVersionDao().loadFullVersion(bundle)
    }
}
