package dev.liftorium.data.resource

import dev.liftorium.domain.resource.ExerciseAlias
import dev.liftorium.domain.resource.ExerciseCatalogEntry
import dev.liftorium.domain.resource.ExerciseEquipment
import dev.liftorium.domain.resource.ExerciseFamily
import dev.liftorium.domain.resource.GroupKind
import dev.liftorium.domain.resource.ImportAudit
import dev.liftorium.domain.resource.PrescriptionNote
import dev.liftorium.domain.resource.PrescriptionRole
import dev.liftorium.domain.resource.PrescriptionTarget
import dev.liftorium.domain.resource.ProgramResource
import dev.liftorium.domain.resource.ProgramResourceJson
import dev.liftorium.domain.resource.ReferenceType
import dev.liftorium.domain.resource.RoundingOverride
import dev.liftorium.domain.resource.SetKind
import dev.liftorium.domain.resource.ValidationIssue
import dev.liftorium.domain.resource.ValidationStatus
import dev.liftorium.domain.common.WeightUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Pure-function mapper that converts a parsed [ProgramResource] DTO into
 * the relational [LoadedProgramVersionBundle] persisted by
 * [LoadedProgramVersionDao.loadFullVersion].
 *
 * Enum-shaped fields are encoded with their JSON wire string (the
 * `@SerialName` declared in `:domain`) by round-tripping through
 * kotlinx.serialization. This guarantees the persisted form always
 * matches the source artifact even if the Kotlin enum constant name
 * differs from the wire name. Opaque sub-trees (importAudit,
 * validationIssues, programDefaults, aliases, notes, progression-rule
 * parameters) are stored as JSON strings; android-program-runner has no query
 * patterns that read these individually.
 */
internal object ProgramResourceMapper {

    fun toBundle(
        resource: ProgramResource,
        loadedAtEpochMillis: Long,
    ): LoadedProgramVersionBundle {
        val pvId = resource.metadata.programVersionId

        val version = LoadedProgramVersionEntity(
            programVersionId = pvId,
            programId = resource.metadata.programId,
            versionLabel = resource.metadata.versionLabel,
            displayName = resource.metadata.displayName,
            authorAttribution = resource.metadata.authorAttribution,
            contentHash = resource.metadata.contentHash,
            schemaVersion = resource.schemaVersion,
            validationStatus = wire(resource.validationStatus, ValidationStatus.serializer()),
            loadedAtEpochMillis = loadedAtEpochMillis,
            programDefaultsJson = resource.programDefaults?.let(::encodeRoundingOverride),
            programStructureRoundingOverrideJson = resource.programStructure.roundingOverride?.let(::encodeRoundingOverride),
            importAuditJson = encodeImportAudit(resource.importAudit),
            validationIssuesJson = encodeValidationIssues(resource.validationIssues),
        )

        val catalog = resource.exerciseCatalog.map { entry ->
            LoadedExerciseCatalogEntryEntity(
                programVersionId = pvId,
                exerciseId = entry.id,
                displayName = entry.displayName,
                family = wire(entry.family, ExerciseFamily.serializer()),
                equipment = wire(entry.equipment, ExerciseEquipment.serializer()),
                defaultRoundingIncrement = entry.defaultRoundingIncrement,
                defaultRoundingUnit = entry.defaultRoundingUnit?.let { wire(it, WeightUnit.serializer()) },
                aliasesJson = encodeAliases(entry),
            )
        }

        val refs = resource.requiredReferences.map { ref ->
            LoadedRequiredReferenceEntity(
                programVersionId = pvId,
                referenceId = ref.id,
                referenceType = wire(ref.referenceType, ReferenceType.serializer()),
                exerciseId = ref.exerciseId,
                firstRunnableWeekIndex = ref.firstRunnableWeekIndex,
                supplied = ref.supplied,
                value = ref.value,
                unit = ref.unit?.let { wire(it, WeightUnit.serializer()) },
            )
        }

        val rules = resource.progressionRules.map { rule ->
            LoadedProgressionRuleEntity(
                programVersionId = pvId,
                ruleId = rule.id,
                kind = rule.kind,
                appliesToExerciseIdsJson = encodeStringList(rule.appliesToExerciseIds),
                parametersJson = rule.parameters?.toString(),
            )
        }

        val blocks = mutableListOf<LoadedProgramBlockEntity>()
        val weeks = mutableListOf<LoadedProgramWeekEntity>()
        val sessions = mutableListOf<LoadedSessionTemplateEntity>()
        val groups = mutableListOf<LoadedExerciseGroupEntity>()
        val items = mutableListOf<LoadedPrescriptionItemEntity>()
        val sets = mutableListOf<LoadedSetPrescriptionEntity>()
        val targets = mutableListOf<LoadedPrescriptionTargetEntity>()

        for (block in resource.programStructure.blocks) {
            blocks.add(
                LoadedProgramBlockEntity(
                    programVersionId = pvId,
                    blockId = block.id,
                    blockOrder = block.order,
                    displayName = block.displayName,
                    roundingOverrideJson = block.roundingOverride?.let(::encodeRoundingOverride),
                ),
            )
            for (week in block.weeks) {
                weeks.add(
                    LoadedProgramWeekEntity(
                        programVersionId = pvId,
                        weekId = week.id,
                        blockId = block.id,
                        weekIndex = week.weekIndex,
                        variantOf = week.variantOf,
                        variantLabel = week.variantLabel,
                    ),
                )
                for (session in week.sessions) {
                    sessions.add(
                        LoadedSessionTemplateEntity(
                            programVersionId = pvId,
                            sessionId = session.id,
                            weekId = week.id,
                            sessionIndex = session.sessionIndex,
                            dayLabel = session.dayLabel,
                            displayName = session.displayName,
                        ),
                    )
                    for (group in session.groups) {
                        groups.add(
                            LoadedExerciseGroupEntity(
                                programVersionId = pvId,
                                groupId = group.id,
                                sessionId = session.id,
                                groupOrder = group.order,
                                kind = wire(group.kind, GroupKind.serializer()),
                            ),
                        )
                        for (item in group.prescriptionItems) {
                            items.add(
                                LoadedPrescriptionItemEntity(
                                    programVersionId = pvId,
                                    itemId = item.id,
                                    groupId = group.id,
                                    itemOrder = item.order,
                                    prescribedExerciseId = item.prescribedExerciseId,
                                    role = wire(item.role, PrescriptionRole.serializer()),
                                    perSide = item.perSide,
                                    restSecondsHint = item.restSecondsHint,
                                    restMaxSecondsHint = item.restMaxSecondsHint,
                                    warmupSetCount = item.warmupSetCount,
                                    notesJson = encodeNotes(item.notes),
                                ),
                            )
                            for (set in item.setPrescriptions) {
                                sets.add(
                                    LoadedSetPrescriptionEntity(
                                        programVersionId = pvId,
                                        setId = set.id,
                                        itemId = item.id,
                                        setOrder = set.order,
                                        setKind = wire(set.setKind, SetKind.serializer()),
                                    ),
                                )
                                set.targets.forEachIndexed { index, target ->
                                    targets.add(toTargetEntity(pvId, set.id, index, target))
                                }
                            }
                        }
                    }
                }
            }
        }

        return LoadedProgramVersionBundle(
            version = version,
            catalogEntries = catalog,
            requiredReferences = refs,
            progressionRules = rules,
            blocks = blocks,
            weeks = weeks,
            sessions = sessions,
            groups = groups,
            items = items,
            sets = sets,
            targets = targets,
        )
    }

