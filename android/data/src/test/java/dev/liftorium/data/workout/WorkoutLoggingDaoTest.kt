package dev.liftorium.data.workout

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.liftorium.data.LiftoriumDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Slice 1 IT-DB-001 (first half): `insertSessionBundle` writes the
 * full workout-session aggregate atomically and the partial-unique
 * `activeWorkoutSlot` index blocks a second InProgress row.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class WorkoutLoggingDaoTest {

    private lateinit var db: LiftoriumDatabase
    private lateinit var dao: WorkoutLoggingDao

    @Before
    fun setUp() {
        // In-memory DB with FKs ON so foreign-key violations surface
        // during the test rather than being silently swallowed by
        // SQLite's default permissive mode.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftoriumDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.workoutLoggingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertSessionBundle_persistsSessionLogsSetsSnapshotsAndMutationAtomically() = runBlocking {
        seedProgramRun(db, programRunId = "run-1", programVersionId = "p@1")

        val bundle = buildBundle(
            sessionId = "ws-1",
            programRunId = "run-1",
            programVersionId = "p@1",
            activeSlot = 1L,
        )

        dao.insertSessionBundle(bundle)

        val storedSession = dao.findSessionById("ws-1")
        assertNotNull(storedSession)
        assertEquals("in_progress", storedSession.status)
        assertEquals(1L, storedSession.activeWorkoutSlot)
        assertEquals(2, dao.listExerciseLogs("ws-1").size)
        val logs = dao.listExerciseLogs("ws-1")
        val sets = dao.listSetsForLogs(logs.map { it.workoutExerciseLogId })
        assertEquals(4, sets.size)
        val snapshots = dao.listSnapshotsForSets(sets.map { it.actualSetId })
        assertEquals(2, snapshots.size)
        val mutation = dao.findMutationById("mut-ws-1")
        assertNotNull(mutation)
        assertEquals("start_workout", mutation.type)
        assertEquals("workout_session", mutation.entityType)
    }

    @Test
    fun insertSessionBundle_secondInProgressInsertFailsOnUniqueIndex() = runBlocking {
        seedProgramRun(db, programRunId = "run-1", programVersionId = "p@1")
        seedProgramRun(db, programRunId = "run-2", programVersionId = "p@1")

        dao.insertSessionBundle(
            buildBundle(
                sessionId = "ws-1",
                programRunId = "run-1",
                programVersionId = "p@1",
                activeSlot = 1L,
            ),
        )

        assertFailsWith<SQLiteConstraintException> {
            dao.insertSessionBundle(
                buildBundle(
                    sessionId = "ws-2",
                    programRunId = "run-2",
                    programVersionId = "p@1",
                    activeSlot = 1L,
                ),
            )
        }

        // The second bundle's rollback must leave NO trace of ws-2 in
        // any of the child tables, since `insertSessionBundle` is
        // `@Transaction`.
        assertNull(dao.findSessionById("ws-2"))
        assertEquals(emptyList(), dao.listExerciseLogs("ws-2"))
    }

    @Test
    fun loadOpenSessionAggregate_returnsSessionWithLogsSetsSnapshots() = runBlocking {
        seedProgramRun(db, programRunId = "run-1", programVersionId = "p@1")
        dao.insertSessionBundle(
            buildBundle(
                sessionId = "ws-1",
                programRunId = "run-1",
                programVersionId = "p@1",
                activeSlot = 1L,
            ),
        )

        val aggregate = dao.loadOpenSessionAggregate()
        assertNotNull(aggregate)
        assertEquals("ws-1", aggregate.session.workoutSessionId)
        assertEquals(2, aggregate.exerciseLogs.size)
        assertEquals(4, aggregate.actualSets.size)
        assertEquals(2, aggregate.snapshots.size)
    }
}
