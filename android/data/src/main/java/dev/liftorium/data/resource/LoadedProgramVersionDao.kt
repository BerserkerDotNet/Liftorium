package dev.liftorium.data.resource

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.liftorium.core.KoverIgnore

/**
 * Aggregate write surface for finalized program resources. The
 * `loadFullVersion` method is the only public entry point that mutates
 * the loaded_* tables and runs inside a Room transaction so the entire
 * program version becomes visible at once or not at all (android-program-runner
 * `transactional Room writes` contract).
 */
@Dao
public abstract class LoadedProgramVersionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertVersion(entity: LoadedProgramVersionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertCatalogEntries(entries: List<LoadedExerciseCatalogEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertRequiredReferences(refs: List<LoadedRequiredReferenceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertProgressionRules(rules: List<LoadedProgressionRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertBlocks(blocks: List<LoadedProgramBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertWeeks(weeks: List<LoadedProgramWeekEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertSessions(sessions: List<LoadedSessionTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertGroups(groups: List<LoadedExerciseGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertItems(items: List<LoadedPrescriptionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertSets(sets: List<LoadedSetPrescriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertTargets(targets: List<LoadedPrescriptionTargetEntity>)

    /**
     * Returns the existing version row (or null) for idempotency /
     * conflict detection. Read inside the same transaction as the write
     * to ensure consistency under concurrent imports.
     */
    @Query("SELECT * FROM loaded_program_version WHERE programVersionId = :programVersionId")
    public abstract suspend fun findById(programVersionId: String): LoadedProgramVersionEntity?

    @Query("SELECT * FROM loaded_program_version ORDER BY loadedAtEpochMillis DESC, programVersionId ASC")
    public abstract suspend fun listAllVersions(): List<LoadedProgramVersionEntity>

    @Query("SELECT * FROM loaded_program_block WHERE programVersionId = :programVersionId ORDER BY blockOrder ASC")
    public abstract suspend fun listBlocks(programVersionId: String): List<LoadedProgramBlockEntity>

    @Query("SELECT * FROM loaded_program_week WHERE programVersionId = :programVersionId ORDER BY weekIndex ASC")
    public abstract suspend fun listWeeks(programVersionId: String): List<LoadedProgramWeekEntity>

    @Query("SELECT * FROM loaded_session_template WHERE programVersionId = :programVersionId ORDER BY sessionIndex ASC")
    public abstract suspend fun listSessions(programVersionId: String): List<LoadedSessionTemplateEntity>

    @Query("SELECT * FROM loaded_exercise_group WHERE programVersionId = :programVersionId ORDER BY groupOrder ASC")
    public abstract suspend fun listGroups(programVersionId: String): List<LoadedExerciseGroupEntity>

    @Query("SELECT * FROM loaded_prescription_item WHERE programVersionId = :programVersionId ORDER BY itemOrder ASC")
    public abstract suspend fun listItems(programVersionId: String): List<LoadedPrescriptionItemEntity>

    @Query("SELECT * FROM loaded_set_prescription WHERE programVersionId = :programVersionId ORDER BY setOrder ASC")
    public abstract suspend fun listSets(programVersionId: String): List<LoadedSetPrescriptionEntity>

    @Query("SELECT * FROM loaded_prescription_target WHERE programVersionId = :programVersionId ORDER BY setId ASC, targetIndex ASC")
    public abstract suspend fun listTargets(programVersionId: String): List<LoadedPrescriptionTargetEntity>

    @Query("SELECT * FROM loaded_exercise_catalog_entry WHERE programVersionId = :programVersionId")
    public abstract suspend fun listCatalogEntries(programVersionId: String): List<LoadedExerciseCatalogEntryEntity>

    @Query("SELECT * FROM loaded_required_reference WHERE programVersionId = :programVersionId")
    public abstract suspend fun listRequiredReferences(programVersionId: String): List<LoadedRequiredReferenceEntity>

    @Query("SELECT * FROM loaded_progression_rule WHERE programVersionId = :programVersionId")
    public abstract suspend fun listProgressionRules(programVersionId: String): List<LoadedProgressionRuleEntity>

    /**
     * Inserts the full normalized tree for a finalized program version
     * in one transaction. Caller is responsible for idempotency /
     * conflict checks before invocation.
     */
    @Transaction
    public open suspend fun loadFullVersion(bundle: LoadedProgramVersionBundle) {
        insertVersion(bundle.version)
        if (bundle.catalogEntries.isNotEmpty()) insertCatalogEntries(bundle.catalogEntries)
        if (bundle.requiredReferences.isNotEmpty()) insertRequiredReferences(bundle.requiredReferences)
        if (bundle.progressionRules.isNotEmpty()) insertProgressionRules(bundle.progressionRules)
        if (bundle.blocks.isNotEmpty()) insertBlocks(bundle.blocks)
        if (bundle.weeks.isNotEmpty()) insertWeeks(bundle.weeks)
        if (bundle.sessions.isNotEmpty()) insertSessions(bundle.sessions)
        if (bundle.groups.isNotEmpty()) insertGroups(bundle.groups)
        if (bundle.items.isNotEmpty()) insertItems(bundle.items)
        if (bundle.sets.isNotEmpty()) insertSets(bundle.sets)
        if (bundle.targets.isNotEmpty()) insertTargets(bundle.targets)
    }
}

/**
 * Aggregate input to [LoadedProgramVersionDao.loadFullVersion]. Kept
 * outside the DAO so the loader can build it eagerly before opening
 * the transaction; building inside the transaction would lengthen the
 * window an unrelated reader is blocked.
 */
@KoverIgnore
public data class LoadedProgramVersionBundle(
    val version: LoadedProgramVersionEntity,
    val catalogEntries: List<LoadedExerciseCatalogEntryEntity>,
    val requiredReferences: List<LoadedRequiredReferenceEntity>,
    val progressionRules: List<LoadedProgressionRuleEntity>,
    val blocks: List<LoadedProgramBlockEntity>,
    val weeks: List<LoadedProgramWeekEntity>,
    val sessions: List<LoadedSessionTemplateEntity>,
    val groups: List<LoadedExerciseGroupEntity>,
    val items: List<LoadedPrescriptionItemEntity>,
    val sets: List<LoadedSetPrescriptionEntity>,
    val targets: List<LoadedPrescriptionTargetEntity>,
)
