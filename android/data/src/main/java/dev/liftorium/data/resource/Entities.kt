package dev.liftorium.data.resource

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.liftorium.core.KoverIgnore
import androidx.room.PrimaryKey

/**
 * android-program-runner Room schema for finalized program resources.
 *
 * Design notes:
 *
 * * The natural IDs from the JSON schema (programVersionId, blockId,
 *   weekId, sessionId, groupId, itemId, setId, referenceId, rule id,
 *   exercise id) are used as composite primary keys. This avoids
 *   round-tripping auto-generated row IDs back to the loader for FK
 *   wiring and keeps the persisted shape semantically identical to the
 *   source JSON (every finalized version is uniquely keyed by its
 *   `programVersionId`).
 * * Foreign keys chain parent → child with `ON DELETE CASCADE` so
 *   deleting a [LoadedProgramVersionEntity] removes every dependent
 *   row in one statement (android-program-runner contract, see
 *   `docs/workstreams/android-program-runner.md`).
 * * Enum-shaped columns are stored as their JSON wire string (e.g.
 *   `activatable`, `working`, `barbell`) so the DB content matches the
 *   source artifact and Room does not need per-enum [TypeConverter]
 *   methods. Translation back to typed enums happens at the read
 *   boundary in the loader / mapper.
 * * Low-value nested data (exercise aliases, prescription notes, the
 *   raw validationIssues/importAudit/programDefaults/progressionRule
 *   parameters) is stored as a JSON string column. This trades
 *   relational queryability for schema simplicity; android-program-runner has no
 *   query patterns that touch these fields individually, and any
 *   future need can be addressed with a v2 migration.
 * * The prescription-target sub-table is intentionally relational with
 *   one row per target so conjunctive `percent + rpe` targets, range
 *   percent (`percentMin`/`percentMax`), and range RPE bounds
 *   round-trip without losing the companion or the bounds (android-program-runner
 *   contract on `docs/workstreams/android-program-runner.md`).
 */

@Entity(
    tableName = "loaded_program_version",
    indices = [Index(value = ["contentHash"], unique = true)],
)
@KoverIgnore
public data class LoadedProgramVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "programId") val programId: String,
    @ColumnInfo(name = "versionLabel") val versionLabel: String,
    @ColumnInfo(name = "displayName") val displayName: String?,
    @ColumnInfo(name = "authorAttribution") val authorAttribution: String?,
    @ColumnInfo(name = "contentHash") val contentHash: String,
    @ColumnInfo(name = "schemaVersion") val schemaVersion: Int,
    @ColumnInfo(name = "validationStatus") val validationStatus: String,
    @ColumnInfo(name = "loadedAtEpochMillis") val loadedAtEpochMillis: Long,
    @ColumnInfo(name = "programDefaultsJson") val programDefaultsJson: String?,
    @ColumnInfo(name = "programStructureRoundingOverrideJson") val programStructureRoundingOverrideJson: String?,
    @ColumnInfo(name = "importAuditJson") val importAuditJson: String,
    @ColumnInfo(name = "validationIssuesJson") val validationIssuesJson: String,
)

@Entity(
    tableName = "loaded_exercise_catalog_entry",
    primaryKeys = ["programVersionId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramVersionEntity::class,
            parentColumns = ["programVersionId"],
            childColumns = ["programVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@KoverIgnore
public data class LoadedExerciseCatalogEntryEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "exerciseId") val exerciseId: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "family") val family: String,
    @ColumnInfo(name = "equipment") val equipment: String,
    @ColumnInfo(name = "defaultRoundingIncrement") val defaultRoundingIncrement: Double?,
    @ColumnInfo(name = "defaultRoundingUnit") val defaultRoundingUnit: String?,
    @ColumnInfo(name = "aliasesJson") val aliasesJson: String,
)

