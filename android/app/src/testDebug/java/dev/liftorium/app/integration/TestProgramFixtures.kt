package dev.liftorium.app.integration

import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.data.resource.LoadedProgramBlockEntity
import dev.liftorium.data.resource.LoadedProgramVersionBundle
import dev.liftorium.data.resource.LoadedProgramVersionEntity
import dev.liftorium.data.resource.LoadedProgramWeekEntity
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.resource.LoadedSessionTemplateEntity

/**
 * Programmatic Room fixtures for `:app` integration tests that need
 * specific program-resource shapes (pending references, week variants).
 *
 * Bypasses the JSON loader on purpose — the loader requires a real
 * canonical content hash, which would couple every test to
 * `ProgramResourceContentHash.compute`. The fixtures here construct
 * `LoadedProgramVersionBundle` rows directly and insert via
 * `LoadedProgramVersionDao.loadFullVersion`, mirroring the strategy
 * proven by `RoomProgramRunRepositoryTest`.
 */
internal object TestProgramFixtures {

    /**
     * One program version whose first runnable week (`weekIndex = 1`)
     * needs a `one_rep_max` reference that is **not supplied**. The
     * version's `validationStatus` is `activatable` so it appears in
     * the library; the runtime guard fires inside `StartProgramRun`.
     */
    suspend fun loadPendingReferenceVersion(
        database: LiftoriumDatabase,
        programVersionId: String = "pending-ref-fixture-v1",
        referenceId: String = "orm-squat",
    ) {
        val bundle = LoadedProgramVersionBundle(
            version = versionRow(programVersionId),
            catalogEntries = emptyList(),
            requiredReferences = listOf(
                LoadedRequiredReferenceEntity(
                    programVersionId = programVersionId,
                    referenceId = referenceId,
                    referenceType = "one_rep_max",
                    exerciseId = null,
                    firstRunnableWeekIndex = 1,
                    supplied = false,
                    value = null,
                    unit = null,
                ),
            ),
            progressionRules = emptyList(),
            blocks = listOf(LoadedProgramBlockEntity(programVersionId, "b1", 1, null, null)),
            weeks = listOf(LoadedProgramWeekEntity(programVersionId, "w1", "b1", 1, null, null)),
            sessions = listOf(LoadedSessionTemplateEntity(programVersionId, "w1-d1", "w1", 1, null, null)),
            groups = emptyList(),
            items = emptyList(),
            sets = emptyList(),
            targets = emptyList(),
        )
        database.loadedProgramVersionDao().loadFullVersion(bundle)
    }

    /**
     * One program version whose week-1 has TWO members in the same
     * variant group: a base week (`variantOf=null`) and one variant
     * (`variantOf="w1-base"`). `StartProgramRun` requires the caller
     * to pick one before the run can start.
     */
    suspend fun loadWeekVariantVersion(
        database: LiftoriumDatabase,
        programVersionId: String = "variant-fixture-v1",
    ) {
        val bundle = LoadedProgramVersionBundle(
            version = versionRow(programVersionId),
            catalogEntries = emptyList(),
            requiredReferences = emptyList(),
            progressionRules = emptyList(),
            blocks = listOf(LoadedProgramBlockEntity(programVersionId, "b1", 1, null, null)),
            weeks = listOf(
                LoadedProgramWeekEntity(programVersionId, "w1-base", "b1", 1, null, "Base"),
                LoadedProgramWeekEntity(programVersionId, "w1-heavy", "b1", 1, "w1-base", "Heavy"),
            ),
            sessions = listOf(
                LoadedSessionTemplateEntity(programVersionId, "w1-base-d1", "w1-base", 1, null, null),
                LoadedSessionTemplateEntity(programVersionId, "w1-heavy-d1", "w1-heavy", 1, null, null),
            ),
            groups = emptyList(),
            items = emptyList(),
            sets = emptyList(),
            targets = emptyList(),
        )
        database.loadedProgramVersionDao().loadFullVersion(bundle)
    }

    private fun versionRow(programVersionId: String) = LoadedProgramVersionEntity(
        programVersionId = programVersionId,
        programId = programVersionId.substringBefore("-v"),
        versionLabel = "v1",
        displayName = programVersionId,
        authorAttribution = null,
        // contentHash carries a UNIQUE index since schema v2; derive a
        // distinct deterministic hash from the version id rather than
        // recomputing canonical SHA-256.
        contentHash = programVersionId.hashCode().toString().padStart(64, '0').take(64),
        schemaVersion = 1,
        validationStatus = "activatable",
        loadedAtEpochMillis = 1L,
        programDefaultsJson = null,
        programStructureRoundingOverrideJson = null,
        importAuditJson = "{}",
        validationIssuesJson = "[]",
    )
}
