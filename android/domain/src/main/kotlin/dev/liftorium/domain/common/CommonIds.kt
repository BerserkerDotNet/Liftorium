package dev.liftorium.domain.common

import dev.liftorium.domain.EntityId

/**
 * Cross-feature opaque identifiers.
 *
 * `ProgramId` and `ProgramVersionId` are referenced by both
 * `:domain.resource` (the loaded program metadata) and `:domain.run`
 * (the immutable pinning of a program version onto a run). They are
 * placed under `dev.liftorium.domain.common` to keep `resource` and
 * `run` package slices independent — see
 * `DomainArchUnitTest.domain package slices are free of cycles`.
 *
 * `ProgramRunId` is intentionally NOT placed here; it is a
 * run-specific concept and lives next to the run aggregate.
 */

@JvmInline
public value class ProgramId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "ProgramId must not be empty" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class ProgramVersionId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "ProgramVersionId must not be empty" }
    }

    override fun toString(): String = value
}
