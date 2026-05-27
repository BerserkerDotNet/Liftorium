package dev.liftorium.domain.common

import dev.liftorium.core.KoverIgnore

/**
 * Sync-ready metadata embedded on every persistence-bearing user-state
 * entity introduced from `android-workout-logging` onward (see
 * `docs/architecture.md` "Sync-readiness contract" and rollout table).
 *
 * MVP has no cloud sync, but every row that records user-visible state
 * MUST carry the full field set so a future sync engine can reconcile
 * without a destructive schema bump:
 *
 *  * [createdAtEpochMillis] / [updatedAtEpochMillis] — wall-clock audit
 *    on every mutation. `updatedAt` equals `createdAt` for the initial
 *    insert and advances on every transactional mutation.
 *  * [deletedAtEpochMillis] — null tombstone marker. Soft-delete only;
 *    rows with `deletedAt != null` are excluded from active queries but
 *    kept for restore/sync history.
 *  * [deviceId] — opaque, client-generated device identity (see
 *    [DeviceIdProvider]). Stable across app launches; not derived from
 *    `Settings.Secure.ANDROID_ID`.
 *  * [localRevision] — monotonically increasing per-entity revision
 *    counter. Starts at 1 on insert and advances by 1 on every
 *    mutation. Used by the future sync layer to detect concurrent
 *    edits without a server round-trip.
 *  * [clientMutationId] — the most-recent [ClientMutationId] that
 *    touched this row. Lets the future sync layer dedupe replayed
 *    mutations.
 *
 * The convention is shared with `:data` via a plain data class so Room
 * can `@Embedded` it directly without a per-entity rebuild.
 */
@KoverIgnore
public data class SyncMetadata(
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val deviceId: DeviceId,
    val localRevision: Long,
    val clientMutationId: ClientMutationId,
) {
    init {
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must be non-negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis ($updatedAtEpochMillis) must be >= createdAtEpochMillis ($createdAtEpochMillis)"
        }
        require(deletedAtEpochMillis == null || deletedAtEpochMillis >= createdAtEpochMillis) {
            "deletedAtEpochMillis must be >= createdAtEpochMillis when present"
        }
        require(localRevision >= 1) { "localRevision must be >= 1 (1 == initial insert)" }
    }

    /**
     * Returns a metadata snapshot for a mutation that touches this row
     * at [nowEpochMillis] with [mutationId]. Increments [localRevision]
     * and updates [updatedAtEpochMillis]. Caller decides whether to
     * also set [deletedAtEpochMillis] (for a tombstone mutation).
     */
    public fun mutated(
        nowEpochMillis: Long,
        mutationId: ClientMutationId,
        deletedAtEpochMillis: Long? = this.deletedAtEpochMillis,
    ): SyncMetadata = copy(
        updatedAtEpochMillis = nowEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
        localRevision = localRevision + 1,
        clientMutationId = mutationId,
    )

    public companion object {
        /**
         * Factory for the metadata stamped on an initial insert.
         * `updatedAt == createdAt`, `deletedAt == null`,
         * `localRevision == 1`.
         */
        public fun forInsert(
            nowEpochMillis: Long,
            deviceId: DeviceId,
            mutationId: ClientMutationId,
        ): SyncMetadata = SyncMetadata(
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            deletedAtEpochMillis = null,
            deviceId = deviceId,
            localRevision = 1L,
            clientMutationId = mutationId,
        )
    }
}

/**
 * Opaque per-install device identity. Generated once on first DB open
 * and persisted in the single-row `device_identity` table; never
 * derived from `Settings.Secure.ANDROID_ID` (privacy + reset-friendly,
 * per the 2026-05-25 ADR).
 */
@JvmInline
public value class DeviceId(public val value: String) {
    init {
        require(value.isNotEmpty()) { "DeviceId must not be empty" }
    }

    override fun toString(): String = value
}

/**
 * Stable, client-generated identifier for one user-visible mutation.
 * Lives on every persisted mutation event (see [SyncMetadata]) and on
 * every row that mutation touched, so a future sync engine can dedupe
 * replayed actions without server coordination.
 */
@JvmInline
public value class ClientMutationId(public val value: String) {
    init {
        require(value.isNotEmpty()) { "ClientMutationId must not be empty" }
    }

    override fun toString(): String = value
}

/**
 * Boundary for resolving the current device's [DeviceId]. Lives in
 * `:domain.common` so use cases can depend on the abstraction; the
 * concrete implementation reads the `device_identity` table in `:data`.
 */
public interface DeviceIdProvider {
    public suspend fun current(): DeviceId
}
