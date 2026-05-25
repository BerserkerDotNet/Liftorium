package dev.liftorium.domain.run

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Start a new program run for a loaded program version.
 *
 * Orchestration steps:
 *
 * 1. Look up prerequisites from the repository (must exist; the loader
 *    has already validated and persisted the version).
 * 2. Assert the command supplies a runtime value for every first-week
 *    required reference.
 * 3. Assert the command supplies a valid choice for every multi-variant
 *    `(blockId, baseWeekId)` group.
 * 4. Build the run, runtime-value rows, and seeded occurrences.
 * 5. Hand the bundle to the repository, which writes them atomically
 *    and translates a unique-constraint failure on `activeRunSlot`
 *    into [StartProgramRunResult.Failure.AlreadyActiveRun].
 */
public class StartProgramRun(
    private val repository: ProgramRunRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    public suspend operator fun invoke(command: StartProgramRunCommand): StartProgramRunResult {
        val prerequisites = repository.loadPrerequisites(command.programVersionId)
            ?: return StartProgramRunResult.Failure.UnknownProgramVersion(command.programVersionId)

        val missingRefs = prerequisites.requiredFirstWeekReferenceIds - command.runtimeReferenceValues.keys
        if (missingRefs.isNotEmpty()) {
            return StartProgramRunResult.Failure.MissingRuntimeReferences(missingRefs)
        }

        val missingVariantGroups = prerequisites.weekVariantGroups.keys - command.chosenWeekVariants.keys
        if (missingVariantGroups.isNotEmpty()) {
            return StartProgramRunResult.Failure.MissingWeekVariantChoices(missingVariantGroups)
        }

        for ((group, chosenWeekId) in command.chosenWeekVariants) {
            val allowed = prerequisites.weekVariantGroups[group] ?: continue
            if (chosenWeekId !in allowed) {
                return StartProgramRunResult.Failure.InvalidWeekVariantChoice(group, chosenWeekId, allowed)
            }
        }

        val nowInstant = timeSource.now()
        val nowMillis = nowInstant.toEpochMilli()
        val startEpochDay = LocalDate.ofInstant(nowInstant, zoneId).toEpochDay()
        val runId = ProgramRunId(idGenerator.newId())

        val run = ProgramRun(
            programRunId = runId,
            programVersionId = command.programVersionId,
            pinnedContentHash = prerequisites.pinnedContentHash,
            startedAtEpochMillis = nowMillis,
            status = ProgramRunStatus.Active,
            chosenWeekVariants = command.chosenWeekVariants,
        )

        val runtimeValues = command.runtimeReferenceValues.map { (referenceId, value) ->
            ProgramRunReferenceValue(
                programRunId = runId,
                referenceId = referenceId,
                value = value.value,
                unit = value.unit,
                source = ReferenceValueSource.RuntimeInjection,
                suppliedAtEpochMillis = nowMillis,
            )
        }

        val seededOccurrences = seedScheduleOccurrences(
            programRunId = runId,
            startEpochDay = startEpochDay,
            chosenWeekVariants = command.chosenWeekVariants,
            prerequisites = prerequisites,
            idGenerator = idGenerator,
        )

        return when (repository.insertNewRun(run, runtimeValues, seededOccurrences)) {
            InsertRunOutcome.Success -> StartProgramRunResult.Success(run, seededOccurrences)
            InsertRunOutcome.AlreadyActiveRun -> StartProgramRunResult.Failure.AlreadyActiveRun
        }
    }
}
