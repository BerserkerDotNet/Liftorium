package dev.liftorium.domain.run

import dev.liftorium.domain.common.ProgramVersionId

/**
 * Repository boundary for the program-run aggregate. Implementations
 * live in `:data` and own the Room transaction that inserts a run, its
 * runtime reference values, and its seeded occurrences atomically.
 *
 * All write operations are atomic at the repository boundary; partial
 * failures must roll back. Read operations are flow-based to support
 * Compose state collection in upstream UI.
 */
public interface ProgramRunRepository {

    /**
     * Returns the prerequisites computed from the loaded program version
     * (first-week required references that must be supplied at runtime
     * + week-variant choice groups + ordered session plan), or `null`
     * when the version is not loaded.
     */
    public suspend fun loadPrerequisites(programVersionId: ProgramVersionId): ProgramVersionPrerequisites?

    /**
     * Inserts a new program run, its runtime reference values, and the
     * seeded schedule occurrences in a single Room transaction.
     *
     * The `activeRunSlot` unique index enforces the one-active-run
     * invariant; if a concurrent insert wins the race, this returns
     * [StartProgramRunResult.Failure.AlreadyActiveRun]. Domain-level
     * validation (missing TMs, missing variant choices) is performed by
     * [StartProgramRun] before the repository is invoked.
     */
    public suspend fun insertNewRun(
        run: ProgramRun,
        runtimeReferenceValues: List<ProgramRunReferenceValue>,
        seededOccurrences: List<ScheduleOccurrence>,
    ): InsertRunOutcome

    /**
     * Returns the run with the given id, or `null` when not found.
     */
    public suspend fun findRun(programRunId: ProgramRunId): ProgramRun?

    /**
     * Marks the given run `Abandoned` and nulls its `activeRunSlot`.
     * Returns the updated row, or `null` when no row exists.
     */
    public suspend fun markAbandoned(programRunId: ProgramRunId): ProgramRun?
}

/**
 * Coarse-grained outcome of [ProgramRunRepository.insertNewRun]. The
 * [AlreadyActiveRun] case is the typed surface for a unique-constraint
 * failure on `activeRunSlot`.
 */
public sealed interface InsertRunOutcome {
    public data object Success : InsertRunOutcome
    public data object AlreadyActiveRun : InsertRunOutcome
}
