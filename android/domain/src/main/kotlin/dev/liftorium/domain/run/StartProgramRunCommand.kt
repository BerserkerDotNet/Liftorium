package dev.liftorium.domain.run

import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit

/**
 * Command to start a new program run for a loaded program version.
 *
 * `runtimeReferenceValues` carries the user-supplied training-max /
 * 1RM values gathered from the activation pending-references dialog.
 * The use case writes them as `ProgramRunReferenceValueEntity` rows in
 * the same Room transaction as the `ProgramRunEntity` insert.
 *
 * `chosenWeekVariants` carries the user's pick for every multi-variant
 * `(blockId, baseWeekId)` group. May be empty if the program has no
 * variant weeks.
 */
@KoverIgnore
public data class StartProgramRunCommand(
    val programVersionId: ProgramVersionId,
    val runtimeReferenceValues: Map<String, RuntimeReferenceValue>,
    val chosenWeekVariants: Map<WeekVariantGroupKey, String>,
)

@KoverIgnore
public data class RuntimeReferenceValue(
    val value: Double,
    val unit: WeightUnit,
)

/**
 * Required prerequisites for a program version to be runnable; computed
 * by the repository from the loaded version's required references and
 * week-variant structure. The use case asserts that the command
 * satisfies these.
 *
 * `weekOrder` is the canonical week ordering for occurrence seeding
 * (blocks by `order`, weeks by `weekIndex`; one entry per weekly slot
 * keyed by `(blockId, baseWeekId)`). `sessionsByWeek` exposes sessions
 * for every concrete `weekId` (base + variants). The use case selects
 * the effective `weekId` for each slot from the user's variant choices
 * (falling back to `baseWeekId` for groups with a single member) and
 * emits one `ScheduleOccurrence` per session with one calendar day
 * between consecutive sessions (android-program-runner MVP calendar policy).
 */
@KoverIgnore
public data class ProgramVersionPrerequisites(
    val programVersionId: ProgramVersionId,
    val pinnedContentHash: String,
    val requiredFirstWeekReferenceIds: Set<String>,
    val weekVariantGroups: Map<WeekVariantGroupKey, Set<String>>,
    val weekOrder: List<WeekSlot>,
    val sessionsByWeek: Map<String, List<PlannedSession>>,
)

/**
 * One weekly slot in the occurrence-seeding order. `baseWeekId` is the
 * `weekId` of the week with no `variantOf` (or the only week in the
 * group). The use case picks the effective `weekId` by looking up
 * `chosenWeekVariants[WeekVariantGroupKey(blockId, baseWeekId)]`,
 * falling back to `baseWeekId` when no choice is required.
 */
@KoverIgnore
public data class WeekSlot(
    val blockId: String,
    val baseWeekId: String,
)

/**
 * One session inside a concrete week. `sessionIndex` is the canonical
 * ordering inside its week.
 */
@KoverIgnore
public data class PlannedSession(
    val sessionId: String,
    val sessionIndex: Int,
)

/**
 * Outcome of a `StartProgramRun` invocation.
 */
public sealed interface StartProgramRunResult {
    public data class Success(
        val run: ProgramRun,
        val seededOccurrences: List<ScheduleOccurrence>,
    ) : StartProgramRunResult

    public sealed interface Failure : StartProgramRunResult {
        public data class UnknownProgramVersion(val programVersionId: ProgramVersionId) : Failure
        public data class MissingRuntimeReferences(val referenceIds: Set<String>) : Failure
        public data class MissingWeekVariantChoices(val groups: Set<WeekVariantGroupKey>) : Failure
        public data class InvalidWeekVariantChoice(
            val group: WeekVariantGroupKey,
            val chosen: String,
            val allowed: Set<String>,
        ) : Failure
        public data object AlreadyActiveRun : Failure
    }
}

/**
 * Outcome of a `RepeatProgramRun` invocation. Reuses
 * [StartProgramRunResult] for success; the failure subtype adds
 * `UnknownPreviousRun`.
 */
public sealed interface RepeatProgramRunResult {
    public data class Success(
        val run: ProgramRun,
        val seededOccurrences: List<ScheduleOccurrence>,
    ) : RepeatProgramRunResult

    public sealed interface Failure : RepeatProgramRunResult {
        public data class UnknownPreviousRun(val programRunId: ProgramRunId) : Failure
        public data class MissingRuntimeReferences(val referenceIds: Set<String>) : Failure
        public data class MissingWeekVariantChoices(val groups: Set<WeekVariantGroupKey>) : Failure
        public data class InvalidWeekVariantChoice(
            val group: WeekVariantGroupKey,
            val chosen: String,
            val allowed: Set<String>,
        ) : Failure
        public data object AlreadyActiveRun : Failure
    }
}

/**
 * Outcome of an `AbandonProgramRun` invocation.
 */
public sealed interface AbandonProgramRunResult {
    public data class Success(val run: ProgramRun) : AbandonProgramRunResult

    public sealed interface Failure : AbandonProgramRunResult {
        public data class UnknownRun(val programRunId: ProgramRunId) : Failure
        public data class NotActive(val programRunId: ProgramRunId, val status: ProgramRunStatus) : Failure
    }
}
