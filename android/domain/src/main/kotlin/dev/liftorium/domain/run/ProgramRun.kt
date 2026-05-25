package dev.liftorium.domain.run

import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit

/**
 * Status of a program run. Exactly one run may be `Active` at a time;
 * the DB enforces this invariant via the unique index on
 * `ProgramRunEntity.activeRunSlot` (see ADR
 * "One active program run enforced by DB unique index on activeRunSlot",
 * `docs/decisions.md` 2026-05-17).
 */
@KoverIgnore
public enum class ProgramRunStatus {
    Active,
    Completed,
    Abandoned,
}

/**
 * State of one scheduled occurrence within a program run.
 */
@KoverIgnore
public enum class OccurrenceState {
    Planned,
    Completed,
    Skipped,
    Rescheduled,
}

/**
 * Provenance of a runtime reference value. The data model stores ONLY
 * `RuntimeInjection` rows in `ProgramRunReferenceValueEntity`;
 * `OperatorImport` exists for symmetry in the resolved-value domain
 * type. See ADR "Pending-reference runtime values are run-scoped,
 * not version-scoped".
 */
@KoverIgnore
public enum class ReferenceValueSource {
    OperatorImport,
    RuntimeInjection,
}

/**
 * A program run pins an immutable program version.
 */
@KoverIgnore
public data class ProgramRun(
    val programRunId: ProgramRunId,
    val programVersionId: ProgramVersionId,
    val pinnedContentHash: String,
    val startedAtEpochMillis: Long,
    val status: ProgramRunStatus,
    val chosenWeekVariants: Map<WeekVariantGroupKey, String>,
)

/**
 * Composite key for the chosen-week-variant map.
 */
@KoverIgnore
public data class WeekVariantGroupKey(
    val blockId: String,
    val baseWeekId: String,
)

/**
 * One workout occurrence inside a program run. `plannedEpochDay` and
 * `actualCompletionEpochDay` are encoded as local-zone epoch day to
 * keep date math timezone-stable.
 */
@KoverIgnore
public data class ScheduleOccurrence(
    val occurrenceId: String,
    val programRunId: ProgramRunId,
    val plannedEpochDay: Long,
    val actualCompletionEpochDay: Long?,
    val blockId: String,
    val weekId: String,
    val sessionId: String,
    val sessionIndex: Int,
    val state: OccurrenceState,
)

/**
 * Runtime-injected training-max / 1RM value scoped to a program run.
 */
@KoverIgnore
public data class ProgramRunReferenceValue(
    val programRunId: ProgramRunId,
    val referenceId: String,
    val value: Double,
    val unit: WeightUnit,
    val source: ReferenceValueSource,
    val suppliedAtEpochMillis: Long,
)
