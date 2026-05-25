package dev.liftorium.data.resource

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [LoadedProgramVersionDao] that exercise the
 * `isNotEmpty()` short-circuit branches in
 * [LoadedProgramVersionDao.loadFullVersion]. The end-to-end loader
 * tests only feed populated fixtures, so without these tests the
 * `false` branches of those 10 guards remain uncovered.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class LoadedProgramVersionDaoTest {

    private lateinit var database: LiftoriumDatabase
    private lateinit var dao: LoadedProgramVersionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftoriumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.loadedProgramVersionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `loadFullVersion with empty optional collections only inserts the version row`() = runBlocking {
        val version = LoadedProgramVersionEntity(
            programVersionId = "p-empty@v1",
            programId = "p-empty",
            versionLabel = "v1",
            displayName = null,
            authorAttribution = null,
            contentHash = "0".repeat(64),
            schemaVersion = 1,
            validationStatus = "activatable",
            loadedAtEpochMillis = 1L,
            programDefaultsJson = null,
            programStructureRoundingOverrideJson = null,
            importAuditJson = "{}",
            validationIssuesJson = "[]",
        )
        val bundle = LoadedProgramVersionBundle(
            version = version,
            catalogEntries = emptyList(),
            requiredReferences = emptyList(),
            progressionRules = emptyList(),
            blocks = emptyList(),
            weeks = emptyList(),
            sessions = emptyList(),
            groups = emptyList(),
            items = emptyList(),
            sets = emptyList(),
            targets = emptyList(),
        )

        dao.loadFullVersion(bundle)

        val stored = dao.findById("p-empty@v1")
        assertNotNull(stored)
        assertEquals("p-empty@v1", stored.programVersionId)

        assertTrue(dao.listBlocks("p-empty@v1").isEmpty())
        assertTrue(dao.listWeeks("p-empty@v1").isEmpty())
        assertTrue(dao.listSessions("p-empty@v1").isEmpty())
        assertTrue(dao.listGroups("p-empty@v1").isEmpty())
        assertTrue(dao.listItems("p-empty@v1").isEmpty())
        assertTrue(dao.listSets("p-empty@v1").isEmpty())
        assertTrue(dao.listTargets("p-empty@v1").isEmpty())
        assertTrue(dao.listCatalogEntries("p-empty@v1").isEmpty())
        assertTrue(dao.listRequiredReferences("p-empty@v1").isEmpty())
        assertTrue(dao.listProgressionRules("p-empty@v1").isEmpty())
    }

    @Test
    fun `findById returns null for an unknown programVersionId`() = runBlocking {
        assertNull(dao.findById("never-loaded@v9"))
    }

    @Test
    fun `listAllVersions returns rows ordered by descending loadedAtEpochMillis`() = runBlocking {
        val older = versionRow("older@v1", loadedAt = 100L)
        val newer = versionRow("newer@v1", loadedAt = 200L)
        dao.loadFullVersion(emptyBundle(older))
        dao.loadFullVersion(emptyBundle(newer))

        val rows = dao.listAllVersions()
        assertEquals(listOf("newer@v1", "older@v1"), rows.map { it.programVersionId })
    }

    private fun versionRow(id: String, loadedAt: Long) = LoadedProgramVersionEntity(
        programVersionId = id,
        programId = id.substringBefore('@'),
        versionLabel = id.substringAfter('@'),
        displayName = null,
        authorAttribution = null,
        // contentHash is unique per row (v2 schema enforces a unique
        // index on this column); derive a deterministic distinct hash
        // from the programVersionId so every fixture row satisfies the
        // invariant without coupling test setup to a real canonicaliser.
        contentHash = id.hashCode().toString().padStart(64, '0').take(64),
        schemaVersion = 1,
        validationStatus = "activatable",
        loadedAtEpochMillis = loadedAt,
        programDefaultsJson = null,
        programStructureRoundingOverrideJson = null,
        importAuditJson = "{}",
        validationIssuesJson = "[]",
    )

    private fun emptyBundle(version: LoadedProgramVersionEntity) = LoadedProgramVersionBundle(
        version = version,
        catalogEntries = emptyList(),
        requiredReferences = emptyList(),
        progressionRules = emptyList(),
        blocks = emptyList(),
        weeks = emptyList(),
        sessions = emptyList(),
        groups = emptyList(),
        items = emptyList(),
        sets = emptyList(),
        targets = emptyList(),
    )
}
