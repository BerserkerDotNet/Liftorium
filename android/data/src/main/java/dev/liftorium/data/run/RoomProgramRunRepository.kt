package dev.liftorium.data.run

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import dev.liftorium.core.TimeSource
import dev.liftorium.data.resource.LoadedProgramVersionDao
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.InsertRunOutcome
import dev.liftorium.domain.run.OccurrenceState
import dev.liftorium.domain.run.PlannedSession
import dev.liftorium.domain.run.ProgramRun
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.run.ProgramRunReferenceValue
import dev.liftorium.domain.run.ProgramRunRepository
import dev.liftorium.domain.run.ProgramRunStatus
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.ProgramVersionPrerequisites
import dev.liftorium.domain.run.ReferenceValueSource
import dev.liftorium.domain.run.ScheduleOccurrence
import dev.liftorium.domain.run.WeekSlot
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room-backed implementation of [ProgramRunRepository]. Computes
 * activation prerequisites by reading the loaded program version's
 * required references, week tree, and session order; writes new runs
 * as a single transactional bundle that fails closed on the
 * `activeRunSlot` unique index when another Active run exists.
 */
public class RoomProgramRunRepository(
    private val runDao: ProgramRunDao,
    private val versionDao: LoadedProgramVersionDao,
    private val timeSource: TimeSource,
    private val json: Json = DEFAULT_JSON,
) : ProgramRunRepository {

    override suspend fun loadPrerequisites(programVersionId: ProgramVersionId): ProgramVersionPrerequisites? {
        val rawId = programVersionId.value
        val version = versionDao.findById(rawId) ?: return null

        val requiredRefs = versionDao.listRequiredReferences(rawId)
        val requiredFirstWeekReferenceIds = requiredRefs
            .filter {
                !it.supplied &&
                    it.firstRunnableWeekIndex == FIRST_WEEK_INDEX &&
                    it.referenceType in RUNTIME_REQUIRED_REFERENCE_TYPES
            }
            .map { it.referenceId }
            .toSet()

        val blocks = versionDao.listBlocks(rawId).sortedBy { it.blockOrder }
        val weeks = versionDao.listWeeks(rawId)
        val sessions = versionDao.listSessions(rawId)

        val weeksByBlock = weeks.groupBy { it.blockId }
        val sessionsByWeekId = sessions.groupBy { it.weekId }

        val weekOrder = mutableListOf<WeekSlot>()
        val variantGroups = mutableMapOf<WeekVariantGroupKey, MutableSet<String>>()

        for (block in blocks) {
            val blockWeeks = weeksByBlock[block.blockId].orEmpty()
            val baseWeeks = blockWeeks
                .filter { it.variantOf == null }
                .sortedBy { it.weekIndex }
            for (baseWeek in baseWeeks) {
                weekOrder += WeekSlot(blockId = block.blockId, baseWeekId = baseWeek.weekId)
                val variants = blockWeeks.filter { it.variantOf == baseWeek.weekId }
                val members = mutableSetOf(baseWeek.weekId)
                members.addAll(variants.map { it.weekId })
                if (members.size > 1) {
                    variantGroups[WeekVariantGroupKey(block.blockId, baseWeek.weekId)] = members
                }
            }
        }

        val sessionsByWeek: Map<String, List<PlannedSession>> = sessionsByWeekId.mapValues { (_, list) ->
            list.sortedBy { it.sessionIndex }
                .map { PlannedSession(sessionId = it.sessionId, sessionIndex = it.sessionIndex) }
        }

        return ProgramVersionPrerequisites(
            programVersionId = ProgramVersionId(version.programVersionId),
            pinnedContentHash = version.contentHash,
            requiredFirstWeekReferenceIds = requiredFirstWeekReferenceIds,
            weekVariantGroups = variantGroups,
            weekOrder = weekOrder,
            sessionsByWeek = sessionsByWeek,
        )
    }

    override suspend fun insertNewRun(
        run: ProgramRun,
        runtimeReferenceValues: List<ProgramRunReferenceValue>,
        seededOccurrences: List<ScheduleOccurrence>,
    ): InsertRunOutcome {
        return try {
            runDao.insertRunBundle(
                run = run.toEntity(json),
                referenceValues = runtimeReferenceValues.map { it.toEntity() },
                occurrences = seededOccurrences.map { it.toEntity() },
            )
            InsertRunOutcome.Success
        } catch (constraintFailure: SQLiteConstraintException) {
            // Only treat the failure as "another Active run blocks us"
            // if there really is another Active row. Any other
            // constraint violation (PK collision, FK to a missing
            // program version, NOT NULL on a required column) signals
            // a programming or schema bug that must NOT be silently
            // labelled as a duplicate-run conflict; rethrow so the
            // caller sees the real cause.
            val active = runDao.findActiveRun()
            if (active != null && active.programRunId != run.programRunId.value) {
                InsertRunOutcome.AlreadyActiveRun
            } else {
                Log.e(LOG_TAG, "insertNewRun: unexpected constraint failure", constraintFailure)
                throw constraintFailure
            }
        }
    }

    override suspend fun findRun(programRunId: ProgramRunId): ProgramRun? {
        val entity = runDao.findById(programRunId.value) ?: return null
        return entity.toDomainSafe(json)
    }

    override suspend fun markAbandoned(programRunId: ProgramRunId): ProgramRun? {
        val updated = runDao.markAbandonedAndReturn(
            programRunId = programRunId.value,
            status = ProgramRunStatus.Abandoned.wire(),
            updatedAtEpochMillis = timeSource.now().toEpochMilli(),
        ) ?: return null
        return updated.toDomainSafe(json)
    }

    private fun ProgramRunEntity.toDomainSafe(json: Json): ProgramRun? = try {
        toDomain(json)
    } catch (corruption: CorruptProgramRunStatusException) {
        Log.e(
            LOG_TAG,
            "Skipping program_run ${corruption.programRunId} with corrupt status wire " +
                "value '${corruption.wireValue}'",
            corruption,
        )
        null
    }

    public companion object {
        private const val FIRST_WEEK_INDEX = 1
        private const val LOG_TAG = "RoomProgramRunRepo"
        private val RUNTIME_REQUIRED_REFERENCE_TYPES = setOf("one_rep_max")

        /**
         * Stable JSON config for serialising `chosenWeekVariants`. The
         * map shape `{blockId → {baseWeekId → chosenWeekId}}` matches
         * the durability contract from the android-program-runner plan.
         */
        public val DEFAULT_JSON: Json = Json {
            prettyPrint = false
            encodeDefaults = true
        }
    }
}