@Entity(
    tableName = "loaded_required_reference",
    primaryKeys = ["programVersionId", "referenceId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramVersionEntity::class,
            parentColumns = ["programVersionId"],
            childColumns = ["programVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@KoverIgnore
public data class LoadedRequiredReferenceEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "referenceId") val referenceId: String,
    @ColumnInfo(name = "referenceType") val referenceType: String,
    @ColumnInfo(name = "exerciseId") val exerciseId: String?,
    @ColumnInfo(name = "firstRunnableWeekIndex") val firstRunnableWeekIndex: Int?,
    @ColumnInfo(name = "supplied") val supplied: Boolean,
    @ColumnInfo(name = "value") val value: Double?,
    @ColumnInfo(name = "unit") val unit: String?,
)

@Entity(
    tableName = "loaded_progression_rule",
    primaryKeys = ["programVersionId", "ruleId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramVersionEntity::class,
            parentColumns = ["programVersionId"],
            childColumns = ["programVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@KoverIgnore
public data class LoadedProgressionRuleEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "ruleId") val ruleId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "appliesToExerciseIdsJson") val appliesToExerciseIdsJson: String,
    @ColumnInfo(name = "parametersJson") val parametersJson: String?,
)

@Entity(
    tableName = "loaded_program_block",
    primaryKeys = ["programVersionId", "blockId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramVersionEntity::class,
            parentColumns = ["programVersionId"],
            childColumns = ["programVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@KoverIgnore
public data class LoadedProgramBlockEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "blockId") val blockId: String,
    @ColumnInfo(name = "blockOrder") val blockOrder: Int,
    @ColumnInfo(name = "displayName") val displayName: String?,
    @ColumnInfo(name = "roundingOverrideJson") val roundingOverrideJson: String?,
)

@Entity(
    tableName = "loaded_program_week",
    primaryKeys = ["programVersionId", "weekId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramBlockEntity::class,
            parentColumns = ["programVersionId", "blockId"],
            childColumns = ["programVersionId", "blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "blockId"])],
)
@KoverIgnore
public data class LoadedProgramWeekEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "weekId") val weekId: String,
    @ColumnInfo(name = "blockId") val blockId: String,
    @ColumnInfo(name = "weekIndex") val weekIndex: Int,
    @ColumnInfo(name = "variantOf") val variantOf: String?,
    @ColumnInfo(name = "variantLabel") val variantLabel: String?,
)

@Entity(
    tableName = "loaded_session_template",
    primaryKeys = ["programVersionId", "sessionId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedProgramWeekEntity::class,
            parentColumns = ["programVersionId", "weekId"],
            childColumns = ["programVersionId", "weekId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "weekId"])],
)
@KoverIgnore
public data class LoadedSessionTemplateEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "weekId") val weekId: String,
    @ColumnInfo(name = "sessionIndex") val sessionIndex: Int,
    @ColumnInfo(name = "dayLabel") val dayLabel: String?,
    @ColumnInfo(name = "displayName") val displayName: String?,
)

@Entity(
    tableName = "loaded_exercise_group",
    primaryKeys = ["programVersionId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedSessionTemplateEntity::class,
            parentColumns = ["programVersionId", "sessionId"],
            childColumns = ["programVersionId", "sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "sessionId"])],
)
@KoverIgnore
public data class LoadedExerciseGroupEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "groupId") val groupId: String,
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "groupOrder") val groupOrder: Int,
    @ColumnInfo(name = "kind") val kind: String,
)

@Entity(
    tableName = "loaded_prescription_item",
    primaryKeys = ["programVersionId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedExerciseGroupEntity::class,
            parentColumns = ["programVersionId", "groupId"],
            childColumns = ["programVersionId", "groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "groupId"])],
)
@KoverIgnore
public data class LoadedPrescriptionItemEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "itemId") val itemId: String,
    @ColumnInfo(name = "groupId") val groupId: String,
    @ColumnInfo(name = "itemOrder") val itemOrder: Int,
    @ColumnInfo(name = "prescribedExerciseId") val prescribedExerciseId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "perSide") val perSide: Boolean?,
    @ColumnInfo(name = "restSecondsHint") val restSecondsHint: Int?,
    @ColumnInfo(name = "restMaxSecondsHint") val restMaxSecondsHint: Int?,
    @ColumnInfo(name = "warmupSetCount") val warmupSetCount: Int?,
    @ColumnInfo(name = "notesJson") val notesJson: String,
)

@Entity(
    tableName = "loaded_set_prescription",
    primaryKeys = ["programVersionId", "setId"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedPrescriptionItemEntity::class,
            parentColumns = ["programVersionId", "itemId"],
            childColumns = ["programVersionId", "itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "itemId"])],
)
@KoverIgnore
public data class LoadedSetPrescriptionEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "setId") val setId: String,
    @ColumnInfo(name = "itemId") val itemId: String,
    @ColumnInfo(name = "setOrder") val setOrder: Int,
    @ColumnInfo(name = "setKind") val setKind: String,
)

@Entity(
    tableName = "loaded_prescription_target",
    primaryKeys = ["programVersionId", "setId", "targetIndex"],
    foreignKeys = [
        ForeignKey(
            entity = LoadedSetPrescriptionEntity::class,
            parentColumns = ["programVersionId", "setId"],
            childColumns = ["programVersionId", "setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programVersionId", "setId"])],
)
@KoverIgnore
public data class LoadedPrescriptionTargetEntity(
    @ColumnInfo(name = "programVersionId") val programVersionId: String,
    @ColumnInfo(name = "setId") val setId: String,
    @ColumnInfo(name = "targetIndex") val targetIndex: Int,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "referenceId") val referenceId: String?,
    @ColumnInfo(name = "loadValue") val loadValue: Double?,
    @ColumnInfo(name = "loadUnit") val loadUnit: String?,
    @ColumnInfo(name = "reps") val reps: Int?,
    @ColumnInfo(name = "repMin") val repMin: Int?,
    @ColumnInfo(name = "repMax") val repMax: Int?,
    @ColumnInfo(name = "percent") val percent: Double?,
    @ColumnInfo(name = "percentMin") val percentMin: Double?,
    @ColumnInfo(name = "percentMax") val percentMax: Double?,
    @ColumnInfo(name = "amrap") val amrap: Boolean?,
    @ColumnInfo(name = "roundingIncrement") val roundingIncrement: Double?,
    @ColumnInfo(name = "roundingUnit") val roundingUnit: String?,
    @ColumnInfo(name = "rpeTarget") val rpeTarget: Double?,
    @ColumnInfo(name = "rpeRangeMin") val rpeRangeMin: Double?,
    @ColumnInfo(name = "rpeRangeMax") val rpeRangeMax: Double?,
    @ColumnInfo(name = "rpeCap") val rpeCap: Double?,
    @ColumnInfo(name = "rirTarget") val rirTarget: Int?,
    @ColumnInfo(name = "rirRangeMin") val rirRangeMin: Int?,
    @ColumnInfo(name = "rirRangeMax") val rirRangeMax: Int?,
    @ColumnInfo(name = "rirFloor") val rirFloor: Int?,
)
