package dev.liftorium.domain.run

import dev.liftorium.core.IdGenerator

/**
 * Shared helper that walks the program structure week-by-week and
 * seeds one [ScheduleOccurrence] per planned session. Both
 * [StartProgramRun] and [RepeatProgramRun] use this helper so the
 * occurrence-seeding contract (chosen variant week wins; sessions
 * ordered by `sessionIndex`; `plannedEpochDay` increments by one per
 * session starting at [startEpochDay]) lives in exactly one place.
 *
 * The caller is responsible for translating any validation failures
 * (missing/invalid variant choice, missing runtime references) BEFORE
 * calling this helper — it assumes the prerequisites are satisfied.
 */
internal fun seedScheduleOccurrences(
    programRunId: ProgramRunId,
    startEpochDay: Long,
    chosenWeekVariants: Map<WeekVariantGroupKey, String>,
    prerequisites: ProgramVersionPrerequisites,
    idGenerator: IdGenerator,
): List<ScheduleOccurrence> {
    val seeded = mutableListOf<ScheduleOccurrence>()
    var dayOffset = 0
    for (slot in prerequisites.weekOrder) {
        val key = WeekVariantGroupKey(slot.blockId, slot.baseWeekId)
        val chosenWeekId = chosenWeekVariants[key] ?: slot.baseWeekId
        val sessions = prerequisites.sessionsByWeek[chosenWeekId] ?: emptyList()
        for (session in sessions) {
            seeded += ScheduleOccurrence(
                occurrenceId = idGenerator.newId(),
                programRunId = programRunId,
                plannedEpochDay = startEpochDay + dayOffset,
                actualCompletionEpochDay = null,
                blockId = slot.blockId,
                weekId = chosenWeekId,
                sessionId = session.sessionId,
                sessionIndex = session.sessionIndex,
                state = OccurrenceState.Planned,
            )
            dayOffset += 1
        }
    }
    return seeded
}
