package dev.liftorium.domain.run

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Repeat a previously completed or abandoned program run. Creates a
 * new `ProgramRun` row pinned to the same `programVersionId` and
 * `contentHash` as the previous run; runtime reference values and
 * week-variant choices are re-collected from the command (the UI may
 * pre-populate them from the previous run, but the use case treats
 * them as user input on every repeat).
 *
 * `RepeatProgramRun` requires the previous run to exist; its `status`
 * is irrelevant (you can repeat from any historical run).
 */
public class RepeatProgramRun(
    private val repository: ProgramRunRepository,
    private val timeSource: TimeSource,
    private val idGenerator: IdGenerator,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    public suspend operator fun invoke(
        previousProgramRunId: ProgramRunId,
        runtimeReferenceValues: Map<String, RuntimeReferenceValue>,
        chosenWeekVariants: Map<WeekVariantGroupKey, String>,
    ): RepeatProgramRunResult {
        val previous = repository.findRun(previousProgramRunId)
            ?: return RepeatProgramRunResult.Failure.UnknownPreviousRun(previousProgramRunId)

        val prerequisites = repository.loadPrerequisites(previous.programVersionId)
            ?: return RepeatProgramRunResult.Failure.UnknownPreviousRun(previousProgramRunId)

        val missingRefs = prerequisites.requiredFirstWeekReferenceIds - runtimeReferenceValues.keys
        if (missingRefs.isNotEmpty()) {
            return RepeatProgramRunResult.Failure.MissingRuntimeReferences(missingRefs)
        }

        val missingVariantGroups = prerequisites.weekVariantGroups.keys - chosenWeekVariants.keys
        if (missingVariantGroups.isNotEmpty()) {
            return RepeatProgramRunResult.Failure.MissingWeekVariantChoices(missingVariantGroups)
        }

        for ((group, chosenWeekId) in chosenWeekVariants) {
            val allowed = prerequisites.weekVariantGroups[group] ?: continue
            if (chosenWeekId !in allowed) {
                return RepeatProgramRunResult.Failure.InvalidWeekVariantChoice(group, chosenWeekId, allowed)
            }
        }

        val nowInstant = timeSource.now()
        val nowMillis = nowInstant.toEpochMilli()
        val startEpochDay = LocalDate.ofInstant(nowInstant, zoneId).toEpochDay()
        val newRunId = ProgramRunId(idGenerator.newId())

        val run = ProgramRun(
            programRunId = newRunId,
            programVersionId = previous.programVersionId,
            pinnedContentHash = previous.pinnedContentHash,
            startedAtEpochMillis = nowMillis,
            status = ProgramRunStatus.Active,
            chosenWeekVariants = chosenWeekVariants,
        )

        val runtimeValues = runtimeReferenceValues.map { (referenceId, value) ->
            ProgramRunReferenceValue(
                programRunId = newRunId,
                referenceId = referenceId,
                value = value.value,
                unit = value.unit,
                source = ReferenceValueSource.RuntimeInjection,
                suppliedAtEpochMillis = nowMillis,
            )
        }

        val seededOccurrences = seedScheduleOccurrences(
            programRunId = newRunId,
            startEpochDay = startEpochDay,
            chosenWeekVariants = chosenWeekVariants,
            prerequisites = prerequisites,
            idGenerator = idGenerator,
        )

        return when (repository.insertNewRun(run, runtimeValues, seededOccurrences)) {
            InsertRunOutcome.Success -> RepeatProgramRunResult.Success(run, seededOccurrences)
            InsertRunOutcome.AlreadyActiveRun -> RepeatProgramRunResult.Failure.AlreadyActiveRun
        }
    }
}

/**
 * Abandon the given program run. Sets `status=Abandoned` and nulls
 * `activeRunSlot`. Seeded occurrences keep their state (they remain
 * visible in run history).
 */
public class AbandonProgramRun(
    private val repository: ProgramRunRepository,
) {

    public suspend operator fun invoke(programRunId: ProgramRunId): AbandonProgramRunResult {
        val existing = repository.findRun(programRunId)
            ?: return AbandonProgramRunResult.Failure.UnknownRun(programRunId)

        if (existing.status != ProgramRunStatus.Active) {
            return AbandonProgramRunResult.Failure.NotActive(programRunId, existing.status)
        }

        val updated = repository.markAbandoned(programRunId)
            ?: return AbandonProgramRunResult.Failure.UnknownRun(programRunId)
        return AbandonProgramRunResult.Success(updated)
    }
}
