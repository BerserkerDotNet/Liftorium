package dev.liftorium.data.resource

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.liftorium.core.TimeSource
import dev.liftorium.data.LiftoriumDatabase
import dev.liftorium.domain.resource.LoaderResult
import dev.liftorium.domain.resource.ProgramResourceContentHash
import dev.liftorium.domain.resource.ProgramResourceJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Robolectric integration tests for the android-program-runner
 * [ProgramResourceLoader] end-to-end:
 *
 * * Recheck-only validation gates (delegated to
 *   `ProgramResourceParseService`).
 * * Transactional Room write of the full normalized tree.
 * * Idempotent replay (same `programVersionId` + same `contentHash`).
 * * Conflict detection (same `programVersionId` + different hash),
 *   traceability `IT-IMP-006` per `docs/product.md` A7.
 * * Persistence parity for v3 features the workstream contract calls
 *   out: range percent, conjunctive percent + RPE, restMaxSecondsHint,
 *   warmupSetCount.
 *
 * Robolectric is configured for SDK 30 to match the production
 * `minSdk` in `:data` and `:app`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ProgramResourceLoaderTest {

    private val schemaRoot: Path = locateSchemaRoot()
    private val fixedInstant: Instant = Instant.parse("2026-05-18T00:00:00Z")
    private val timeSource: TimeSource = TimeSource.fixed(fixedInstant)

    private lateinit var database: LiftoriumDatabase
    private lateinit var dao: LoadedProgramVersionDao
    private lateinit var loader: ProgramResourceLoader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiftoriumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.loadedProgramVersionDao()
        loader = ProgramResourceLoader(database, dao, timeSource)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `loader writes full normalized tree for valid-activatable fixture`() = runBlocking {
        val raw = readFixture("fixtures/valid-activatable.json")

        val result = loader.load(raw)

        val loaded = assertIs<LoaderResult.Loaded>(result)
        val parsedRoot = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val pvId = (parsedRoot.getValue("metadata").jsonObject.getValue("programVersionId") as JsonPrimitive).content
        assertEquals(pvId, loaded.programVersionId.value)

        val versionRow = assertNotNull(dao.findById(pvId))
        assertEquals(fixedInstant.toEpochMilli(), versionRow.loadedAtEpochMillis)

        val blocks = dao.listBlocks(pvId)
        val weeks = dao.listWeeks(pvId)
        val sessions = dao.listSessions(pvId)
        val items = dao.listItems(pvId)
        val sets = dao.listSets(pvId)
        val targets = dao.listTargets(pvId)
        val refs = dao.listRequiredReferences(pvId)
        val catalog = dao.listCatalogEntries(pvId)

        assertTrue(blocks.isNotEmpty(), "blocks must be persisted")
        assertTrue(weeks.isNotEmpty(), "weeks must be persisted")
        assertTrue(sessions.isNotEmpty(), "sessions must be persisted")
        assertTrue(items.isNotEmpty(), "prescription items must be persisted")
        assertTrue(sets.isNotEmpty(), "set prescriptions must be persisted")
        assertTrue(targets.isNotEmpty(), "prescription targets must be persisted")
        assertTrue(refs.isNotEmpty(), "required references must be persisted")
        assertTrue(catalog.isNotEmpty(), "exercise catalog must be persisted")
    }

    @Test
    fun `loader is idempotent when the same artifact is loaded twice`() = runBlocking {
        val raw = readFixture("fixtures/valid-activatable.json")

        val first = loader.load(raw)
        assertIs<LoaderResult.Loaded>(first)
        val initialItemCount = dao.listItems((first as LoaderResult.Loaded).programVersionId.value).size

        val second = loader.load(raw)
        assertIs<LoaderResult.Idempotent>(second)

        val finalItemCount = dao.listItems(first.programVersionId.value).size
        assertEquals(initialItemCount, finalItemCount, "idempotent replay must not duplicate rows")
    }

    @Test
    fun `loader rejects same programVersionId with different contentHash IT-IMP-006`() = runBlocking {
        val raw = readFixture("fixtures/valid-activatable.json")

        val first = loader.load(raw)
        val originalLoaded = assertIs<LoaderResult.Loaded>(first)

        // Mutate one structural field (versionLabel inside metadata is
        // covered by the hash; bumping it changes the canonical bytes)
        // and recompute the hash so the artifact is self-consistent.
        // The programVersionId stays the same so the loader must reject
        // it as a conflict.
        val mutated = mutateVersionLabelKeepingHashConsistent(raw, "v1.0.1-conflict")

        val second = loader.load(mutated)
        val conflict = assertIs<LoaderResult.Failure.ConflictDifferentHash>(second)

        assertEquals(originalLoaded.programVersionId, conflict.programVersionId)
        assertTrue(conflict.existingHash != conflict.incomingHash)

        // The original row must still be unchanged.
        val versionRow = assertNotNull(dao.findById(originalLoaded.programVersionId.value))
        assertEquals(conflict.existingHash, versionRow.contentHash)
    }

    @Test
    fun `tampered contentHash never reaches the database`() = runBlocking {
        val raw = readFixture("fixtures/valid-activatable.json")
        val tampered = replaceContentHash(raw, "deadbeef".repeat(8))

        val result = loader.load(tampered)
        assertIs<LoaderResult.Failure.HashMismatch>(result)

        assertEquals(emptyList(), dao.listAllVersions())
    }

    @Test
    fun `blocked status never reaches the database`() = runBlocking {
        val raw = readFixture("fixtures/blocked-missing-first-week-max.json")

        val result = loader.load(raw)
        val blocked = assertIs<LoaderResult.Failure.StatusBlocked>(result)
        assertTrue(blocked.criticalIssueCodes.isNotEmpty())

        assertEquals(emptyList(), dao.listAllVersions())
    }

    @Test
    fun `schemaVersion above supportedMax never reaches the database`() = runBlocking {
        val raw = readFixture("fixtures/valid-activatable.json")
        val bumped = bumpSchemaVersionKeepingHashConsistent(raw, newVersion = 99)

        val result = loader.load(bumped)
        assertIs<LoaderResult.Failure.UnsupportedSchemaVersion>(result)

        assertEquals(emptyList(), dao.listAllVersions())
    }

    @Test
    fun `v3 features round-trip into Room when present in the artifact`() = runBlocking {
        // Slice 1 round-trip contract (plan critique #5): the loader MUST
        // preserve schemaVersion 3 features through Room. The stock
        // `valid-activatable` fixture only exercises single percent
        // targets, so this test mutates it to inject every v3 feature
        // the workstream contract calls out:
        //   * percent range (`percentMin`/`percentMax`)
        //   * conjunctive `percent` + `rpe` targets on one set
        //   * `restMaxSecondsHint` on a prescription item
        //   * `warmupSetCount` on a prescription item
        // and then recomputes contentHash so the artifact is
        // self-consistent.
        val baseRaw = readFixture("fixtures/valid-activatable.json")
        val mutatedRaw = injectV3Features(baseRaw)

        val result = loader.load(mutatedRaw)
        val loaded = assertIs<LoaderResult.Loaded>(result)

        val items = dao.listItems(loaded.programVersionId.value)
        val targets = dao.listTargets(loaded.programVersionId.value)

        val restMaxItem = items.firstOrNull { it.itemId == V3_TARGET_ITEM_ID }
        assertNotNull(restMaxItem, "mutated item must persist")
        assertEquals(120, restMaxItem.restSecondsHint)
        assertEquals(180, restMaxItem.restMaxSecondsHint)
        assertEquals(2, restMaxItem.warmupSetCount)

        val mutatedSetTargets = targets.filter { it.setId == V3_TARGET_SET_ID }
        assertEquals(
            2,
            mutatedSetTargets.size,
            "conjunctive percent + RPE must persist as two target rows on the same set",
        )

        val percentRangeRow = mutatedSetTargets.firstOrNull { it.kind == "percent" }
        assertNotNull(percentRangeRow, "percent target row must survive")
        assertNull(percentRangeRow.percent, "range targets must NOT also carry the single-percent field")
        assertEquals(70.0, percentRangeRow.percentMin)
        assertEquals(75.0, percentRangeRow.percentMax)

        val rpeRow = mutatedSetTargets.firstOrNull { it.kind == "rpe" }
        assertNotNull(rpeRow, "conjunctive RPE companion must survive")
        assertEquals(8.0, rpeRow.rpeTarget)
    }


    // ---- helpers ----

    private fun readFixture(relative: String): String =
        schemaRoot.resolve(relative).toFile().readText()
    private fun replaceContentHash(raw: String, newHash: String): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val metadata = root.getValue("metadata").jsonObject
        val newMetadata = JsonObject(metadata.toMutableMap().also { it["contentHash"] = JsonPrimitive(newHash) })
        val mutated = buildJsonObject {
            root.forEach { (k, v) -> if (k == "metadata") put("metadata", newMetadata) else put(k, v) }
        }
        return mutated.toString()
    }

    private fun bumpSchemaVersionKeepingHashConsistent(raw: String, newVersion: Int): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val mutated = JsonObject(root.toMutableMap().also { it["schemaVersion"] = JsonPrimitive(newVersion) })
        // Note: we deliberately do NOT recompute the hash here. The
        // schemaVersion gate runs BEFORE the hash gate, so the
        // UnsupportedSchemaVersion failure is what should surface.
        return mutated.toString()
    }

    private fun mutateVersionLabelKeepingHashConsistent(raw: String, newLabel: String): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val metadata = root.getValue("metadata").jsonObject
        val withNewLabel = JsonObject(metadata.toMutableMap().also { it["versionLabel"] = JsonPrimitive(newLabel) })
        val pendingRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "metadata") put("metadata", withNewLabel) else put(k, v) }
        }
        val recomputed = ProgramResourceContentHash.compute(pendingRoot)
        val finalMetadata = JsonObject(withNewLabel.toMutableMap().also { it["contentHash"] = JsonPrimitive(recomputed) })
        val finalRoot = buildJsonObject {
            pendingRoot.forEach { (k, v) -> if (k == "metadata") put("metadata", finalMetadata) else put(k, v) }
        }
        return finalRoot.toString()
    }

    private fun locateSchemaRoot(): Path {
        var dir: Path = Paths.get("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve("schema").resolve("program-resource.schema.json")
            if (Files.exists(candidate)) return dir.resolve("schema")
            dir = dir.parent ?: error("Could not locate schema/ from working dir")
        }
    }

    private fun injectV3Features(raw: String): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val withSchemaV3 = JsonObject(root.toMutableMap().also { it["schemaVersion"] = JsonPrimitive(3) })
        val withMutatedStructure = JsonObject(
            withSchemaV3.toMutableMap().also { mut ->
                mut["programStructure"] = mutateProgramStructure(mut.getValue("programStructure").jsonObject)
            },
        )
        val recomputed = ProgramResourceContentHash.compute(withMutatedStructure)
        val metadata = withMutatedStructure.getValue("metadata").jsonObject
        val newMetadata = JsonObject(metadata.toMutableMap().also { it["contentHash"] = JsonPrimitive(recomputed) })
        val finalRoot = JsonObject(withMutatedStructure.toMutableMap().also { it["metadata"] = newMetadata })
        return finalRoot.toString()
    }

    private fun mutateProgramStructure(structure: JsonObject): JsonObject {
        val blocks = (structure.getValue("blocks") as kotlinx.serialization.json.JsonArray).map { block ->
            mutateBlock(block.jsonObject)
        }
        return JsonObject(structure.toMutableMap().also { it["blocks"] = kotlinx.serialization.json.JsonArray(blocks) })
    }

    private fun mutateBlock(block: JsonObject): JsonObject {
        val weeks = (block.getValue("weeks") as kotlinx.serialization.json.JsonArray).map { week ->
            mutateWeek(week.jsonObject)
        }
        return JsonObject(block.toMutableMap().also { it["weeks"] = kotlinx.serialization.json.JsonArray(weeks) })
    }

    private fun mutateWeek(week: JsonObject): JsonObject {
        val sessions = (week.getValue("sessions") as kotlinx.serialization.json.JsonArray).map { session ->
            mutateSession(session.jsonObject)
        }
        return JsonObject(week.toMutableMap().also { it["sessions"] = kotlinx.serialization.json.JsonArray(sessions) })
    }

    private fun mutateSession(session: JsonObject): JsonObject {
        val groups = (session.getValue("groups") as kotlinx.serialization.json.JsonArray).map { group ->
            mutateGroup(group.jsonObject)
        }
        return JsonObject(session.toMutableMap().also { it["groups"] = kotlinx.serialization.json.JsonArray(groups) })
    }

    private fun mutateGroup(group: JsonObject): JsonObject {
        val items = (group.getValue("prescriptionItems") as kotlinx.serialization.json.JsonArray).map { item ->
            val itemObj = item.jsonObject
            val id = (itemObj.getValue("id") as JsonPrimitive).content
            if (id == V3_TARGET_ITEM_ID) mutateTargetItem(itemObj) else itemObj
        }
        return JsonObject(group.toMutableMap().also { it["prescriptionItems"] = kotlinx.serialization.json.JsonArray(items) })
    }

    private fun mutateTargetItem(item: JsonObject): JsonObject {
        val sets = (item.getValue("setPrescriptions") as kotlinx.serialization.json.JsonArray).map { set ->
            val setObj = set.jsonObject
            val id = (setObj.getValue("id") as JsonPrimitive).content
            if (id == V3_TARGET_SET_ID) mutateTargetSet(setObj) else setObj
        }
        return JsonObject(
            item.toMutableMap().also {
                it["restSecondsHint"] = JsonPrimitive(120)
                it["restMaxSecondsHint"] = JsonPrimitive(180)
                it["warmupSetCount"] = JsonPrimitive(2)
                it["setPrescriptions"] = kotlinx.serialization.json.JsonArray(sets)
            },
        )
    }

    private fun mutateTargetSet(set: JsonObject): JsonObject {
        val percentRange = buildJsonObject {
            put("kind", JsonPrimitive("percent"))
            put("referenceId", JsonPrimitive("tm-squat"))
            put("percentMin", JsonPrimitive(70.0))
            put("percentMax", JsonPrimitive(75.0))
            put("reps", JsonPrimitive(5))
        }
        val rpe = buildJsonObject {
            put("kind", JsonPrimitive("rpe"))
            put("target", JsonPrimitive(8.0))
        }
        return JsonObject(
            set.toMutableMap().also {
                it["targets"] = kotlinx.serialization.json.JsonArray(listOf(percentRange, rpe))
            },
        )
    }

    companion object {
        private const val V3_TARGET_ITEM_ID = "w1-d1-g1-i1"
        private const val V3_TARGET_SET_ID = "w1-d1-g1-i1-s1"
    }
}
