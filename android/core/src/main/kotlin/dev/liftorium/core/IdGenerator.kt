package dev.liftorium.core

import java.util.UUID

/**
 * Generator for opaque random IDs (program runs, schedule occurrences,
 * runtime reference value rows). Injected so unit tests can supply a
 * deterministic sequence without forcing each test to seed
 * `java.util.UUID` internals.
 *
 * IDs are 36-character UUID strings in canonical form. Persistence
 * layers should treat them as opaque text.
 */
public interface IdGenerator {
    public fun newId(): String

    public companion object {
        public fun random(): IdGenerator = JvmUuidIdGenerator
    }
}

internal object JvmUuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
