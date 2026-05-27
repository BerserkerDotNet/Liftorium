package dev.liftorium.domain.resource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cross-language parity tests for the SHA-256 program-resource content
 * hash. The TypeScript reference is `schema/hash.ts` and the recorded
 * digests in `schema/examples/` and `schema/fixtures/` are the
 * cross-language anchor: if the Kotlin recomputer disagrees with the
 * recorded digest on a finalized fixture, the loader's integrity check
 * is broken in either import-workflow or android-program-runner and must be fixed before any
 * import flow lands on real devices.
 *
 * The fixtures are read from the schema project on the filesystem to
 * avoid duplicating canonical example bytes inside the Android module.
 * Keeping a single source of truth means schema changes propagate
 * automatically.
 */
class ProgramResourceContentHashTest {

    private val schemaRoot: Path = locateSchemaRoot()

    private val parser = Json { ignoreUnknownKeys = false }

    @Test
    fun `example 5-3-1-bbb recomputes to its recorded contentHash`() {
        val (resource, declaredHash) = loadResourceWithDeclaredHash("examples/example-5-3-1-bbb.json")

        val computed = ProgramResourceContentHash.compute(resource)

        assertEquals(declaredHash, computed)
    }

    @Test
    fun `valid-activatable fixture recomputes to its recorded contentHash`() {
        val (resource, declaredHash) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")

        val computed = ProgramResourceContentHash.compute(resource)

        assertEquals(declaredHash, computed)
    }

    @Test
    fun `valid-activatable-warnings fixture recomputes to its recorded contentHash`() {
        val (resource, declaredHash) = loadResourceWithDeclaredHash("fixtures/valid-activatable-warnings.json")

        val computed = ProgramResourceContentHash.compute(resource)

        assertEquals(declaredHash, computed)
    }

    @Test
    fun `hash is stable when validationStatus is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutated = mutateTopLevel(resource, "validationStatus", JsonPrimitive("rejected"))

        assertEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash is stable when validationIssues is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutated = mutateTopLevel(
            resource,
            "validationIssues",
            buildJsonArray {
                add(buildJsonObject {
                    put("severity", JsonPrimitive("info"))
                    put("code", JsonPrimitive("synthetic.injection"))
                    put("message", JsonPrimitive("ignored for hash"))
                })
            },
        )

        assertEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash is stable when importAudit is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutatedAudit = JsonObject(
            resource.jsonObject["importAudit"]!!.jsonObject.toMutableMap().apply {
                put("sourceFilename", JsonPrimitive("renamed.xlsx"))
            },
        )
        val mutated = mutateTopLevel(resource, "importAudit", mutatedAudit)

        assertEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash is stable when metadata-contentHash is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutatedMeta = JsonObject(
            resource.jsonObject["metadata"]!!.jsonObject.toMutableMap().apply {
                put("contentHash", JsonPrimitive("0".repeat(64)))
            },
        )
        val mutated = mutateTopLevel(resource, "metadata", mutatedMeta)

        assertEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash changes when schemaVersion is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutated = mutateTopLevel(
            resource,
            "schemaVersion",
            JsonPrimitive(resource.jsonObject["schemaVersion"]!!.jsonPrimitive.content.toInt() + 1),
        )

        assertNotEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash changes when programStructure is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutatedStructure = JsonObject(
            resource.jsonObject["programStructure"]!!.jsonObject.toMutableMap().apply {
                put("blocks", buildJsonArray { /* intentionally cleared */ })
            },
        )
        val mutated = mutateTopLevel(resource, "programStructure", mutatedStructure)

        assertNotEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash changes when exerciseCatalog is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutated = mutateTopLevel(resource, "exerciseCatalog", buildJsonArray { /* empty */ })

        assertNotEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash changes when requiredReferences is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutated = mutateTopLevel(resource, "requiredReferences", buildJsonArray { /* empty */ })

        assertNotEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash changes when metadata non-contentHash field is mutated`() {
        val (resource, _) = loadResourceWithDeclaredHash("fixtures/valid-activatable.json")
        val original = ProgramResourceContentHash.compute(resource)

        val mutatedMeta = JsonObject(
            resource.jsonObject["metadata"]!!.jsonObject.toMutableMap().apply {
                put("versionLabel", JsonPrimitive("v999"))
            },
        )
        val mutated = mutateTopLevel(resource, "metadata", mutatedMeta)

        assertNotEquals(original, ProgramResourceContentHash.compute(mutated))
    }

