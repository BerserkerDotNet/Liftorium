package dev.liftorium.domain.resource

import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.WeightUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

/**
 * Top-level program resource DTO that mirrors
 * `schema/program-resource.schema.json` (android-program-runner supports schemaVersion
 * 1, 2, and 3). The Kotlin field names match the JSON field names so
 * kotlinx.serialization does not need per-field overrides.
 *
 * IMPORTANT: This DTO is NEVER used to recompute the content hash;
 * [ProgramResourceContentHash] operates on the raw
 * [kotlinx.serialization.json.JsonElement] to preserve byte parity
 * with the TypeScript validator (`schema/hash.ts`). Decoding to this
 * DTO and re-serializing is intentionally lossy in edge cases
 * (default-encoded optional fields, integer/float coercions) that the
 * JsonElement path is not.
 */
@KoverIgnore
@Serializable
public data class ProgramResource(
    val schemaVersion: Int,
    val metadata: Metadata,
    val validationStatus: ValidationStatus,
    val validationIssues: List<ValidationIssue> = emptyList(),
    val importAudit: ImportAudit,
    val programDefaults: RoundingOverride? = null,
    val exerciseCatalog: List<ExerciseCatalogEntry> = emptyList(),
    val requiredReferences: List<RequiredReference> = emptyList(),
    val programStructure: ProgramStructure,
    val progressionRules: List<ProgressionRule> = emptyList(),
)

@KoverIgnore
@Serializable
public data class Metadata(
    val programId: String,
    val programVersionId: String,
    val versionLabel: String,
    val displayName: String? = null,
    val authorAttribution: String? = null,
    val contentHash: String,
)

@KoverIgnore
@Serializable
public data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val locationHint: String? = null,
)

@KoverIgnore
@Serializable
public data class ImportAudit(
    val sourceHash: String,
    val sourceFilename: String,
    val importedAtUtc: String,
    val sourceKind: ImportSourceKind,
    val schemaVersionUsed: Int,
)

@KoverIgnore
@Serializable
public data class RoundingOverride(
    val roundingIncrement: Double? = null,
    val roundingUnit: WeightUnit? = null,
)

@KoverIgnore
@Serializable
public data class ExerciseCatalogEntry(
    val id: String,
    val displayName: String,
    val family: ExerciseFamily,
    val equipment: ExerciseEquipment,
    val defaultRoundingIncrement: Double? = null,
    val defaultRoundingUnit: WeightUnit? = null,
    val aliases: List<ExerciseAlias> = emptyList(),
)

@KoverIgnore
@Serializable
public data class ExerciseAlias(
    val aliasText: String,
    val source: AliasSource,
)

@KoverIgnore
@Serializable
public data class RequiredReference(
    val id: String,
    val referenceType: ReferenceType,
    val exerciseId: String? = null,
    val firstRunnableWeekIndex: Int? = null,
    val supplied: Boolean,
    val value: Double? = null,
    val unit: WeightUnit? = null,
)

@KoverIgnore
@Serializable
public data class ProgressionRule(
    val id: String,
    val kind: String,
    val appliesToExerciseIds: List<String> = emptyList(),
    val parameters: JsonObject? = null,
)

@KoverIgnore
@Serializable
public data class ProgramStructure(
    val roundingOverride: RoundingOverride? = null,
    val blocks: List<ProgramBlock> = emptyList(),
)

@KoverIgnore
@Serializable
public data class ProgramBlock(
    val id: String,
    val order: Int,
    val displayName: String? = null,
    val roundingOverride: RoundingOverride? = null,
    val weeks: List<ProgramWeek> = emptyList(),
)

@KoverIgnore
@Serializable
public data class ProgramWeek(
    val id: String,
    val weekIndex: Int,
    val variantOf: String? = null,
    val variantLabel: String? = null,
    val sessions: List<SessionTemplate> = emptyList(),
)

@KoverIgnore
@Serializable
public data class SessionTemplate(
    val id: String,
    val sessionIndex: Int,
    val dayLabel: String? = null,
    val displayName: String? = null,
    val groups: List<ExerciseGroup> = emptyList(),
)

@KoverIgnore
@Serializable
public data class ExerciseGroup(
    val id: String,
    val order: Int,
    val kind: GroupKind,
    val prescriptionItems: List<PrescriptionItem> = emptyList(),
)

@KoverIgnore
@Serializable
public data class PrescriptionItem(
    val id: String,
    val order: Int,
    val prescribedExerciseId: String,
    val role: PrescriptionRole,
    val perSide: Boolean? = null,
    val restSecondsHint: Int? = null,
    val restMaxSecondsHint: Int? = null,
    val warmupSetCount: Int? = null,
    val notes: List<PrescriptionNote> = emptyList(),
    val setPrescriptions: List<SetPrescription> = emptyList(),
)

@KoverIgnore
@Serializable
public data class PrescriptionNote(
    val kind: NoteKind,
    val text: String,
)

@KoverIgnore
@Serializable
public data class SetPrescription(
    val id: String,
    val order: Int,
    val setKind: SetKind,
    val targets: List<PrescriptionTarget> = emptyList(),
)

/**
 * Sealed hierarchy for prescription targets. The schema uses the
 * `kind` field as the discriminator and forbids unknown values, so the
 * sealed class is annotated to use `kind` directly (overriding
 * kotlinx-serialization's default `type` discriminator).
 *
 * The `Percent` variant encodes both the single-percent and range
 * (min/max) shapes as nullable fields. The schema's `oneOf` rule
 * "either `percent` is set, or `percentMin`+`percentMax` are set"
 * is enforced at the validator/loader level, not by the parser; this
 * matches the recheck-only android-program-runner strategy.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
public sealed class PrescriptionTarget {

    @KoverIgnore
    @Serializable
    @SerialName("exact_load_reps")
    public data class ExactLoadReps(
        val loadValue: Double,
        val loadUnit: WeightUnit,
        val reps: Int,
    ) : PrescriptionTarget()

    @KoverIgnore
    @Serializable
    @SerialName("rep_range")
    public data class RepRange(
        val repMin: Int,
        val repMax: Int,
    ) : PrescriptionTarget()

    @KoverIgnore
    @Serializable
    @SerialName("percent")
    public data class Percent(
        val referenceId: String,
        val percent: Double? = null,
        val percentMin: Double? = null,
        val percentMax: Double? = null,
        val reps: Int? = null,
        val amrap: Boolean? = null,
        val roundingIncrement: Double? = null,
        val roundingUnit: WeightUnit? = null,
    ) : PrescriptionTarget()

    @KoverIgnore
    @Serializable
    @SerialName("rpe")
    public data class Rpe(
        val target: Double? = null,
        val rangeMin: Double? = null,
        val rangeMax: Double? = null,
        val cap: Double? = null,
    ) : PrescriptionTarget()

    @KoverIgnore
    @Serializable
    @SerialName("rir")
    public data class Rir(
        val target: Int? = null,
        val rangeMin: Int? = null,
        val rangeMax: Int? = null,
        val floor: Int? = null,
    ) : PrescriptionTarget()
}

/**
 * True when this percent target carries a range (`percentMin`+`percentMax`).
 * The runner must surface both bounds; dropping either silently is a
 * contract violation (`docs/workstreams/android-program-runner.md`).
 *
 * Defined as a top-level extension so the underlying [PrescriptionTarget.Percent]
 * stays a behaviorless data class (annotated `@KoverIgnore`) — the
 * branch logic lives here where it can be measured and tested directly.
 */
public val PrescriptionTarget.Percent.isRange: Boolean
    get() = percentMin != null && percentMax != null
