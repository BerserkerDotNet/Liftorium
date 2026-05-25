package dev.liftorium.domain.run

import dev.liftorium.domain.common.ProgramVersionId

import dev.liftorium.core.IdGenerator
import dev.liftorium.core.TimeSource
import java.time.Instant

internal fun fixedTimeSource(instant: Instant): TimeSource = object : TimeSource {
    override fun now(): Instant = instant
}

internal fun sequencedIdGenerator(vararg ids: String): IdGenerator = object : IdGenerator {
    private val iter = ids.iterator()
    override fun newId(): String = iter.next()
}

internal class RecordingFakeRepository : ProgramRunRepository {
    var storedPrerequisites: ProgramVersionPrerequisites? = null
    var storedRun: ProgramRun? = null
    var insertOutcome: InsertRunOutcome = InsertRunOutcome.Success
    var abandonReturns: ProgramRun? = null
    val insertedRuns: MutableList<ProgramRun> = mutableListOf()
    val insertedValues: MutableList<List<ProgramRunReferenceValue>> = mutableListOf()
    val insertedOccurrences: MutableList<List<ScheduleOccurrence>> = mutableListOf()

    override suspend fun loadPrerequisites(programVersionId: ProgramVersionId): ProgramVersionPrerequisites? {
        return storedPrerequisites?.takeIf { it.programVersionId == programVersionId }
    }

    override suspend fun insertNewRun(
        run: ProgramRun,
        runtimeReferenceValues: List<ProgramRunReferenceValue>,
        seededOccurrences: List<ScheduleOccurrence>,
    ): InsertRunOutcome {
        if (insertOutcome == InsertRunOutcome.Success) {
            insertedRuns += run
            insertedValues += runtimeReferenceValues
            insertedOccurrences += seededOccurrences
        }
        return insertOutcome
    }

    override suspend fun findRun(programRunId: ProgramRunId): ProgramRun? {
        return storedRun?.takeIf { it.programRunId == programRunId }
    }

    override suspend fun markAbandoned(programRunId: ProgramRunId): ProgramRun? {
        if (storedRun?.programRunId != programRunId) return null
        return abandonReturns ?: storedRun?.copy(status = ProgramRunStatus.Abandoned)
    }
}
