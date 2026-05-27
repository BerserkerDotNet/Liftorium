package dev.liftorium.app.data

import dev.liftorium.app.ui.BlockUi
import dev.liftorium.app.ui.LiftoriumNavState
import dev.liftorium.app.ui.PendingReferenceRow
import dev.liftorium.app.ui.ProgramDetailUi
import dev.liftorium.app.ui.ProgramVersionRow
import dev.liftorium.app.ui.TodayItemUi
import dev.liftorium.app.ui.TodaySessionUi
import dev.liftorium.app.ui.VariantGroupUi
import dev.liftorium.app.ui.VariantOptionUi
import dev.liftorium.app.ui.WeekUi
import dev.liftorium.data.resource.LoadedExerciseCatalogEntryEntity
import dev.liftorium.data.resource.LoadedPrescriptionItemEntity
import dev.liftorium.data.resource.LoadedPrescriptionTargetEntity
import dev.liftorium.data.resource.LoadedProgramVersionDao
import dev.liftorium.data.resource.LoadedProgramVersionEntity
import dev.liftorium.data.resource.LoadedProgramWeekEntity
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.resource.LoadedSessionTemplateEntity
import dev.liftorium.data.resource.LoadedSetPrescriptionEntity
import dev.liftorium.data.run.ProgramRunDao
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * App-side bridge that reads finalized program-resource rows from
 * `:data` and assembles the Compose UI types declared in
 * `dev.liftorium.app.ui.UiState`. Replaces the sample-only
 * `bootstrapState()` shim with real Room-backed library content; the
 * debug `SampleStateFactory` stays in place for Paparazzi/Robolectric.
 */