    @Test
    fun `hash distinguishes single percent target from percent range target`() {
        val single = buildJsonObject {
            put("schemaVersion", JsonPrimitive(3))
            put("metadata", buildJsonObject {
                put("programId", JsonPrimitive("p"))
                put("programVersionId", JsonPrimitive("p-v1"))
                put("versionLabel", JsonPrimitive("v1"))
                put("contentHash", JsonPrimitive("ignored"))
            })
            put("target", buildJsonObject {
                put("kind", JsonPrimitive("percent"))
                put("percent", JsonPrimitive(77.5))
                put("referenceId", JsonPrimitive("orm-squat"))
            })
        }

        val range = buildJsonObject {
            put("schemaVersion", JsonPrimitive(3))
            put("metadata", buildJsonObject {
                put("programId", JsonPrimitive("p"))
                put("programVersionId", JsonPrimitive("p-v1"))
                put("versionLabel", JsonPrimitive("v1"))
                put("contentHash", JsonPrimitive("ignored"))
            })
            put("target", buildJsonObject {
                put("kind", JsonPrimitive("percent"))
                put("percentMin", JsonPrimitive(75.0))
                put("percentMax", JsonPrimitive(80.0))
                put("referenceId", JsonPrimitive("orm-squat"))
            })
        }

        // The hash function only canonicalizes a fixed top-level key
        // set, so to distinguish targets we treat the unknown
        // "target" key as ignored; here we embed the difference inside
        // programStructure via a minimal stub.
        val singleResource = withProgramStructureTarget(single.jsonObject["target"]!!.jsonObject)
        val rangeResource = withProgramStructureTarget(range.jsonObject["target"]!!.jsonObject)

        assertNotEquals(
            ProgramResourceContentHash.compute(singleResource),
            ProgramResourceContentHash.compute(rangeResource),
        )
    }

    @Test
    fun `canonical stringify sorts object keys ascending`() {
        val input = buildJsonObject {
            put("z", JsonPrimitive(1))
            put("a", JsonPrimitive(2))
            put("m", JsonPrimitive(3))
        }

        val output = ProgramResourceContentHash.canonicalStringify(input)

        assertEquals("""{"a":2,"m":3,"z":1}""", output)
    }

    @Test
    fun `canonical stringify recurses into nested objects with sorted keys`() {
        val input = buildJsonObject {
            put("outer", buildJsonObject {
                put("z", JsonPrimitive(true))
                put("a", JsonPrimitive(false))
            })
        }

        val output = ProgramResourceContentHash.canonicalStringify(input)

        assertEquals("""{"outer":{"a":false,"z":true}}""", output)
    }

    @Test
    fun `canonical stringify preserves array order`() {
        val input = buildJsonArray {
            add(JsonPrimitive(3))
            add(JsonPrimitive(1))
            add(JsonPrimitive(2))
        }

        val output = ProgramResourceContentHash.canonicalStringify(input)

        assertEquals("[3,1,2]", output)
    }

    @Test
    fun `canonical stringify emits null primitives as JSON null`() {
        val input = buildJsonObject {
            put("nullable", JsonNull)
        }

        val output = ProgramResourceContentHash.canonicalStringify(input)

        assertEquals("""{"nullable":null}""", output)
    }

    @Test
    fun `canonical stringify escapes special characters in strings`() {
        // Matches JSON.stringify behavior for newline, backslash, quote.
        val input = JsonPrimitive("a\"b\\c\nd")

        val output = ProgramResourceContentHash.canonicalStringify(input)

        assertEquals("\"a\\\"b\\\\c\\nd\"", output)
    }

    @Test
    fun `canonicalize is a no-op for non-object roots`() {
        val arr: JsonElement = buildJsonArray { add(JsonPrimitive(1)) }

        assertEquals(arr, ProgramResourceContentHash.canonicalize(arr))
    }

    @Test
    fun `canonicalize drops top-level keys excluded from the hash`() {
        val input = buildJsonObject {
            put("schemaVersion", JsonPrimitive(3))
            put("validationStatus", JsonPrimitive("activatable"))
            put("validationIssues", buildJsonArray { })
            put("importAudit", buildJsonObject { })
            put("metadata", buildJsonObject {
                put("programId", JsonPrimitive("p"))
                put("contentHash", JsonPrimitive("ignored"))
            })
        }

        val view = ProgramResourceContentHash.canonicalize(input) as JsonObject

        assertTrue("validationStatus" !in view)
        assertTrue("validationIssues" !in view)
        assertTrue("importAudit" !in view)
        assertTrue("schemaVersion" in view)
        assertTrue("metadata" in view)
        assertTrue("contentHash" !in (view["metadata"] as JsonObject))
    }

