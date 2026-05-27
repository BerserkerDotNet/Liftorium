package dev.liftorium.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.liftorium.data.run.ProgramRunDao
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that the Room v1 → v2 schema migration applies cleanly
 * against a real v1 SQLite database AND preserves the row data that
 * was written before the upgrade.
 *
 * The test uses [MigrationTestHelper], which:
 *  * reads the committed schema exports from `assets/` (mirrored
 *    from `android/data/schemas/` by `data/build.gradle.kts` so the
 *    host-side unit test source set picks them up);
 *  * creates the v1 db from the v1 schema export;
 *  * runs every supplied [androidx.room.migration.Migration];
 *  * compares the resulting schema to the v2 export and fails if
 *    they don't match.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        LiftoriumDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsAuditColumnsAndIndexesWithoutDataLoss() = runBlocking {
        val dbName = "liftorium-migration-test.db"

        helper.createDatabase(dbName, 1).use { v1Db ->
            v1Db.execSQL(
                "INSERT INTO loaded_program_version (" +
                    "programVersionId, programId, versionLabel, displayName, " +
                    "authorAttribution, contentHash, schemaVersion, validationStatus, " +
                    "loadedAtEpochMillis, programDefaultsJson, " +
                    "programStructureRoundingOverrideJson, importAuditJson, " +
                    "validationIssuesJson) VALUES (" +
                    "'p@v1', 'p', '1', 'Program', 'Author', 'hash-a', 3, 'activatable', " +
                    "100, NULL, NULL, '{}', '[]')",
            )
            v1Db.execSQL(
                "INSERT INTO program_run (" +
                    "programRunId, programVersionId, pinnedContentHash, " +
                    "startedAtEpochMillis, status, chosenWeekVariantsJson, " +
                    "activeRunSlot) VALUES (" +
                    "'run-1', 'p@v1', 'hash-a', 555, 'active', '{}', 1)",
            )
            v1Db.execSQL(
                "INSERT INTO schedule_occurrence (" +
                    "programRunId, occurrenceId, plannedEpochDay, " +
                    "actualCompletionEpochDay, blockId, weekId, sessionId, " +
                    "sessionIndex, state) VALUES (" +
                    "'run-1', 'occ-1', 0, NULL, 'b1', 'w1', 's1', 1, 'planned')",
            )
        }

        val v2Db = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            *LIFTORIUM_DATABASE_MIGRATIONS,
        )

        v2Db.query(
            "SELECT updatedAtEpochMillis, startedAtEpochMillis FROM program_run WHERE programRunId = 'run-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst(), "program_run row preserved across migration")
            assertEquals(555L, cursor.getLong(0), "updatedAtEpochMillis backfilled to startedAtEpochMillis")
            assertEquals(555L, cursor.getLong(1), "startedAtEpochMillis preserved")
        }

        v2Db.query(
            "SELECT updatedAtEpochMillis FROM schedule_occurrence WHERE occurrenceId = 'occ-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst(), "schedule_occurrence row preserved across migration")
            assertEquals(0L, cursor.getLong(0), "schedule_occurrence updatedAtEpochMillis defaults to 0 for legacy v1 rows")
        }

        v2Db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'schedule_occurrence'",
        ).use { cursor ->
            val indexes = mutableListOf<String>()
            while (cursor.moveToNext()) indexes += cursor.getString(0)
            assertTrue(
                indexes.any { it == "index_schedule_occurrence_programRunId_plannedEpochDay_sessionIndex" },
                "composite occurrence index created (had: $indexes)",
            )
            assertTrue(
                indexes.none { it == "index_schedule_occurrence_programRunId" },
                "single-column occurrence index dropped (had: $indexes)",
            )
        }

        v2Db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'loaded_program_version'",
        ).use { cursor ->
            val indexes = mutableListOf<String>()
            while (cursor.moveToNext()) indexes += cursor.getString(0)
            assertTrue(
                indexes.any { it == "index_loaded_program_version_contentHash" },
                "unique content-hash index created (had: $indexes)",
            )
        }

        v2Db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'program_run'",
        ).use { cursor ->
            val indexes = mutableListOf<String>()
            while (cursor.moveToNext()) indexes += cursor.getString(0)
            assertTrue(
                indexes.any { it == "index_program_run_startedAtEpochMillis" },
                "startedAtEpochMillis index created (had: $indexes)",
            )
        }

        v2Db.close()

        // Sanity-check that the migrated database also boots cleanly
        // through the full Room facade with the migration registered.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val live = androidx.room.Room.databaseBuilder(
            context,
            LiftoriumDatabase::class.java,
            dbName,
        )
            .addMigrations(*LIFTORIUM_DATABASE_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao: ProgramRunDao = live.programRunDao()
            val found = dao.findById("run-1")
            assertNotNull(found, "Room facade can read migrated v2 row")
            assertEquals(555L, found.updatedAtEpochMillis)
        } finally {
            live.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate2To3_addsWorkoutLoggingTablesAdditively() = runBlocking {
        val dbName = "liftorium-migration-v3-test.db"

        // Seed a v2 db with a program version + run + occurrence so the
        // additive migration must coexist with existing rows and the v2
        // → v3 path is verified to not touch pre-existing data.
        helper.createDatabase(dbName, 2).use { v2Db ->
            v2Db.execSQL(
                "INSERT INTO loaded_program_version (" +
                    "programVersionId, programId, versionLabel, displayName, " +
                    "authorAttribution, contentHash, schemaVersion, validationStatus, " +
                    "loadedAtEpochMillis, programDefaultsJson, " +
                    "programStructureRoundingOverrideJson, importAuditJson, " +
                    "validationIssuesJson) VALUES (" +
                    "'p@v1', 'p', '1', 'Program', 'Author', 'hash-b', 3, 'activatable', " +
                    "100, NULL, NULL, '{}', '[]')",
            )
            v2Db.execSQL(
                "INSERT INTO program_run (" +
                    "programRunId, programVersionId, pinnedContentHash, " +
                    "startedAtEpochMillis, status, chosenWeekVariantsJson, " +
                    "activeRunSlot, updatedAtEpochMillis) VALUES (" +
                    "'run-v2', 'p@v1', 'hash-b', 777, 'active', '{}', 1, 777)",
            )
            v2Db.execSQL(
                "INSERT INTO schedule_occurrence (" +
                    "programRunId, occurrenceId, plannedEpochDay, " +
                    "actualCompletionEpochDay, blockId, weekId, sessionId, " +
                    "sessionIndex, state, updatedAtEpochMillis) VALUES (" +
                    "'run-v2', 'occ-v2', 0, NULL, 'b1', 'w1', 's1', 1, 'planned', 0)",
            )
        }

        val v3Db = helper.runMigrationsAndValidate(
            dbName,
            3,
            true,
            *LIFTORIUM_DATABASE_MIGRATIONS,
        )

        v3Db.query(
            "SELECT programRunId, updatedAtEpochMillis FROM program_run WHERE programRunId = 'run-v2'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst(), "pre-existing program_run row preserved by v2→v3")
            assertEquals("run-v2", cursor.getString(0))
            assertEquals(777L, cursor.getLong(1))
        }

        val newTables = setOf(
            "workout_session",
            "workout_exercise_log",
            "actual_set",
            "prescription_calculation_snapshot",
            "local_mutation",
            "device_identity",
        )
        v3Db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            val present = mutableSetOf<String>()
            while (cursor.moveToNext()) present += cursor.getString(0)
            for (table in newTables) {
                assertTrue(table in present, "v2→v3 created table $table (present: $present)")
            }
        }

        v3Db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'workout_session'",
        ).use { cursor ->
            val indexes = mutableListOf<String>()
            while (cursor.moveToNext()) indexes += cursor.getString(0)
            assertTrue(
                indexes.any { it == "index_workout_session_activeWorkoutSlot" },
                "unique activeWorkoutSlot index created (had: $indexes)",
            )
        }

        v3Db.close()

        // Re-open through the Room facade to confirm the live runtime
        // also boots cleanly on v3 with the new DAOs.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val live = androidx.room.Room.databaseBuilder(
            context,
            LiftoriumDatabase::class.java,
            dbName,
        )
            .addMigrations(*LIFTORIUM_DATABASE_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val activeWorkout = live.workoutLoggingDao().findActiveSession()
            assertEquals(null, activeWorkout, "no active workout session after v3 migration of legacy v2 data")
            val device = live.deviceIdentityDao().find()
            assertEquals(null, device, "device_identity is empty until first DeviceIdProvider call")
        } finally {
            live.close()
            context.deleteDatabase(dbName)
        }
    }
}
