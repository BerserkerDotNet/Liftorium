package dev.liftorium.domain.run

import dev.liftorium.domain.EntityId

/**
 * Opaque identifier for a program run. Wraps the raw `String` so that
 * mixing it up with [dev.liftorium.domain.common.ProgramVersionId] or
 * [dev.liftorium.domain.common.ProgramId] at the API boundary becomes
 * a compile-time error.
 *
 * Stored on disk and on the wire as the raw `String`. The Room data
 * layer registers a [androidx.room.TypeConverter] so column types stay
 * `TEXT`; kotlinx.serialization adapters live next to the wire DTOs
 * that need them (none currently — the JSON resource format never
 * carries a `programRunId`).
 *
 * Cross-feature ids (`ProgramId`, `ProgramVersionId`) intentionally
 * live in `dev.liftorium.domain.common` to keep `:domain.run` and
 * `:domain.resource` package slices independent. See
 * `DomainArchUnitTest.domain package slices are free of cycles`.
 */
@JvmInline
public value class ProgramRunId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "ProgramRunId must not be empty" }
    }

    override fun toString(): String = value
}
