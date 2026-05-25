package dev.liftorium.domain.resource

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * Recomputes the SHA-256 content hash for a import-workflow finalized program
 * resource. Mirrors `schema/hash.ts` (TypeScript) so the Android loader
 * and the import-time validator always agree on the digest of identical
 * content.
 *
 * The hash deliberately excludes:
 *   - `validationStatus`
 *   - `validationIssues`
 *   - `importAudit`
 *   - `metadata.contentHash` itself
 *
 * so re-validating, flipping status, persisting a different audit, or
 * injecting runtime references never invalidates the hash. See the
 * android-program-runner ADR ("Runtime validation strategy = recheck_only").
 *
 * This function operates on `JsonElement` rather than the typed
 * `ProgramResource` Kotlin DTOs on purpose: Kotlin nullable fields can
 * serialize as `"key": null`, which would change the canonical form
 * relative to the JavaScript reference (`schema/hash.ts` only filters
 * `undefined`, not `null`). Working on the parsed `JsonElement` keeps
 * the canonical form bit-identical to the TypeScript source of truth.
 */
public object ProgramResourceContentHash {

    private val orderedContentKeys: List<String> = listOf(
        "schemaVersion",
        "metadata",
        "programDefaults",
        "exerciseCatalog",
        "requiredReferences",
        "programStructure",
        "progressionRules",
    )

    /**
     * Build the canonical hashing view of a parsed program resource.
     *
     * Returns the same value untouched when the input is not a JSON
     * object (the recursive canonical stringifier still handles those
     * shapes correctly).
     */
    public fun canonicalize(resource: JsonElement): JsonElement {
        if (resource !is JsonObject) return resource
        val view = LinkedHashMap<String, JsonElement>()
        for (key in orderedContentKeys) {
            val value = resource[key] ?: continue
            if (key == "metadata" && value is JsonObject) {
                view[key] = JsonObject(value.filterKeys { it != "contentHash" })
            } else {
                view[key] = value
            }
        }
        return JsonObject(view)
    }

    /**
     * Deterministically serialize a `JsonElement` with sorted object
     * keys. Output is byte-identical to the TypeScript
     * `canonicalJsonStringify` in `schema/hash.ts`.
     */
    public fun canonicalStringify(value: JsonElement): String = when (value) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (value.isString) {
            primitiveJson.encodeToString(JsonPrimitive.serializer(), value)
        } else {
            value.content
        }
        is JsonArray -> value.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
            transform = ::canonicalStringify,
        )
        is JsonObject -> value.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
                primitiveJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(k)) +
                    ":" +
                    canonicalStringify(v)
            }
    }

    /**
     * Compute the SHA-256 content hash. Returns a lowercase hex digest
     * matching `schema/hash.ts:computeProgramResourceContentHash`.
     */
    public fun compute(resource: JsonElement): String {
        val canonical = canonicalStringify(canonicalize(resource))
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xff
                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0f])
            }
        }
    }

    private val primitiveJson: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json.Default

    private val HEX_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