    private fun toTargetEntity(
        pvId: String,
        setId: String,
        index: Int,
        target: PrescriptionTarget,
    ): LoadedPrescriptionTargetEntity = when (target) {
        is PrescriptionTarget.ExactLoadReps -> baseTarget(pvId, setId, index, "exact_load_reps").copy(
            loadValue = target.loadValue,
            loadUnit = wire(target.loadUnit, WeightUnit.serializer()),
            reps = target.reps,
        )
        is PrescriptionTarget.RepRange -> baseTarget(pvId, setId, index, "rep_range").copy(
            repMin = target.repMin,
            repMax = target.repMax,
        )
        is PrescriptionTarget.Percent -> baseTarget(pvId, setId, index, "percent").copy(
            referenceId = target.referenceId,
            percent = target.percent,
            percentMin = target.percentMin,
            percentMax = target.percentMax,
            reps = target.reps,
            amrap = target.amrap,
            roundingIncrement = target.roundingIncrement,
            roundingUnit = target.roundingUnit?.let { wire(it, WeightUnit.serializer()) },
        )
        is PrescriptionTarget.Rpe -> baseTarget(pvId, setId, index, "rpe").copy(
            rpeTarget = target.target,
            rpeRangeMin = target.rangeMin,
            rpeRangeMax = target.rangeMax,
            rpeCap = target.cap,
        )
        is PrescriptionTarget.Rir -> baseTarget(pvId, setId, index, "rir").copy(
            rirTarget = target.target,
            rirRangeMin = target.rangeMin,
            rirRangeMax = target.rangeMax,
            rirFloor = target.floor,
        )
    }

    private fun baseTarget(
        pvId: String,
        setId: String,
        index: Int,
        kind: String,
    ): LoadedPrescriptionTargetEntity = LoadedPrescriptionTargetEntity(
        programVersionId = pvId,
        setId = setId,
        targetIndex = index,
        kind = kind,
        referenceId = null,
        loadValue = null,
        loadUnit = null,
        reps = null,
        repMin = null,
        repMax = null,
        percent = null,
        percentMin = null,
        percentMax = null,
        amrap = null,
        roundingIncrement = null,
        roundingUnit = null,
        rpeTarget = null,
        rpeRangeMin = null,
        rpeRangeMax = null,
        rpeCap = null,
        rirTarget = null,
        rirRangeMin = null,
        rirRangeMax = null,
        rirFloor = null,
    )

    private fun <T> wire(value: T, serializer: KSerializer<T>): String =
        ProgramResourceJson.encodeToString(serializer, value).trim('"')

    private fun encodeRoundingOverride(override: RoundingOverride): String =
        ProgramResourceJson.encodeToString(RoundingOverride.serializer(), override)

    private fun encodeImportAudit(audit: ImportAudit): String =
        ProgramResourceJson.encodeToString(ImportAudit.serializer(), audit)

    private fun encodeValidationIssues(issues: List<ValidationIssue>): String =
        ProgramResourceJson.encodeToString(ListSerializer(ValidationIssue.serializer()), issues)

    private fun encodeAliases(entry: ExerciseCatalogEntry): String =
        ProgramResourceJson.encodeToString(ListSerializer(ExerciseAlias.serializer()), entry.aliases)

    private fun encodeNotes(notes: List<PrescriptionNote>): String =
        ProgramResourceJson.encodeToString(ListSerializer(PrescriptionNote.serializer()), notes)

    private fun encodeStringList(values: List<String>): String =
        ProgramResourceJson.encodeToString(ListSerializer(String.serializer()), values)
}