internal fun ProgramRun.toEntity(json: Json): ProgramRunEntity = ProgramRunEntity(
    programRunId = programRunId.value,
    programVersionId = programVersionId.value,
    pinnedContentHash = pinnedContentHash,
    startedAtEpochMillis = startedAtEpochMillis,
    status = status.wire(),
    chosenWeekVariantsJson = encodeChosenWeekVariants(chosenWeekVariants, json),
    activeRunSlot = if (status == ProgramRunStatus.Active) 1L else null,
    // `updatedAtEpochMillis` audit column was introduced in schema v2;
    // new rows carry the same timestamp as their initial insertion until
    // a subsequent mutation (e.g. `markAbandoned`) bumps it.
    updatedAtEpochMillis = startedAtEpochMillis,
)

internal fun ProgramRunEntity.toDomain(json: Json): ProgramRun = ProgramRun(
    programRunId = ProgramRunId(programRunId),
    programVersionId = ProgramVersionId(programVersionId),
    pinnedContentHash = pinnedContentHash,
    startedAtEpochMillis = startedAtEpochMillis,
    status = programRunStatusFromWire(status, programRunId),
    chosenWeekVariants = decodeChosenWeekVariants(chosenWeekVariantsJson, json),
)

internal fun ProgramRunReferenceValue.toEntity(): ProgramRunReferenceValueEntity =
    ProgramRunReferenceValueEntity(
        programRunId = programRunId.value,
        referenceId = referenceId,
        value = value,
        unit = when (unit) {
            WeightUnit.Kg -> "kg"
            WeightUnit.Lb -> "lb"
        },
        source = when (source) {
            ReferenceValueSource.OperatorImport -> "operator_import"
            ReferenceValueSource.RuntimeInjection -> "runtime_injection"
        },
        suppliedAtEpochMillis = suppliedAtEpochMillis,
    )

