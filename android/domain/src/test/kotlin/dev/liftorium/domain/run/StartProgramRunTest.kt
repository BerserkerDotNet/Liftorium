package dev.liftorium.domain.run

import dev.liftorium.domain.common.ProgramVersionId

import dev.liftorium.domain.common.WeightUnit
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val FIXED_INSTANT: Instant = Instant.parse("2026-05-18T10:00:00Z")
private val UTC = ZoneOffset.UTC

private fun prerequisites(
    programVersionId: ProgramVersionId = ProgramVersionId("p1@v1"),
    pinnedContentHash: String = "h".repeat(64),
    requiredFirstWeekReferenceIds: Set<String> = setOf("tm-bench"),
    weekVariantGroups: Map<WeekVariantGroupKey, Set<String>> = emptyMap(),
    weekOrder: List<WeekSlot> = listOf(WeekSlot("b1", "w1")),
    sessionsByWeek: Map<String, List<PlannedSession>> = mapOf(
        "w1" to listOf(PlannedSession("s1", 1), PlannedSession("s2", 2)),
    ),
): ProgramVersionPrerequisites = ProgramVersionPrerequisites(
    programVersionId = programVersionId,
    pinnedContentHash = pinnedContentHash,
    requiredFirstWeekReferenceIds = requiredFirstWeekReferenceIds,
    weekVariantGroups = weekVariantGroups,
    weekOrder = weekOrder,
    sessionsByWeek = sessionsByWeek,
)

class StartProgramRunTest {

    @Test
    fun `returns UnknownProgramVersion when prerequisites missing`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = StartProgramRun(repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1"), UTC)

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("nope@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = emptyMap(),
            ),
        )

