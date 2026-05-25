package dev.liftorium.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration `v1 → v2`. The v2 schema deliberately keeps the
 * row data identical to v1 but adds the following defensive/perf
 * improvements that came out of the Phase 4 review:
 *
 *  * `program_run.updatedAtEpochMillis` — audit timestamp for the
 *    "last mutation" of a run row, populated to the row's own
 *    `startedAtEpochMillis` for v1→v2 carry-overs so existing rows
 *    don't appear as never-touched.
 *  * `program_run.startedAtEpochMillis` index — accelerates the
 *    history query `SELECT * FROM program_run ORDER BY
 *    startedAtEpochMillis DESC` (`ProgramRunDao.listAllRuns`).
 *  * `schedule_occurrence.updatedAtEpochMillis` — mirrors the audit
 *    column on the per-occurrence table; backfilled to `0` because
 *    v1 occurrences carry no per-row creation time and the run's
 *    `startedAtEpochMillis` is the only meaningful anchor (consumers
 *    can ignore the legacy `0` carry-over until the workout-logging
 *    workstream mutates the rows for the first time).
 *  * `schedule_occurrence` index swap — the v1 single-column index
 *    on `programRunId` is replaced with a composite covering the
 *    DAO's `WHERE programRunId = ? ORDER BY plannedEpochDay ASC,
 *    sessionIndex ASC` access pattern.
 *  * `loaded_program_version.contentHash` unique index — enforces
 *    the activation-contract invariant that no two loaded version
 *    rows share the same canonicalised content hash. v1 already
 *    encoded this in code via `findByContentHash`; v2 promotes the
 *    invariant to a DB-level constraint.
 *
 * No data loss: the migration is additive (ADD COLUMN + new
 * indexes). The index swap drops the old index name Room auto-
 * generated for v1 (`index_schedule_occurrence_programRunId`) and
 * creates the v2 composite index name Room expects.
 */
internal object Migration1To2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `program_run` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "UPDATE `program_run` SET `updatedAtEpochMillis` = `startedAtEpochMillis` " +
                "WHERE `updatedAtEpochMillis` = 0",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_program_run_startedAtEpochMillis` " +
                "ON `program_run` (`startedAtEpochMillis`)",
        )

        db.execSQL(
            "ALTER TABLE `schedule_occurrence` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("DROP INDEX IF EXISTS `index_schedule_occurrence_programRunId`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_schedule_occurrence_programRunId_plannedEpochDay_sessionIndex` " +
                "ON `schedule_occurrence` (`programRunId`, `plannedEpochDay`, `sessionIndex`)",
        )

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_loaded_program_version_contentHash` " +
                "ON `loaded_program_version` (`contentHash`)",
        )
    }
}

/**
 * Ordered list of all production migrations to register against
 * Room's database builder. Tests against an in-memory builder do not
 * need migrations because Room recreates the v[N] schema from scratch.
 */
public val LIFTORIUM_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)
