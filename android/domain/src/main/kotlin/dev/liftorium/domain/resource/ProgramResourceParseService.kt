package dev.liftorium.domain.resource

import dev.liftorium.core.KoverIgnore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Pure-JVM parsing + recheck pipeline for finalized program resources.
 *
 * Runs the device-side gates that android-program-runner owns (recheck_only strategy,
 * see ADR in `docs/decisions.md`):
 *
 * 1. JSON parse to a [JsonElement] (the JsonElement is the canonical
 *    form for the content-hash recompute; never use the Kotlin DTO).
 * 2. schemaVersion within [MIN_SCHEMA_VERSION] .. [MAX_SCHEMA_VERSION].
 * 3. DTO decode (catches unknown keys / unknown discriminator values).
 * 4. `contentHash` recompute and compare against the declared value.
 * 5. `validationStatus` ∈ {Activatable, PendingRuntimeReferences}.
 *
 * No DB / Room involvement; the caller (e.g. `ProgramResourceLoader` in
 * `:data`) handles idempotency and conflict.
 */
public object ProgramResourceParseService {
    public const val MIN_SCHEMA_VERSION: Int = 1
    public const val MAX_SCHEMA_VERSION: Int = 3

    public fun parse(json: String): ParseOutcome {
        val element: JsonElement = try {
            ProgramResourceJson.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Invalid JSON: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Invalid JSON: ${e.message}"))
        }

        val root = element as? JsonObject
            ?: return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Top-level JSON must be an object"))

        val sv = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Missing or invalid schemaVersion"))

        if (sv > MAX_SCHEMA_VERSION) {
            return ParseOutcome.Failed(
                LoaderResult.Failure.UnsupportedSchemaVersion(actual = sv, supportedMax = MAX_SCHEMA_VERSION),
            )
        }
        if (sv < MIN_SCHEMA_VERSION) {
            return ParseOutcome.Failed(
                LoaderResult.Failure.Malformed("schemaVersion $sv is below supported minimum $MIN_SCHEMA_VERSION"),
            )
        }

        val dto: ProgramResource = try {
            ProgramResourceJson.decodeFromJsonElement(ProgramResource.serializer(), element)
        } catch (e: SerializationException) {
            return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Schema-shape decode failed: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return ParseOutcome.Failed(LoaderResult.Failure.Malformed("Schema-shape decode failed: ${e.message}"))
        }

        val declared = dto.metadata.contentHash
        val computed = ProgramResourceContentHash.compute(element)
        if (!declared.equals(computed, ignoreCase = true)) {
            return ParseOutcome.Failed(
                LoaderResult.Failure.HashMismatch(declared = declared, computed = computed),
            )
        }

        when (dto.validationStatus) {
            ValidationStatus.Activatable,
            ValidationStatus.PendingRuntimeReferences,
            -> Unit
            ValidationStatus.Blocked,
            ValidationStatus.Rejected,
            -> {
                val criticalCodes = dto.validationIssues
                    .filter { it.severity == ValidationSeverity.Critical }
                    .map { it.code }
                return ParseOutcome.Failed(
                    LoaderResult.Failure.StatusBlocked(
                        status = dto.validationStatus,
                        criticalIssueCodes = criticalCodes,
                    ),
                )
            }
        }

        return ParseOutcome.Parsed(resource = dto, jsonElement = element)
    }
}

/**
 * Outcome of [ProgramResourceParseService.parse]. Either a parsed-and-
 * verified pair (`Parsed`) or a typed failure that maps 1:1 to a
 * [LoaderResult.Failure] subclass.
 */
public sealed class ParseOutcome {
    @KoverIgnore
    public data class Parsed(
        val resource: ProgramResource,
        val jsonElement: JsonElement,
    ) : ParseOutcome()

    @KoverIgnore
    public data class Failed(val failure: LoaderResult.Failure) : ParseOutcome()
}
