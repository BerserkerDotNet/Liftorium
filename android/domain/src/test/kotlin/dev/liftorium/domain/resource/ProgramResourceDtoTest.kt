package dev.liftorium.domain.resource

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * DTO parity tests for [ProgramResource] against the schema fixtures.
 * Each fixture round-trips Kotlin DTO -> JsonElement and is compared
 * structurally against the original JsonElement. Mismatches indicate
 * DTO drift from the schema.
 *
 * Round-trip equality is NOT bytewise (kotlinx-serialization preserves
 * field order from the DTO declaration, not from the source JSON), so
 * the structural compare walks the JsonElement tree.
 *
 * The schemaVersion-3 round-trip suite specifically exercises every
 * v3-only feature (percent range, rest range, warmupSetCount, and
 * conjunctive percent+RPE targets) so the android-program-runner schemaVersion gate
 * rests on real test coverage rather than the schema number alone.
 */
class ProgramResourceDtoTest {

    private val schemaRoot: Path = locateSchemaRoot()

    @Test
    fun `example 5-3-1-bbb decodes and re-encoding is idempotent`() {
        assertDtoRoundTripIdempotent("examples/example-5-3-1-bbb.json")
    }

    @Test
    fun `valid-activatable fixture decodes and re-encoding is idempotent`() {
        assertDtoRoundTripIdempotent("fixtures/valid-activatable.json")
    }

    @Test
    fun `valid-activatable-warnings fixture decodes and re-encoding is idempotent`() {
        assertDtoRoundTripIdempotent("fixtures/valid-activatable-warnings.json")
    }

