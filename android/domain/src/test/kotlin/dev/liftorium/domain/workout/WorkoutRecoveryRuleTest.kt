package dev.liftorium.domain.workout

import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceId
import dev.liftorium.domain.common.SyncMetadata
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UT-DUR-002 — recovery state rules.
 *
 * Asserts [WorkoutRecoveryRule] decisions match the recovery contract
 * (`docs/architecture.md` Recovery flow):
 *  * null aggregate → NoOpenSession.
 *  * Terminal session aggregate → NoOpenSession (history-only, not
 *    recoverable).
 *  * InProgress aggregate whose pinned program version is loaded →
 *    Resume.
 *  * InProgress aggregate whose pinned program version is NOT loaded
 *    → Repair (NOT auto-abandon; raw logs preserved per ADR).
 */
class WorkoutRecoveryRuleTest {

    @Test
    fun `null aggregate produces no open session`() {
        val decision = WorkoutRecoveryRule.decide(aggregate = null, loadedProgramVersionIds = setOf("pv-1"))
        assertEquals(RecoveryDecision.NoOpenSession, decision)
    }

    @Test
    fun `terminal completed aggregate produces no open session`() {
        val aggregate = aggregateWithStatus(WorkoutSessionStatus.Completed, pinned = "pv-1")
        val decision = WorkoutRecoveryRule.decide(aggregate, setOf("pv-1"))
        assertEquals(RecoveryDecision.NoOpenSession, decision)
    }

    @Test
    fun `terminal abandoned aggregate produces no open session`() {
        val aggregate = aggregateWithStatus(WorkoutSessionStatus.Abandoned, pinned = "pv-1")
        val decision = WorkoutRecoveryRule.decide(aggregate, setOf("pv-1"))
        assertEquals(RecoveryDecision.NoOpenSession, decision)
    }

    @Test
    fun `in-progress session with loaded pinned version resumes`() {
        val aggregate = aggregateWithStatus(WorkoutSessionStatus.InProgress, pinned = "pv-1")
        val decision = WorkoutRecoveryRule.decide(aggregate, setOf("pv-1", "pv-2"))
        assertEquals(RecoveryDecision.Resume(aggregate), decision)
    }

    @Test
    fun `in-progress session with missing pinned version routes to repair`() {
        val aggregate = aggregateWithStatus(WorkoutSessionStatus.InProgress, pinned = "pv-missing")
        val decision = WorkoutRecoveryRule.decide(aggregate, setOf("pv-1"))
        assertEquals(RecoveryDecision.Repair(aggregate, "pv-missing"), decision)
    }

    @Test
    fun `empty loaded set still allows repair routing, never auto-abandon`() {
        val aggregate = aggregateWithStatus(WorkoutSessionStatus.InProgress, pinned = "pv-1")
        val decision = WorkoutRecoveryRule.decide(aggregate, emptySet())
        assertEquals(RecoveryDecision.Repair(aggregate, "pv-1"), decision)
    }

    private fun aggregateWithStatus(
        status: WorkoutSessionStatus,
        pinned: String,
    ): WorkoutSessionAggregate {
        val metadata = SyncMetadata.forInsert(
            nowEpochMillis = 1_000L,
            deviceId = DeviceId("device-1"),
            mutationId = ClientMutationId("mut-1"),
        )
        val session = WorkoutSession(
            workoutSessionId = WorkoutSessionId("ws-1"),
            programRunId = ProgramRunId("run-1"),
            plannedOccurrenceId = "occ-1",
            pinnedProgramVersionId = pinned,
            status = status,
            startedAtEpochMillis = 1_000L,
            eventZoneId = "UTC",
            localDateEpochDay = 20_000L,
            completedAtEpochMillis = if (status == WorkoutSessionStatus.Completed) 2_000L else null,
            abandonedAtEpochMillis = if (status == WorkoutSessionStatus.Abandoned) 2_000L else null,
            lastSavedMutationId = ClientMutationId("mut-1"),
            syncMetadata = metadata,
        )
        return WorkoutSessionAggregate(session = session, exercises = emptyList())
    }

    @Suppress("unused")
    private val unusedWeightUnit = WeightUnit.Kg
}
