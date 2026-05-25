package dev.liftorium.domain.resource

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parser + recheck gates for [ProgramResourceParseService].
 *
 * These tests cover the pure-JVM portion of the loader pipeline
 * (parsing, schemaVersion gate, content-hash recompute, status gate).
 * The DB-side idempotency + conflict checks are covered by the
 * Robolectric loader integration tests in `:data`.
 */
class ProgramResourceParseServiceTest {

    private val schemaRoot: Path = locateSchemaRoot()

    // ---- schemaVersion gate ----

    @Test
    fun `valid-activatable fixture parses successfully`() {
        val outcome = parseFixture("fixtures/valid-activatable.json")
        val parsed = assertIs<ParseOutcome.Parsed>(outcome)
        assertEquals(ValidationStatus.Activatable, parsed.resource.validationStatus)
    }

    @Test
    fun `valid-activatable-warnings fixture parses successfully`() {
        val outcome = parseFixture("fixtures/valid-activatable-warnings.json")
        val parsed = assertIs<ParseOutcome.Parsed>(outcome)
        assertEquals(ValidationStatus.Activatable, parsed.resource.validationStatus)
        assertTrue(parsed.resource.validationIssues.isNotEmpty(), "warnings fixture must carry validation issues")
    }

    @Test
    fun `schemaVersion above supportedMax is rejected with UnsupportedSchemaVersion`() {
        val raw = readResourceString("fixtures/valid-activatable.json")
        val bumped = bumpSchemaVersion(raw, ProgramResourceParseService.MAX_SCHEMA_VERSION + 1)

        val failure = assertFailureIs<LoaderResult.Failure.UnsupportedSchemaVersion>(ProgramResourceParseService.parse(bumped))

        assertEquals(ProgramResourceParseService.MAX_SCHEMA_VERSION + 1, failure.actual)
        assertEquals(ProgramResourceParseService.MAX_SCHEMA_VERSION, failure.supportedMax)
    }

    @Test
    fun `schemaVersion below supportedMin is rejected with Malformed`() {
        val raw = readResourceString("fixtures/valid-activatable.json")
        val bumped = bumpSchemaVersion(raw, ProgramResourceParseService.MIN_SCHEMA_VERSION - 1)

        assertFailureIs<LoaderResult.Failure.Malformed>(ProgramResourceParseService.parse(bumped))
    }

    @Test
    fun `missing schemaVersion is Malformed`() {
        val raw = readResourceString("fixtures/valid-activatable.json")
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val without = JsonObject(root.filterKeys { it != "schemaVersion" })

        assertFailureIs<LoaderResult.Failure.Malformed>(
            ProgramResourceParseService.parse(without.toString()),
        )
    }

    @Test
    fun `non-object root JSON is Malformed`() {
        assertFailureIs<LoaderResult.Failure.Malformed>(ProgramResourceParseService.parse("[]"))
        assertFailureIs<LoaderResult.Failure.Malformed>(ProgramResourceParseService.parse("\"oops\""))
    }

    @Test
    fun `syntactically invalid JSON is Malformed`() {
        assertFailureIs<LoaderResult.Failure.Malformed>(ProgramResourceParseService.parse("{ not json"))
    }

    // ---- DTO shape gate ----

    @Test
    fun `unknown top-level keys are rejected as Malformed`() {
        val raw = readResourceString("fixtures/valid-activatable.json")
        val tampered = injectExtraKey(raw, "surpriseField")

        assertFailureIs<LoaderResult.Failure.Malformed>(ProgramResourceParseService.parse(tampered))
    }

    // ---- Content-hash recompute gate ----

    @Test
    fun `tampered contentHash is rejected with HashMismatch`() {
        val raw = readResourceString("fixtures/valid-activatable.json")
        val tampered = replaceContentHash(raw, "deadbeef".repeat(8))

        val failure = assertFailureIs<LoaderResult.Failure.HashMismatch>(ProgramResourceParseService.parse(tampered))

        assertEquals("deadbeef".repeat(8), failure.declared)
        assertTrue(failure.computed.isNotBlank())
        assertTrue(failure.computed != failure.declared)
    }

