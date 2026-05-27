package dev.liftorium.data.run

import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.OccurrenceState
import dev.liftorium.domain.run.ProgramRun
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.run.ProgramRunReferenceValue
import dev.liftorium.domain.run.ProgramRunStatus
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.ReferenceValueSource
import dev.liftorium.domain.run.ScheduleOccurrence
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the `internal` wire-mapping helpers in
 * [RoomProgramRunRepository]. The Robolectric integration tests cover
 * the round-trip happy paths but skip rarely-hit enum branches (e.g.
 * `WeightUnit.Lb`, `OccurrenceState.Completed/Skipped/Rescheduled`,
 * `ProgramRunStatus.Completed`, `ReferenceValueSource.OperatorImport`)
 * because they're not produced by the start/repeat/abandon use cases
 * today. These tests pin the wire format directly so a future migration
 * cannot silently rename them.
 */
class RunMapperTest {

    private val json = RoomProgramRunRepository.DEFAULT_JSON

    @Test
    fun `ProgramRunStatus wire format is stable across all values`() {
        assertEquals("active", ProgramRunStatus.Active.wire())
        assertEquals("completed", ProgramRunStatus.Completed.wire())
        assertEquals("abandoned", ProgramRunStatus.Abandoned.wire())
    }

    @Test
    fun `programRunStatusFromWire round-trips every value`() {
        for (status in ProgramRunStatus.entries) {
            assertEquals(status, programRunStatusFromWire(status.wire(), programRunId = "run-1"))
        }
    }

    @Test
    fun `programRunStatusFromWire throws CorruptProgramRunStatusException on unknown wire value`() {
        val ex = runCatching {
            programRunStatusFromWire("nope", programRunId = "run-1")
        }.exceptionOrNull()
        check(ex is CorruptProgramRunStatusException) { "expected CorruptProgramRunStatusException, got $ex" }
        assertEquals("run-1", ex.programRunId)
        assertEquals("nope", ex.wireValue)
    }

    @Test
    fun `ProgramRun toEntity flips activeRunSlot only for Active rows`() {
        val active = sampleRun().copy(status = ProgramRunStatus.Active)
        val completed = sampleRun().copy(status = ProgramRunStatus.Completed)
        val abandoned = sampleRun().copy(status = ProgramRunStatus.Abandoned)

        assertEquals(1L, active.toEntity(json).activeRunSlot)
        assertNull(completed.toEntity(json).activeRunSlot)
        assertNull(abandoned.toEntity(json).activeRunSlot)
    }

    @Test
    fun `ProgramRun round-trips status through entity-to-domain for every value`() {
        for (status in ProgramRunStatus.entries) {
            val run = sampleRun().copy(status = status)
            val roundTripped = run.toEntity(json).toDomain(json)
            assertEquals(status, roundTripped.status)
        }
    }

    @Test
    fun `ProgramRunReferenceValue toEntity maps both weight units`() {
        val kg = sampleReferenceValue().copy(unit = WeightUnit.Kg).toEntity()
        val lb = sampleReferenceValue().copy(unit = WeightUnit.Lb).toEntity()

        assertEquals("kg", kg.unit)
        assertEquals("lb", lb.unit)
    }

    @Test
    fun `ProgramRunReferenceValue toEntity maps both source kinds`() {
        val runtime = sampleReferenceValue().copy(source = ReferenceValueSource.RuntimeInjection).toEntity()
        val operator = sampleReferenceValue().copy(source = ReferenceValueSource.OperatorImport).toEntity()

        assertEquals("runtime_injection", runtime.source)
        assertEquals("operator_import", operator.source)
    }

    @Test
    fun `ScheduleOccurrence toEntity maps every state value`() {
        val byState = OccurrenceState.entries.associateWith {
            sampleOccurrence().copy(state = it).toEntity().state
        }
        assertEquals(
            mapOf(
                OccurrenceState.Planned to "planned",
                OccurrenceState.Completed to "completed",
                OccurrenceState.Skipped to "skipped",
                OccurrenceState.Rescheduled to "rescheduled",
            ),
            byState,
        )
    }

    @Test
    fun `chosen week variants encode round-trips through JSON`() {
        val choices = mapOf(
            WeekVariantGroupKey("b1", "w1") to "w1-heavy",
            WeekVariantGroupKey("b1", "w2") to "w2-light",
            WeekVariantGroupKey("b2", "w1") to "w1-volume",
        )
        val encoded = encodeChosenWeekVariants(choices, json)
        val decoded = decodeChosenWeekVariants(encoded, json)
        assertEquals(choices, decoded)
    }

    @Test
    fun `decodeChosenWeekVariants returns empty for blank input`() {
        assertEquals(emptyMap(), decodeChosenWeekVariants("", json))
        assertEquals(emptyMap(), decodeChosenWeekVariants("   ", json))
    }

    @Test
    fun `encodeChosenWeekVariants emits empty JSON object for no choices`() {
        val encoded = encodeChosenWeekVariants(emptyMap(), json)
        assertEquals(emptyMap(), decodeChosenWeekVariants(encoded, json))
    }

    private fun sampleRun() = ProgramRun(
        programRunId = ProgramRunId("r1"),
        programVersionId = ProgramVersionId("p@v1"),
        pinnedContentHash = "0".repeat(64),
        startedAtEpochMillis = 1L,
        status = ProgramRunStatus.Active,
        chosenWeekVariants = emptyMap(),
    )

    private fun sampleReferenceValue() = ProgramRunReferenceValue(
        programRunId = ProgramRunId("r1"),
        referenceId = "orm-bench",
        value = 100.0,
        unit = WeightUnit.Kg,
        source = ReferenceValueSource.RuntimeInjection,
        suppliedAtEpochMillis = 5L,
    )

    private fun sampleOccurrence() = ScheduleOccurrence(
        occurrenceId = "o1",
        programRunId = ProgramRunId("r1"),
        plannedEpochDay = 0L,
        actualCompletionEpochDay = null,
        blockId = "b1",
        weekId = "w1",
        sessionId = "s1",
        sessionIndex = 1,
        state = OccurrenceState.Planned,
    )
}