public class ProgramLibraryRepository(
    private val versionDao: LoadedProgramVersionDao,
    private val runDao: ProgramRunDao,
) {

    public suspend fun snapshot(): LiftoriumNavState.Library {
        val versions = versionDao.listAllVersions()
        if (versions.isEmpty()) {
            return LiftoriumNavState.Library(persistentListOf(), emptyMap(), emptyMap())
        }
        val rows = mutableListOf<ProgramVersionRow>()
        val details = mutableMapOf<ProgramVersionId, ProgramDetailUi>()
        val todays = mutableMapOf<ProgramVersionId, TodaySessionUi>()
        for (v in versions) {
            rows += v.toRow()
            val detail = buildDetail(v)
            details[ProgramVersionId(v.programVersionId)] = detail
            buildPreviewToday(v)?.let { todays[ProgramVersionId(v.programVersionId)] = it }
        }
        return LiftoriumNavState.Library(
            versions = rows.toImmutableList(),
            details = details,
            todays = todays,
        )
    }

    /**
     * Resolve the "today" card for a freshly-activated run by walking
     * `schedule_occurrence` and picking the earliest row.
     */
    public suspend fun todayForRun(programRunId: ProgramRunId): TodaySessionUi? {
        val occurrences = runDao.listOccurrences(programRunId.value)
        val first = occurrences.firstOrNull() ?: return null
        val run = runDao.findById(programRunId.value) ?: return null
        val version = versionDao.findById(run.programVersionId) ?: return null
        val session = versionDao.listSessions(run.programVersionId)
            .firstOrNull { it.sessionId == first.sessionId } ?: return null
        return renderToday(
            programRunId = programRunId,
            plannedOccurrenceId = first.occurrenceId,
            plannedEpochDay = first.plannedEpochDay,
            version = version,
            session = session,
        )
    }

    private suspend fun buildPreviewToday(version: LoadedProgramVersionEntity): TodaySessionUi? {
        val raw = version.programVersionId
        val firstSession = versionDao.listSessions(raw).minByOrNull { it.sessionIndex } ?: return null
        return renderToday(
            programRunId = ProgramRunId("preview:$raw"),
            plannedOccurrenceId = "preview:${firstSession.sessionId}",
            plannedEpochDay = 0L,
            version = version,
            session = firstSession,
        )
    }

    private suspend fun buildDetail(version: LoadedProgramVersionEntity): ProgramDetailUi {
        val raw = version.programVersionId
        val blocks = versionDao.listBlocks(raw)
        val weeks = versionDao.listWeeks(raw)
        val sessions = versionDao.listSessions(raw)
        val refs = versionDao.listRequiredReferences(raw)
        val catalog = versionDao.listCatalogEntries(raw).associateBy { it.exerciseId }

        val weeksByBlock = weeks.groupBy { it.blockId }
        val sessionsByWeek = sessions.groupBy { it.weekId }

        val blockUis = blocks.sortedBy { it.blockOrder }.map { block ->
            val blockWeeks = (weeksByBlock[block.blockId].orEmpty()).sortedBy { it.weekIndex }
            val weekUis = blockWeeks.filter { it.variantOf == null }.map { week ->
                weekUiFor(week, sessionsByWeek[week.weekId].orEmpty())
            }
            BlockUi(
                blockId = block.blockId,
                displayName = block.displayName ?: "Block ${block.blockOrder}",
                weeks = weekUis.toImmutableList(),
            )
        }

        val variantGroupUis = blocks.flatMap { block ->
            val blockWeeks = weeksByBlock[block.blockId].orEmpty()
            blockWeeks.filter { it.variantOf == null }.mapNotNull { base ->
                val variants = blockWeeks.filter { it.variantOf == base.weekId }
                if (variants.isEmpty()) {
                    null
                } else {
                    val options = (listOf(base) + variants).map { week ->
                        VariantOptionUi(weekId = week.weekId, label = week.variantLabel ?: weekDisplayLabel(week))
                    }
                    VariantGroupUi(
                        key = WeekVariantGroupKey(blockId = block.blockId, baseWeekId = base.weekId),
                        baseLabel = base.variantLabel ?: weekDisplayLabel(base),
                        options = options.toImmutableList(),
                    )
                }
            }
        }

        val pendingRows = refs
            .filter { !it.supplied && it.referenceType in RUNTIME_REQUIRED_REFERENCE_TYPES }
            .map { it.toPendingRow(catalog) }
            .toImmutableList()

        return ProgramDetailUi(
            programVersionId = ProgramVersionId(raw),
            displayName = version.displayName ?: version.versionLabel,
            versionLabel = version.versionLabel,
            authorAttribution = version.authorAttribution,
            validationStatus = version.validationStatus,
            blocks = blockUis.toImmutableList(),
            pendingReferences = pendingRows,
            variantGroups = variantGroupUis.toImmutableList(),
        )
    }

    private fun weekUiFor(week: LoadedProgramWeekEntity, sessions: List<LoadedSessionTemplateEntity>): WeekUi {
        val titles = sessions.sortedBy { it.sessionIndex }
            .map { it.displayName ?: it.dayLabel ?: "Session ${it.sessionIndex}" }
        return WeekUi(
            weekId = week.weekId,
            label = weekDisplayLabel(week),
            sessionTitles = titles.toImmutableList(),
        )
    }

    private fun weekDisplayLabel(week: LoadedProgramWeekEntity): String =
        "Week ${week.weekIndex}" + if (week.variantLabel != null) " · ${week.variantLabel}" else ""

    private suspend fun renderToday(
        programRunId: ProgramRunId,
        plannedOccurrenceId: String,
        plannedEpochDay: Long,
        version: LoadedProgramVersionEntity,
        session: LoadedSessionTemplateEntity,
    ): TodaySessionUi {
        val raw = version.programVersionId
        val groups = versionDao.listGroups(raw)
            .filter { it.sessionId == session.sessionId }
            .sortedBy { it.groupOrder }
        val items = versionDao.listItems(raw).groupBy { it.groupId }
        val sets = versionDao.listSets(raw).groupBy { it.itemId }
        val targets = versionDao.listTargets(raw).groupBy { it.setId }
        val catalog = versionDao.listCatalogEntries(raw).associateBy { it.exerciseId }
        val refs = versionDao.listRequiredReferences(raw).associateBy { it.referenceId }

        val itemUis = groups.flatMap { group ->
            (items[group.groupId].orEmpty()).sortedBy { it.itemOrder }.map { item ->
                val itemSets = (sets[item.itemId].orEmpty()).sortedBy { it.setOrder }
                val lines = itemSets.map { set ->
                    renderSetLine(set, targets[set.setId].orEmpty(), refs, catalog)
                }
                TodayItemUi(
                    itemId = item.itemId,
                    exerciseName = catalog[item.prescribedExerciseId]?.displayName ?: item.prescribedExerciseId,
                    role = item.role,
                    setLines = lines.toImmutableList(),
                )
            }
        }

        return TodaySessionUi(
            programRunId = programRunId,
            plannedOccurrenceId = plannedOccurrenceId,
            programDisplayName = version.displayName ?: version.versionLabel,
            sessionTitle = session.displayName ?: session.dayLabel ?: "Session ${session.sessionIndex}",
            plannedEpochDay = plannedEpochDay,
            items = itemUis.toImmutableList(),
        )
    }

    private fun renderSetLine(
        set: LoadedSetPrescriptionEntity,
        setTargets: List<LoadedPrescriptionTargetEntity>,
        refs: Map<String, LoadedRequiredReferenceEntity>,
        catalog: Map<String, LoadedExerciseCatalogEntryEntity>,
    ): String {
        val first = setTargets.firstOrNull()
        return buildString {
            append(set.setKind.replaceFirstChar { it.uppercase() })
            if (first != null) {
                val reps = first.reps
                val pct = first.percent
                if (reps != null) append(" · $reps reps")
                if (pct != null) {
                    append(" · ${pct.toInt()}%")
                    val refId = first.referenceId
                    if (refId != null) {
                        val ref = refs[refId]
                        val tag = ref?.exerciseId?.let { catalog[it]?.displayName } ?: refId
                        append(" $tag")
                    }
                }
                if (first.amrap == true) append(" · AMRAP")
            }
        }
    }

    private fun LoadedProgramVersionEntity.toRow(): ProgramVersionRow = ProgramVersionRow(
        programVersionId = ProgramVersionId(programVersionId),
        displayName = displayName ?: versionLabel,
        versionLabel = versionLabel,
        authorAttribution = authorAttribution,
        validationStatus = validationStatus,
    )

    private fun LoadedRequiredReferenceEntity.toPendingRow(
        catalog: Map<String, LoadedExerciseCatalogEntryEntity>,
    ): PendingReferenceRow {
        val exerciseName = exerciseId?.let { catalog[it]?.displayName } ?: referenceId
        val resolvedUnit = when (unit?.lowercase()) {
            "kg" -> WeightUnit.Kg
            else -> WeightUnit.Lb
        }
        return PendingReferenceRow(
            referenceId = referenceId,
            displayLabel = exerciseName,
            referenceType = referenceType,
            defaultUnit = resolvedUnit,
        )
    }

    private companion object {
        private val RUNTIME_REQUIRED_REFERENCE_TYPES = setOf("one_rep_max")
    }
}
