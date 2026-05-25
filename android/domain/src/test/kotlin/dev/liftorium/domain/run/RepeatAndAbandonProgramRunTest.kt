package dev.liftorium.domain.run

import dev.liftorium.domain.common.ProgramVersionId

import dev.liftorium.domain.common.WeightUnit
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val FIXED_INSTANT: Instant = Instant.parse("2026-06-01T08:30:00Z")

private fun aRun(
    runId: ProgramRunId = ProgramRunId("old-run"),
    versionId: ProgramVersionId = ProgramVersionId("p1@v1"),
    hash: String = "h".repeat(64),
    status: ProgramRunStatus = ProgramRunStatus.Completed,
) = ProgramRun(
    programRunId = runId,
    programVersionId = versionId,
    pinnedContentHash = hash,
    startedAtEpochMillis = 1L,
    status = status,
    chosenWeekVariants = emptyMap(),
)

private fun aPrereqs(versionId: ProgramVersionId = ProgramVersionId("p1@v1"), hash: String = "h".repeat(64)) =
    ProgramVersionPrerequisites(
        programVersionId = versionId,
        pinnedContentHash = hash,
        requiredFirstWeekReferenceIds = setOf("tm-bench"),
        weekVariantGroups = emptyMap(),
        weekOrder = listOf(WeekSlot("b1", "w1")),
        sessionsByWeek = mapOf("w1" to listOf(PlannedSession("s1", 1))),
    )

class RepeatProgramRunTest {

    @Test
    fun `returns UnknownPreviousRun when no row exists`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2"), ZoneOffset.UTC,
        )

        val result = useCase(ProgramRunId("missing"), emptyMap(), emptyMap())

        assertIs<RepeatProgramRunResult.Failure.UnknownPreviousRun>(result)
    }

    @Test
    fun `returns UnknownPreviousRun when prerequisites are gone`() = runTest {
        val repo = RecordingFakeRepository().apply { storedRun = aRun() }
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2"), ZoneOffset.UTC,
        )

        val result = useCase(ProgramRunId("old-run"), mapOf("tm-bench" to RuntimeReferenceValue(80.0, WeightUnit.Kg)), emptyMap())

        assertIs<RepeatProgramRunResult.Failure.UnknownPreviousRun>(result)
    }

    @Test
    fun `returns MissingRuntimeReferences when no values supplied`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedRun = aRun()
            storedPrerequisites = aPrereqs()
        }
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2"), ZoneOffset.UTC,
        )

        val result = useCase(ProgramRunId("old-run"), emptyMap(), emptyMap())

        assertIs<RepeatProgramRunResult.Failure.MissingRuntimeReferences>(result)
        assertEquals(setOf("tm-bench"), result.referenceIds)
    }

    @Test
    fun `returns MissingWeekVariantChoices when a variant group has no choice`() = runTest {
        val groupKey = WeekVariantGroupKey("b1", "w10")
        val repo = RecordingFakeRepository().apply {
            storedRun = aRun()
            storedPrerequisites = aPrereqs().copy(
                requiredFirstWeekReferenceIds = emptySet(),
                weekVariantGroups = mapOf(groupKey to setOf("w10a", "w10b")),
            )
        }
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2"), ZoneOffset.UTC,
        )

        val result = useCase(ProgramRunId("old-run"), emptyMap(), emptyMap())

        assertIs<RepeatProgramRunResult.Failure.MissingWeekVariantChoices>(result)
        assertEquals(setOf(groupKey), result.groups)
    }

    @Test
    fun `success creates a new run with same pinned hash`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedRun = aRun()
            storedPrerequisites = aPrereqs()
        }
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2", "occ-1"), ZoneOffset.UTC,
        )

        val result = useCase(
            ProgramRunId("old-run"),
            mapOf("tm-bench" to RuntimeReferenceValue(82.5, WeightUnit.Kg)),
            emptyMap(),
        )

        val success = assertIs<RepeatProgramRunResult.Success>(result)
        assertEquals(ProgramRunId("run-2"), success.run.programRunId)
        assertEquals(ProgramVersionId("p1@v1"), success.run.programVersionId)
        assertEquals("h".repeat(64), success.run.pinnedContentHash)
        assertEquals(ProgramRunStatus.Active, success.run.status)
        assertEquals(1, repo.insertedRuns.size)
    }

    @Test
    fun `repository AlreadyActiveRun surfaces as Repeat failure`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedRun = aRun()
            storedPrerequisites = aPrereqs()
            insertOutcome = InsertRunOutcome.AlreadyActiveRun
        }
        val useCase = RepeatProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-2", "occ-1"), ZoneOffset.UTC,
        )

        val result = useCase(
            ProgramRunId("old-run"),
            mapOf("tm-bench" to RuntimeReferenceValue(82.5, WeightUnit.Kg)),
            emptyMap(),
        )

        assertEquals(RepeatProgramRunResult.Failure.AlreadyActiveRun, result)
    }
}

class AbandonProgramRunTest {

    @Test
    fun `returns UnknownRun when no row exists`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = AbandonProgramRun(repo)

        val result = useCase(ProgramRunId("missing"))

        assertIs<AbandonProgramRunResult.Failure.UnknownRun>(result)
    }

    @Test
    fun `returns NotActive when run is already completed`() = runTest {
        val repo = RecordingFakeRepository().apply { storedRun = aRun(status = ProgramRunStatus.Completed) }
        val useCase = AbandonProgramRun(repo)

        val result = useCase(ProgramRunId("old-run"))

        val notActive = assertIs<AbandonProgramRunResult.Failure.NotActive>(result)
        assertEquals(ProgramRunStatus.Completed, notActive.status)
    }

    @Test
    fun `returns Success when active run is abandoned`() = runTest {
        val activeRun = aRun(status = ProgramRunStatus.Active)
        val repo = RecordingFakeRepository().apply { storedRun = activeRun }
        val useCase = AbandonProgramRun(repo)

        val result = useCase(ProgramRunId("old-run"))

        val success = assertIs<AbandonProgramRunResult.Success>(result)
        assertEquals(ProgramRunStatus.Abandoned, success.run.status)
    }

    @Test
    fun `returns UnknownRun when row disappears between find and update`() = runTest {
        val activeRun = aRun(status = ProgramRunStatus.Active)
        val repo = object : ProgramRunRepository {
            override suspend fun loadPrerequisites(programVersionId: ProgramVersionId) = null
            override suspend fun insertNewRun(
                run: ProgramRun,
                runtimeReferenceValues: List<ProgramRunReferenceValue>,
                seededOccurrences: List<ScheduleOccurrence>,
            ) = InsertRunOutcome.Success
            override suspend fun findRun(programRunId: ProgramRunId): ProgramRun? = activeRun
            override suspend fun markAbandoned(programRunId: ProgramRunId): ProgramRun? = null
        }
        val useCase = AbandonProgramRun(repo)

        val result = useCase(ProgramRunId("old-run"))

        assertIs<AbandonProgramRunResult.Failure.UnknownRun>(result)
    }
}