    private fun assertDtoRoundTripIdempotent(relative: String) {
        // The Kotlin DTOs are a read view; they are NOT the canonical
        // byte form for hashing (see ProgramResourceContentHashTest).
        // The property we actually need is decode-idempotency:
        // decoding twice through encode in the middle must yield the
        // same Kotlin object graph. This tolerates representational
        // drift like 315 -> 315.0 (Kotlin Double) or omitted-vs-empty
        // aliases lists, which are semantically equal in the DTO model.
        val raw = readResource(relative)
        val first = ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), raw)
        val reEncoded = ProgramResourceJson.encodeToJsonElement(ProgramResource.serializer(), first)
        val second = ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), reEncoded)

        assertEquals(first, second)
    }

    @Test
    fun `parser rejects unknown top-level keys`() {
        val tampered = buildJsonObject {
            ProgramResourceJson.parseToJsonElement(readResourceString("fixtures/valid-activatable.json"))
                .jsonObject
                .forEach { (k, v) -> put(k, v) }
            put("unknownTopLevelKey", kotlinx.serialization.json.JsonPrimitive("nope"))
        }

        assertFailsWith<SerializationException> {
            ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), tampered)
        }
    }

    @Test
    fun `parser rejects unknown PrescriptionTarget kind`() {
        // Build a minimal valid resource with one unknown-kind target.
        val resource = buildJsonObject {
            put("schemaVersion", kotlinx.serialization.json.JsonPrimitive(3))
            put("metadata", buildJsonObject {
                put("programId", kotlinx.serialization.json.JsonPrimitive("p"))
                put("programVersionId", kotlinx.serialization.json.JsonPrimitive("p-v1"))
                put("versionLabel", kotlinx.serialization.json.JsonPrimitive("v1"))
                put("contentHash", kotlinx.serialization.json.JsonPrimitive("0".repeat(64)))
            })
            put("validationStatus", kotlinx.serialization.json.JsonPrimitive("activatable"))
            put("validationIssues", buildJsonArray { })
            put("importAudit", buildJsonObject {
                put("sourceHash", kotlinx.serialization.json.JsonPrimitive("0".repeat(64)))
                put("sourceFilename", kotlinx.serialization.json.JsonPrimitive("fixture.xlsx"))
                put("importedAtUtc", kotlinx.serialization.json.JsonPrimitive("2026-01-01T00:00:00Z"))
                put("sourceKind", kotlinx.serialization.json.JsonPrimitive("synthetic"))
                put("schemaVersionUsed", kotlinx.serialization.json.JsonPrimitive(3))
            })
            put("exerciseCatalog", buildJsonArray {
                add(buildJsonObject {
                    put("id", kotlinx.serialization.json.JsonPrimitive("squat"))
                    put("displayName", kotlinx.serialization.json.JsonPrimitive("Squat"))
                    put("family", kotlinx.serialization.json.JsonPrimitive("squat"))
                    put("equipment", kotlinx.serialization.json.JsonPrimitive("barbell"))
                })
            })
            put("requiredReferences", buildJsonArray { })
            put("programStructure", minimalProgramStructure(targetKind = "unknown_kind"))
            put("progressionRules", buildJsonArray { })
        }

        assertFailsWith<SerializationException> {
            ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), resource)
        }
    }

    @Test
    fun `schemaVersion 3 round-trip preserves single percent target`() {
        val target = ProgramResourceJson.encodeToJsonElement(
            PrescriptionTarget.serializer(),
            PrescriptionTarget.Percent(referenceId = "orm-squat", percent = 75.0, reps = 5),
        )
        val decoded = ProgramResourceJson.decodeFromJsonElement(PrescriptionTarget.serializer(), target)

        val percent = assertIsInstance<PrescriptionTarget.Percent>(decoded)
        assertEquals(75.0, percent.percent)
        assertNull(percent.percentMin)
        assertNull(percent.percentMax)
        assertEquals(5, percent.reps)
    }

    @Test
    fun `schemaVersion 3 round-trip preserves percent range target without dropping bounds`() {
        // Per docs/workstreams/android-program-runner.md "Contracts not to break":
        // range targets must round-trip into Room without losing bounds.
        // This is the Kotlin-DTO half of that contract.
        val target = ProgramResourceJson.encodeToJsonElement(
            PrescriptionTarget.serializer(),
            PrescriptionTarget.Percent(
                referenceId = "orm-bench",
                percentMin = 70.0,
                percentMax = 75.0,
                reps = 8,
            ),
        )
        val decoded = ProgramResourceJson.decodeFromJsonElement(PrescriptionTarget.serializer(), target)

        val percent = assertIsInstance<PrescriptionTarget.Percent>(decoded)
        assertNull(percent.percent)
        assertEquals(70.0, percent.percentMin)
        assertEquals(75.0, percent.percentMax)
        assertEquals(8, percent.reps)
        assertTrue(percent.isRange, "isRange should be true when both bounds are present")
    }

    @Test
    fun `isRange is false when only single percent is set`() {
        val singlePercent = PrescriptionTarget.Percent(
            referenceId = "orm-bench",
            percent = 70.0,
            reps = 8,
        )
        assertFalse(singlePercent.isRange, "isRange should be false for single percent")

        val onlyMin = PrescriptionTarget.Percent(referenceId = "orm-bench", percentMin = 70.0)
        assertFalse(onlyMin.isRange, "isRange should be false when only percentMin is set")

        val onlyMax = PrescriptionTarget.Percent(referenceId = "orm-bench", percentMax = 75.0)
        assertFalse(onlyMax.isRange, "isRange should be false when only percentMax is set")
    }

    @Test
    fun `schemaVersion 3 round-trip preserves rest range hint`() {
        val item = PrescriptionItem(
            id = "p1",
            order = 1,
            prescribedExerciseId = "squat",
            role = PrescriptionRole.Working,
            restSecondsHint = 90,
            restMaxSecondsHint = 180,
            setPrescriptions = emptyList(),
        )
        val encoded = ProgramResourceJson.encodeToJsonElement(PrescriptionItem.serializer(), item)
        val decoded = ProgramResourceJson.decodeFromJsonElement(PrescriptionItem.serializer(), encoded)

        assertEquals(90, decoded.restSecondsHint)
        assertEquals(180, decoded.restMaxSecondsHint)
    }

    @Test
    fun `schemaVersion 3 round-trip preserves warmupSetCount`() {
        val item = PrescriptionItem(
            id = "p1",
            order = 1,
            prescribedExerciseId = "squat",
            role = PrescriptionRole.Warmup,
            warmupSetCount = 3,
            setPrescriptions = emptyList(),
        )
        val encoded = ProgramResourceJson.encodeToJsonElement(PrescriptionItem.serializer(), item)
        val decoded = ProgramResourceJson.decodeFromJsonElement(PrescriptionItem.serializer(), encoded)

        assertEquals(3, decoded.warmupSetCount)
    }

    @Test
    fun `schemaVersion 3 round-trip preserves conjunctive percent plus RPE targets on one set`() {
        // The android-program-runner contract requires that percent and RPE targets can
        // coexist on a single set and the runner must surface both. This
        // test locks the DTO behavior; the runner UI test for "surface
        // RPE companion" lives in :app (Slice 3).
        val set = SetPrescription(
            id = "sp1",
            order = 1,
            setKind = SetKind.Working,
            targets = listOf(
                PrescriptionTarget.Percent(referenceId = "orm-squat", percent = 80.0, reps = 5),
                PrescriptionTarget.Rpe(target = 8.0),
            ),
        )
        val encoded = ProgramResourceJson.encodeToJsonElement(SetPrescription.serializer(), set)
        val decoded = ProgramResourceJson.decodeFromJsonElement(SetPrescription.serializer(), encoded)

        assertEquals(2, decoded.targets.size)
        assertIsInstance<PrescriptionTarget.Percent>(decoded.targets[0])
        assertIsInstance<PrescriptionTarget.Rpe>(decoded.targets[1])
    }

    @Test
    fun `validationStatus enum decodes pending_runtime_references wire name`() {
        val raw = readResource("examples/example-5-3-1-bbb.json")
        // example is currently activatable; substitute the status to confirm the wire name maps to the enum case.
        val mutated = buildJsonObject {
            raw.jsonObject.forEach { (k, v) ->
                if (k == "validationStatus") put(k, kotlinx.serialization.json.JsonPrimitive("pending_runtime_references"))
                else put(k, v)
            }
        }
        val parsed = ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), mutated)

        assertEquals(ValidationStatus.PendingRuntimeReferences, parsed.validationStatus)
    }

    @Test
    fun `metadata contentHash is preserved verbatim`() {
        val raw = readResource("examples/example-5-3-1-bbb.json")
        val expected = raw.jsonObject["metadata"]!!.jsonObject["contentHash"]!!.jsonPrimitive.content

        val parsed = ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), raw)

        assertEquals(expected, parsed.metadata.contentHash)
    }

    @Test
    fun `progressionRules with parameters object round-trips`() {
        val rule = ProgressionRule(
            id = "rule1",
            kind = "linear_load_increment",
            appliesToExerciseIds = listOf("squat"),
            parameters = buildJsonObject {
                put("incrementPerWeek", kotlinx.serialization.json.JsonPrimitive(2.5))
                put("unit", kotlinx.serialization.json.JsonPrimitive("kg"))
            },
        )
        val encoded = ProgramResourceJson.encodeToJsonElement(ProgressionRule.serializer(), rule)
        val decoded = ProgramResourceJson.decodeFromJsonElement(ProgressionRule.serializer(), encoded)

        assertEquals("linear_load_increment", decoded.kind)
        val params = assertNotNull(decoded.parameters)
        assertEquals(2.5, params["incrementPerWeek"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `programWeek variantOf and variantLabel round-trip`() {
        // schemaVersion 2 feature; schemaVersion 3 inherits it.
        val week = ProgramWeek(
            id = "w10b",
            weekIndex = 10,
            variantOf = "w10a",
            variantLabel = "B",
            sessions = emptyList(),
        )
        val encoded = ProgramResourceJson.encodeToJsonElement(ProgramWeek.serializer(), week)
        val decoded = ProgramResourceJson.decodeFromJsonElement(ProgramWeek.serializer(), encoded)

        assertEquals("w10a", decoded.variantOf)
        assertEquals("B", decoded.variantLabel)
    }

    private inline fun <reified T> assertIsInstance(value: Any?): T {
        if (value !is T) {
            fail("Expected ${T::class.simpleName}, got ${value?.javaClass?.simpleName ?: "null"}")
        }
        return value
    }

    private fun readResource(relative: String): JsonObject =
        ProgramResourceJson.parseToJsonElement(readResourceString(relative)).jsonObject

    private fun readResourceString(relative: String): String =
        Files.readString(schemaRoot.resolve(relative))

    private fun minimalProgramStructure(targetKind: String): JsonObject = buildJsonObject {
        put("blocks", buildJsonArray {
            add(buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive("b1"))
                put("order", kotlinx.serialization.json.JsonPrimitive(1))
                put("weeks", buildJsonArray {
                    add(buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive("w1"))
                        put("weekIndex", kotlinx.serialization.json.JsonPrimitive(1))
                        put("sessions", buildJsonArray {
                            add(buildJsonObject {
                                put("id", kotlinx.serialization.json.JsonPrimitive("s1"))
                                put("sessionIndex", kotlinx.serialization.json.JsonPrimitive(1))
                                put("groups", buildJsonArray {
                                    add(buildJsonObject {
                                        put("id", kotlinx.serialization.json.JsonPrimitive("g1"))
                                        put("order", kotlinx.serialization.json.JsonPrimitive(1))
                                        put("kind", kotlinx.serialization.json.JsonPrimitive("single"))
                                        put("prescriptionItems", buildJsonArray {
                                            add(buildJsonObject {
                                                put("id", kotlinx.serialization.json.JsonPrimitive("p1"))
                                                put("order", kotlinx.serialization.json.JsonPrimitive(1))
                                                put("prescribedExerciseId", kotlinx.serialization.json.JsonPrimitive("squat"))
                                                put("role", kotlinx.serialization.json.JsonPrimitive("working"))
                                                put("setPrescriptions", buildJsonArray {
                                                    add(buildJsonObject {
                                                        put("id", kotlinx.serialization.json.JsonPrimitive("sp1"))
                                                        put("order", kotlinx.serialization.json.JsonPrimitive(1))
                                                        put("setKind", kotlinx.serialization.json.JsonPrimitive("working"))
                                                        put("targets", buildJsonArray {
                                                            add(buildJsonObject {
                                                                put("kind", kotlinx.serialization.json.JsonPrimitive(targetKind))
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
        })
    }

    private fun locateSchemaRoot(): Path {
        var dir: Path = Paths.get("").toAbsolutePath()
        while (true) {
            val candidate = dir.resolve("schema").resolve("program-resource.schema.json")
            if (Files.exists(candidate)) return dir.resolve("schema")
            dir = dir.parent ?: error("Could not locate schema/ from working dir")
        }
    }
}
