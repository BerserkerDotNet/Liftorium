package dev.liftorium.domain.resource

import kotlinx.serialization.json.Json

/**
 * Canonical android-program-runner JSON configuration for parsing and re-serializing
 * finalized Liftorium program resources. Keep this as a single shared
 * instance: the `:domain` content-hash parity guarantee (in
 * [ProgramResourceContentHash]) operates on the parsed [kotlinx.serialization.json.JsonElement]
 * directly, so this configuration only affects the typed DTO layer.
 *
 * Settings rationale:
 *   - `ignoreUnknownKeys = false`: the schema is closed
 *     (`additionalProperties: false`) and any unknown key on a
 *     finalized resource is evidence of corruption or a forward
 *     schemaVersion the loader does not understand.
 *   - `explicitNulls = false`: optional fields encode as absent keys,
 *     not as `"key": null`, when serializing DTOs back to JSON.
 *   - `prettyPrint = false`: matches the canonical form the importer
 *     emits.
 */
public val ProgramResourceJson: Json = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    prettyPrint = false
    encodeDefaults = true
}
