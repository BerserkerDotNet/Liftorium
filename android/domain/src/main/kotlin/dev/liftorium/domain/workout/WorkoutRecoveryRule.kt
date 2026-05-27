package dev.liftorium.domain.workout

import dev.liftorium.core.KoverIgnore

/**
 * Pure function that classifies a recovered session aggregate into the
 * UI-facing recovery decision. Lives in `:domain.workout` so the rule
 * is testable without spinning up a database or ViewModel.
 *
 * Recovery contract (`docs/architecture.md` Recovery flow):
 *  * No open session → [RecoveryDecision.NoOpenSession] (home / today).
 *  * Open session whose pinned program version matches the loaded
 *    program version set → [RecoveryDecision.Resume].
 *  * Open session whose pinned program version is no longer loaded
 *    (resource deleted/corrupted/re-imported with a hash mismatch) →
 *    [RecoveryDecision.Repair]. Raw logs are NEVER deleted; the UI
 *    routes to a Repair screen instead.
 *
 * The rule deliberately does NOT auto-abandon: per the 2026-05-25 ADR
 * "Recovery Repair-screen policy preserves raw logs", auto-abandon is
 * destructive and silent. The Repair screen is the user-visible
 * surface for the rare case where a pinned version is unavailable.
 */
public object WorkoutRecoveryRule {

    public fun decide(
        aggregate: WorkoutSessionAggregate?,
        loadedProgramVersionIds: Set<String>,
    ): RecoveryDecision {
        if (aggregate == null) return RecoveryDecision.NoOpenSession
        val session = aggregate.session
        if (session.status != WorkoutSessionStatus.InProgress) {
            // Terminal sessions are not "open" for recovery purposes;
            // they may still appear in history queries owned by stats.
            return RecoveryDecision.NoOpenSession
        }
        return if (session.pinnedProgramVersionId in loadedProgramVersionIds) {
            RecoveryDecision.Resume(aggregate)
        } else {
            RecoveryDecision.Repair(aggregate, session.pinnedProgramVersionId)
        }
    }
}

@KoverIgnore
public sealed interface RecoveryDecision {
    public data object NoOpenSession : RecoveryDecision
    public data class Resume(val aggregate: WorkoutSessionAggregate) : RecoveryDecision
    public data class Repair(
        val aggregate: WorkoutSessionAggregate,
        val missingProgramVersionId: String,
    ) : RecoveryDecision
}
