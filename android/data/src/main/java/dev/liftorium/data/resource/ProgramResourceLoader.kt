package dev.liftorium.data.resource

import androidx.room.withTransaction
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.domain.resource.LoaderResult
import dev.liftorium.domain.resource.ParseOutcome
import dev.liftorium.domain.resource.ProgramResourceParseService
import dev.liftorium.domain.common.ProgramVersionId

/**
 * android-program-runner entry point for ingesting finalized program-resource JSON.
 *
 * Sequence (recheck_only validation strategy, see ADR
 * `Runtime validation strategy = recheck_only` in `docs/decisions.md`):
 *
 * 1. [ProgramResourceParseService.parse] runs the schemaVersion gate,
 *    DTO decode, content-hash recompute, and validationStatus gate.
 * 2. The loader opens a single Room transaction that:
 *    a. Re-reads the existing row by `programVersionId` for
 *       idempotency / conflict detection.
 *    b. Returns [LoaderResult.Idempotent] when the same hash is
 *       already present, or [LoaderResult.Failure.ConflictDifferentHash]
 *       when the hashes differ (`IT-IMP-006` per `docs/product.md` A7).
 *    c. Otherwise writes the full normalized tree via
 *       [LoadedProgramVersionDao.loadFullVersion] and returns
 *       [LoaderResult.Loaded].
 *
 * Validation issues, importAudit, programDefaults, and any other
 * import-time artefacts are preserved verbatim as opaque JSON columns
 * (android-program-runner contract: "Import audit metadata and validation issues are
 * preserved").
 */
public class ProgramResourceLoader(
    private val database: LiftoriumDatabase,
    private val dao: LoadedProgramVersionDao,
    private val timeSource: TimeSource,
) {

    public suspend fun load(rawJson: String): LoaderResult {
        return when (val parsed = ProgramResourceParseService.parse(rawJson)) {
            is ParseOutcome.Failed -> parsed.failure
            is ParseOutcome.Parsed -> persist(parsed)
        }
    }

    private suspend fun persist(parsed: ParseOutcome.Parsed): LoaderResult {
        val bundle = ProgramResourceMapper.toBundle(
            resource = parsed.resource,
            loadedAtEpochMillis = timeSource.now().toEpochMilli(),
        )

        return database.withTransaction {
            val existing = dao.findById(bundle.version.programVersionId)
            when {
                existing == null -> {
                    dao.loadFullVersion(bundle)
                    LoaderResult.Loaded(ProgramVersionId(bundle.version.programVersionId))
                }
                existing.contentHash.equals(bundle.version.contentHash, ignoreCase = true) -> {
                    LoaderResult.Idempotent(ProgramVersionId(bundle.version.programVersionId))
                }
                else -> LoaderResult.Failure.ConflictDifferentHash(
                    programVersionId = ProgramVersionId(bundle.version.programVersionId),
                    existingHash = existing.contentHash,
                    incomingHash = bundle.version.contentHash,
                )
            }
        }
    }
}