    @Test
    fun `every fixture with a real contentHash digest recomputes correctly`() {
        // Scans schema/fixtures/ for finalized resources whose
        // metadata.contentHash is a 64-char hex digest (i.e. NOT a
        // synthetic sentinel like all zeros or the deliberately broken
        // mismatch fixture). Each must recompute to the recorded value.
        val fixturesDir = schemaRoot.resolve("fixtures")
        val hexPattern = Regex("^[A-Fa-f0-9]{64}$")
        val zeroDigest = "0".repeat(64)
        val excluded = setOf(
            // This fixture deliberately stores a wrong hash so the
            // schema-level "hash mismatch" rule has a fixture; do not
            // assert it round-trips.
            "blocked-content-hash-mismatch.json",
        )

        val checked = ArrayList<Path>()
        Files.list(fixturesDir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".json") }
                .filter { it.fileName.toString() !in excluded }
                .forEach { path ->
                    val resource = parser.parseToJsonElement(Files.readString(path)).jsonObject
                    val declared = (resource["metadata"] as? JsonObject)
                        ?.get("contentHash")
                        ?.jsonPrimitive
                        ?.content
                    if (declared != null && declared != zeroDigest && hexPattern.matches(declared)) {
                        val computed = ProgramResourceContentHash.compute(resource)
                        assertEquals(declared, computed, "Hash mismatch for fixture ${path.fileName}")
                        checked.add(path)
                    }
                }
        }
        // Sanity: we must have actually checked at least one fixture, otherwise the test is vacuous.
        assertTrue(checked.isNotEmpty(), "Expected to find at least one hex-digest fixture; checked=$checked")
    }

    private fun withProgramStructureTarget(target: JsonObject): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(3))
        put("metadata", buildJsonObject {
            put("programId", JsonPrimitive("p"))
            put("programVersionId", JsonPrimitive("p-v1"))
            put("versionLabel", JsonPrimitive("v1"))
        })
        put("programStructure", buildJsonObject {
            put("blocks", buildJsonArray {
                add(buildJsonObject {
                    put("id", JsonPrimitive("b1"))
                    put("order", JsonPrimitive(1))
                    put("weeks", buildJsonArray {
                        add(buildJsonObject {
                            put("id", JsonPrimitive("w1"))
                            put("weekIndex", JsonPrimitive(1))
                            put("sessions", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive("s1"))
                                    put("sessionIndex", JsonPrimitive(1))
                                    put("groups", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", JsonPrimitive("g1"))
                                            put("order", JsonPrimitive(1))
                                            put("kind", JsonPrimitive("single"))
                                            put("prescriptionItems", buildJsonArray {
                                                add(buildJsonObject {
                                                    put("id", JsonPrimitive("p1"))
                                                    put("order", JsonPrimitive(1))
                                                    put("prescribedExerciseId", JsonPrimitive("squat"))
                                                    put("role", JsonPrimitive("working"))
                                                    put("setPrescriptions", buildJsonArray {
                                                        add(buildJsonObject {
                                                            put("id", JsonPrimitive("sp1"))
                                                            put("order", JsonPrimitive(1))
                                                            put("setKind", JsonPrimitive("working"))
                                                            put("targets", buildJsonArray { add(target) })
                                                        })
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        })
    }

    private fun mutateTopLevel(resource: JsonObject, key: String, value: JsonElement): JsonObject {
        val mutable = resource.toMutableMap()
        mutable[key] = value
        return JsonObject(mutable)
    }

    private fun loadResourceWithDeclaredHash(relative: String): Pair<JsonObject, String> {
        val path = schemaRoot.resolve(relative)
        val parsed = parser.parseToJsonElement(Files.readString(path)).jsonObject
        val declared = parsed["metadata"]?.jsonObject?.get("contentHash")?.jsonPrimitive?.content
            ?: error("Fixture $relative has no metadata.contentHash")
        return parsed to declared
    }

    private fun locateSchemaRoot(): Path {
        // Tests run with the working directory set to the project
        // directory (android/domain). Walk up until we find the schema
        // folder, so this test is robust against changes to the test
        // working directory.
        var dir: Path = Paths.get("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve("schema").resolve("program-resource.schema.json")
            if (Files.exists(candidate)) return dir.resolve("schema")
            dir = dir.parent ?: error("Could not locate schema/ from working dir")
        }
    }
}