internal fun ScheduleOccurrence.toEntity(): ScheduleOccurrenceEntity = ScheduleOccurrenceEntity(
    programRunId = programRunId.value,
    occurrenceId = occurrenceId,
    plannedEpochDay = plannedEpochDay,
    actualCompletionEpochDay = actualCompletionEpochDay,
    blockId = blockId,
    weekId = weekId,
    sessionId = sessionId,
    sessionIndex = sessionIndex,
    state = when (state) {
        OccurrenceState.Planned -> "planned"
        OccurrenceState.Completed -> "completed"
        OccurrenceState.Skipped -> "skipped"
        OccurrenceState.Rescheduled -> "rescheduled"
    },
    // `updatedAtEpochMillis` was added in schema v2. The seeded
    // occurrences are stamped with `0L` at insert time because
    // `ScheduleOccurrence` (domain) has no audit timestamp and the
    // initial seeding's "creation" time equals the run's start; once
    // the workout-logging workstream begins mutating occurrence rows
    // each mutation will set this column to the wall-clock time of
    // the change.
    updatedAtEpochMillis = 0L,
)

internal fun ProgramRunStatus.wire(): String = when (this) {
    ProgramRunStatus.Active -> "active"
    ProgramRunStatus.Completed -> "completed"
    ProgramRunStatus.Abandoned -> "abandoned"
}

internal fun programRunStatusFromWire(wire: String, programRunId: String): ProgramRunStatus = when (wire) {
    "active" -> ProgramRunStatus.Active
    "completed" -> ProgramRunStatus.Completed
    "abandoned" -> ProgramRunStatus.Abandoned
    else -> throw CorruptProgramRunStatusException(programRunId = programRunId, wireValue = wire)
}

/**
 * Signals that a `program_run.status` cell carries a wire value that
 * does not match the closed `ProgramRunStatus` enum. Raised by
 * [programRunStatusFromWire] when row-level corruption is detected;
 * the repository logs the row id and returns `null` to upstream
 * callers rather than crashing the entire query.
 */
internal class CorruptProgramRunStatusException(
    val programRunId: String,
    val wireValue: String,
) : IllegalStateException(
    "program_run.status carries unknown wire value '$wireValue' for programRunId=$programRunId",
)

internal fun encodeChosenWeekVariants(
    choices: Map<WeekVariantGroupKey, String>,
    json: Json,
): String {
    val nested: Map<String, Map<String, String>> = choices.entries
        .groupBy { it.key.blockId }
        .mapValues { (_, entries) -> entries.associate { it.key.baseWeekId to it.value } }
    return json.encodeToString(WEEK_VARIANTS_SERIALIZER, nested)
}

internal fun decodeChosenWeekVariants(
    raw: String,
    json: Json,
): Map<WeekVariantGroupKey, String> {
    if (raw.isBlank()) return emptyMap()
    val nested: Map<String, Map<String, String>> = json.decodeFromString(WEEK_VARIANTS_SERIALIZER, raw)
    return buildMap {
        for ((blockId, inner) in nested) {
            for ((baseWeekId, chosenWeekId) in inner) {
                put(WeekVariantGroupKey(blockId, baseWeekId), chosenWeekId)
            }
        }
    }
}

private val WEEK_VARIANTS_SERIALIZER = MapSerializer(
    String.serializer(),
    MapSerializer(String.serializer(), String.serializer()),
)