    @Test
    fun `blocked-content-hash-mismatch fixture is caught by HashMismatch and never reaches status gate`() {
        // This fixture's validationStatus is "activatable" but the recorded
        // contentHash does NOT match the canonical bytes; import-workflow tagged it
        // as broken artwork. The runner's file-integrity gate must fire
        // before the status gate.
        val outcome = parseFixture("fixtures/blocked-content-hash-mismatch.json")
        assertFailureIs<LoaderResult.Failure.HashMismatch>(outcome)
    }

    // ---- validationStatus gate ----

    @Test
    fun `validationStatus blocked is rejected and critical codes are captured`() {
        val outcome = parseFixture("fixtures/blocked-missing-first-week-max.json")
        val failure = assertFailureIs<LoaderResult.Failure.StatusBlocked>(outcome)

        assertEquals(ValidationStatus.Blocked, failure.status)
        assertTrue(
            failure.criticalIssueCodes.isNotEmpty(),
            "blocked fixture should expose at least one critical code",
        )
    }

    @Test
    fun `validationStatus rejected is rejected with StatusBlocked`() {
        val outcome = parseFixture("fixtures/rejected-not-activatable.json")
        val failure = assertFailureIs<LoaderResult.Failure.StatusBlocked>(outcome)

        assertEquals(ValidationStatus.Rejected, failure.status)
    }

    @Test
    fun `pending_runtime_references status is accepted by the parser`() {
        // Synthesize a pending_runtime_references resource from the
        // valid-activatable fixture: flip the status, recompute the hash,
        // and ensure the parser lets it through (run-scoped ref injection
        // is a use-case concern, not a parser concern).
        val raw = readResourceString("fixtures/valid-activatable.json")
        val withPending = withStatusAndRecomputedHash(raw, ValidationStatus.PendingRuntimeReferences)

        val outcome = ProgramResourceParseService.parse(withPending)
        val parsed = assertIs<ParseOutcome.Parsed>(outcome)
        assertEquals(ValidationStatus.PendingRuntimeReferences, parsed.resource.validationStatus)
    }

    // ---- helpers ----

    private fun parseFixture(relative: String): ParseOutcome =
        ProgramResourceParseService.parse(readResourceString(relative))

    private fun readResourceString(relative: String): String =
        Files.readString(schemaRoot.resolve(relative))

    private inline fun <reified T : LoaderResult.Failure> assertFailureIs(outcome: ParseOutcome): T {
        val failed = outcome as? ParseOutcome.Failed
            ?: fail("Expected Failed outcome, got $outcome")
        return assertIs<T>(failed.failure)
    }

    private fun bumpSchemaVersion(raw: String, newVersion: Int): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val mutated = JsonObject(root.toMutableMap().also { it["schemaVersion"] = JsonPrimitive(newVersion) })
        return mutated.toString()
    }

    private fun injectExtraKey(raw: String, key: String): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val mutated = JsonObject(root.toMutableMap().also { it[key] = JsonPrimitive("unexpected") })
        return mutated.toString()
    }

    private fun replaceContentHash(raw: String, newHash: String): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val metadata = root.getValue("metadata").jsonObject
        val newMetadata = JsonObject(metadata.toMutableMap().also { it["contentHash"] = JsonPrimitive(newHash) })
        val mutated = JsonObject(root.toMutableMap().also { it["metadata"] = newMetadata })
        return mutated.toString()
    }

    private fun withStatusAndRecomputedHash(raw: String, status: ValidationStatus): String {
        val root = ProgramResourceJson.parseToJsonElement(raw).jsonObject
        val statusWire = when (status) {
            ValidationStatus.Activatable -> "activatable"
            ValidationStatus.PendingRuntimeReferences -> "pending_runtime_references"
            ValidationStatus.Blocked -> "blocked"
            ValidationStatus.Rejected -> "rejected"
        }
        val withStatus = JsonObject(root.toMutableMap().also { it["validationStatus"] = JsonPrimitive(statusWire) })
        val recomputed = ProgramResourceContentHash.compute(withStatus)
        val metadata = withStatus.getValue("metadata").jsonObject
        val newMetadata = JsonObject(metadata.toMutableMap().also { it["contentHash"] = JsonPrimitive(recomputed) })
        val mutated = buildJsonObject {
            withStatus.forEach { (k, v) ->
                if (k == "metadata") put("metadata", newMetadata) else put(k, v)
            }
        }
        return mutated.toString()
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
