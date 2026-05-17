package dev.liftorium.domain

/**
 * Stable, opaque identifier base for domain entities.
 *
 * Phase 1 ships only the abstraction; concrete ID value classes (program ID,
 * program version ID, workout session ID, etc.) live in the workstreams that
 * own those entities.
 */
public interface EntityId {
    public val value: String
}