        assertIs<StartProgramRunResult.Failure.UnknownProgramVersion>(result)
        assertEquals(ProgramVersionId("nope@v1"), result.programVersionId)
        assertEquals(0, repo.insertedRuns.size)
    }

    @Test
    fun `returns MissingRuntimeReferences when first-week refs unsupplied`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(requiredFirstWeekReferenceIds = setOf("tm-bench", "tm-squat"))
        }
        val useCase = StartProgramRun(repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1"), UTC)

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = mapOf("tm-bench" to RuntimeReferenceValue(100.0, WeightUnit.Kg)),
                chosenWeekVariants = emptyMap(),
            ),
        )

        assertIs<StartProgramRunResult.Failure.MissingRuntimeReferences>(result)
        assertEquals(setOf("tm-squat"), result.referenceIds)
    }

    @Test
    fun `returns MissingWeekVariantChoices when variant group lacks a choice`() = runTest {
        val groupKey = WeekVariantGroupKey("b1", "w10")
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(
                requiredFirstWeekReferenceIds = emptySet(),
                weekVariantGroups = mapOf(groupKey to setOf("w10a", "w10b")),
            )
        }
        val useCase = StartProgramRun(repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1"), UTC)

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = emptyMap(),
            ),
        )

        assertIs<StartProgramRunResult.Failure.MissingWeekVariantChoices>(result)
        assertEquals(setOf(groupKey), result.groups)
    }

    @Test
    fun `returns InvalidWeekVariantChoice when chosen weekId not in allowed set`() = runTest {
        val groupKey = WeekVariantGroupKey("b1", "w10")
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(
                requiredFirstWeekReferenceIds = emptySet(),
                weekVariantGroups = mapOf(groupKey to setOf("w10a", "w10b")),
            )
        }
        val useCase = StartProgramRun(repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1"), UTC)

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = mapOf(groupKey to "w10z"),
            ),
        )

        assertIs<StartProgramRunResult.Failure.InvalidWeekVariantChoice>(result)
        assertEquals(groupKey, result.group)
        assertEquals("w10z", result.chosen)
        assertEquals(setOf("w10a", "w10b"), result.allowed)
    }

    @Test
    fun `success persists run with pinned hash and seeded occurrences`() = runTest {
        val repo = RecordingFakeRepository().apply { storedPrerequisites = prerequisites() }
        val useCase = StartProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1", "occ-1", "occ-2"), UTC,
        )

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = mapOf("tm-bench" to RuntimeReferenceValue(100.0, WeightUnit.Kg)),
                chosenWeekVariants = emptyMap(),
            ),
        )

        val success = assertIs<StartProgramRunResult.Success>(result)
        assertEquals(ProgramRunId("run-1"), success.run.programRunId)
        assertEquals(ProgramVersionId("p1@v1"), success.run.programVersionId)
        assertEquals("h".repeat(64), success.run.pinnedContentHash)
        assertEquals(ProgramRunStatus.Active, success.run.status)
        assertEquals(FIXED_INSTANT.toEpochMilli(), success.run.startedAtEpochMillis)

        assertEquals(2, success.seededOccurrences.size)
        val startDay = LocalDate.ofInstant(FIXED_INSTANT, UTC).toEpochDay()
        assertEquals(startDay, success.seededOccurrences[0].plannedEpochDay)
        assertEquals(startDay + 1, success.seededOccurrences[1].plannedEpochDay)
        assertEquals(OccurrenceState.Planned, success.seededOccurrences[0].state)
        assertNull(success.seededOccurrences[0].actualCompletionEpochDay)

        assertEquals(1, repo.insertedRuns.size)
        assertEquals(1, repo.insertedValues[0].size)
        val refValue = repo.insertedValues[0][0]
        assertEquals(100.0, refValue.value)
        assertEquals(WeightUnit.Kg, refValue.unit)
        assertEquals(ReferenceValueSource.RuntimeInjection, refValue.source)
    }

    @Test
    fun `success works with empty required-refs and empty variant groups`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(
                requiredFirstWeekReferenceIds = emptySet(),
                weekOrder = listOf(WeekSlot("b1", "w1")),
                sessionsByWeek = mapOf("w1" to listOf(PlannedSession("s1", 1))),
            )
        }
        val useCase = StartProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1", "occ-1"), UTC,
        )

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = emptyMap(),
            ),
        )

        assertIs<StartProgramRunResult.Success>(result)
        assertEquals(0, repo.insertedValues[0].size)
    }

    @Test
    fun `repository AlreadyActiveRun surfaces as failure case`() = runTest {
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(requiredFirstWeekReferenceIds = emptySet())
            insertOutcome = InsertRunOutcome.AlreadyActiveRun
        }
        val useCase = StartProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1", "occ-1", "occ-2"), UTC,
        )

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = emptyMap(),
            ),
        )

        assertEquals(StartProgramRunResult.Failure.AlreadyActiveRun, result)
        assertEquals(0, repo.insertedRuns.size)
    }

    @Test
    fun `valid variant choice is accepted`() = runTest {
        val groupKey = WeekVariantGroupKey("b1", "w10")
        val repo = RecordingFakeRepository().apply {
            storedPrerequisites = prerequisites(
                requiredFirstWeekReferenceIds = emptySet(),
                weekVariantGroups = mapOf(groupKey to setOf("w10a", "w10b")),
                weekOrder = listOf(WeekSlot("b1", "w10")),
                sessionsByWeek = mapOf(
                    "w10a" to listOf(PlannedSession("s1", 1)),
                    "w10b" to listOf(PlannedSession("s1", 1)),
                ),
            )
        }
        val useCase = StartProgramRun(
            repo, fixedTimeSource(FIXED_INSTANT), sequencedIdGenerator("run-1", "occ-1"), UTC,
        )

        val result = useCase(
            StartProgramRunCommand(
                programVersionId = ProgramVersionId("p1@v1"),
                runtimeReferenceValues = emptyMap(),
                chosenWeekVariants = mapOf(groupKey to "w10a"),
            ),
        )

        val success = assertIs<StartProgramRunResult.Success>(result)
        assertEquals(mapOf(groupKey to "w10a"), success.run.chosenWeekVariants)
        assertEquals("w10a", success.seededOccurrences[0].weekId)
    }
}
