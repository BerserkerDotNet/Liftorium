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
 * Room migration `v2 → v3`. The v3 schema adds the entire
 * `android-workout-logging` family of tables:
 *
 *  * `workout_session` — one row per workout attempt; partial-unique
 *    `activeWorkoutSlot` enforces the one-in-progress invariant.
 *  * `workout_exercise_log` — per-exercise child of a session.
 *  * `actual_set` — per-set grandchild of a session.
 *  * `prescription_calculation_snapshot` — pinned calculation inputs
 *    for one actual_set row.
 *  * `local_mutation` — durable audit log of every user-visible
 *    mutation. Carries the full `SyncMetadata` columns under the
 *    `audit_` prefix to avoid collision with the mutation's own
 *    `clientMutationId` primary key and `createdAtEpochMillis`
 *    columns.
 *  * `device_identity` — single-row table seeding the per-install
 *    `DeviceId` UUID (see ADR 2026-05-25).
 *
 * The migration is purely additive (CREATE TABLE + CREATE INDEX);
 * no existing v2 data is touched.
 */
@Suppress("LongMethod")
internal object Migration2To3 : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_session` (
                `workoutSessionId` TEXT NOT NULL,
                `programRunId` TEXT NOT NULL,
                `plannedOccurrenceId` TEXT NOT NULL,
                `pinnedProgramVersionId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `startedAtEpochMillis` INTEGER NOT NULL,
                `eventZoneId` TEXT NOT NULL,
                `localDateEpochDay` INTEGER NOT NULL,
                `completedAtEpochMillis` INTEGER,
                `abandonedAtEpochMillis` INTEGER,
                `lastSavedMutationId` TEXT NOT NULL,
                `activeWorkoutSlot` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `deletedAtEpochMillis` INTEGER,
                `deviceId` TEXT NOT NULL,
                `localRevision` INTEGER NOT NULL,
                `clientMutationId` TEXT NOT NULL,
                PRIMARY KEY(`workoutSessionId`),
                FOREIGN KEY(`programRunId`) REFERENCES `program_run`(`programRunId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_programRunId` ON `workout_session` (`programRunId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_status` ON `workout_session` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_session_activeWorkoutSlot` ON `workout_session` (`activeWorkoutSlot`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_startedAtEpochMillis` ON `workout_session` (`startedAtEpochMillis`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_exercise_log` (
                `workoutExerciseLogId` TEXT NOT NULL,
                `workoutSessionId` TEXT NOT NULL,
                `prescriptionItemId` TEXT NOT NULL,
                `exerciseGroupId` TEXT NOT NULL,
                `displayOrder` INTEGER NOT NULL,
                `prescribedExerciseId` TEXT NOT NULL,
                `performedExerciseId` TEXT NOT NULL,
                `notes` TEXT,
                `isCompleted` INTEGER NOT NULL,
                `isSkipped` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `deletedAtEpochMillis` INTEGER,
                `deviceId` TEXT NOT NULL,
                `localRevision` INTEGER NOT NULL,
                `clientMutationId` TEXT NOT NULL,
                PRIMARY KEY(`workoutExerciseLogId`),
                FOREIGN KEY(`workoutSessionId`) REFERENCES `workout_session`(`workoutSessionId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_exercise_log_workoutSessionId_displayOrder` " +
                "ON `workout_exercise_log` (`workoutSessionId`, `displayOrder`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `actual_set` (
                `actualSetId` TEXT NOT NULL,
                `workoutExerciseLogId` TEXT NOT NULL,
                `prescribedSetId` TEXT,
                `role` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `performedExerciseId` TEXT NOT NULL,
                `perSide` INTEGER NOT NULL,
                `actualLoad` REAL,
                `actualLoadUnit` TEXT,
                `actualReps` INTEGER,
                `actualRpe` REAL,
                `actualRir` INTEGER,
                `notes` TEXT,
                `calculationSnapshotId` TEXT,
                `sourceSubstitutionEventId` TEXT,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `deletedAtEpochMillis` INTEGER,
                `deviceId` TEXT NOT NULL,
                `localRevision` INTEGER NOT NULL,
                `clientMutationId` TEXT NOT NULL,
                PRIMARY KEY(`actualSetId`),
                FOREIGN KEY(`workoutExerciseLogId`) REFERENCES `workout_exercise_log`(`workoutExerciseLogId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_actual_set_workoutExerciseLogId_sequence` " +
                "ON `actual_set` (`workoutExerciseLogId`, `sequence`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `prescription_calculation_snapshot` (
                `snapshotId` TEXT NOT NULL,
                `actualSetId` TEXT NOT NULL,
                `referenceType` TEXT,
                `referenceExerciseId` TEXT,
                `referenceValue` REAL,
                `referenceUnit` TEXT,
                `percent` REAL,
                `roundingRule` TEXT,
                `calculatedRawLoad` REAL,
                `displayLoad` REAL,
                `displayLoadUnit` TEXT,
                `targetReps` INTEGER,
                `targetRpe` REAL,
                `targetRir` INTEGER,
                `caveatsJson` TEXT NOT NULL,
                PRIMARY KEY(`snapshotId`),
                FOREIGN KEY(`actualSetId`) REFERENCES `actual_set`(`actualSetId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_prescription_calculation_snapshot_actualSetId` " +
                "ON `prescription_calculation_snapshot` (`actualSetId`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_mutation` (
                `clientMutationId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `eventZoneId` TEXT NOT NULL,
                `localDateEpochDay` INTEGER NOT NULL,
                `audit_createdAtEpochMillis` INTEGER NOT NULL,
                `audit_updatedAtEpochMillis` INTEGER NOT NULL,
                `audit_deletedAtEpochMillis` INTEGER,
                `audit_deviceId` TEXT NOT NULL,
                `audit_localRevision` INTEGER NOT NULL,
                `audit_clientMutationId` TEXT NOT NULL,
                PRIMARY KEY(`clientMutationId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_local_mutation_entityType_entityId_createdAtEpochMillis` " +
                "ON `local_mutation` (`entityType`, `entityId`, `createdAtEpochMillis`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `device_identity` (
                `id` INTEGER NOT NULL,
                `deviceId` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Ordered list of all production migrations to register against
 * Room's database builder. Tests against an in-memory builder do not
 * need migrations because Room recreates the v[N] schema from scratch.
 */
public val LIFTORIUM_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(Migration1To2, Migration2To3)
