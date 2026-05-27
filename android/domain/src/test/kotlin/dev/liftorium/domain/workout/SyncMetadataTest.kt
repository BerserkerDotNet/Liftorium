package dev.liftorium.domain.workout

import dev.liftorium.domain.common.ClientMutationId
import dev.liftorium.domain.common.DeviceId
import dev.liftorium.domain.common.SyncMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * UT-MUT-001 — local mutation metadata shape.
 *
 * Asserts the [SyncMetadata] convention introduced by
 * `android-workout-logging` (and inherited by every later workstream):
 *  * `forInsert` produces `localRevision == 1`,
 *    `createdAt == updatedAt`, `deletedAt == null`.
 *  * `mutated` advances `localRevision` by 1, updates `updatedAt`,
 *    re-stamps `clientMutationId`, and leaves all other fields alone.
 *  * Tombstone mutations set `deletedAt`.
 *  * Invariants (`updatedAt >= createdAt`, `deletedAt >= createdAt`,
 *    `localRevision >= 1`) reject malformed states at construction
 *    time so no downstream code has to defend against them.
 */
class SyncMetadataTest {

    private val deviceId = DeviceId("dev-1")
    private val mutationA = ClientMutationId("mut-A")
    private val mutationB = ClientMutationId("mut-B")

    @Test
    fun `forInsert produces revision 1 with createdAt equal to updatedAt`() {
        val metadata = SyncMetadata.forInsert(
            nowEpochMillis = 1_000L,
            deviceId = deviceId,
            mutationId = mutationA,
        )
        assertEquals(1_000L, metadata.createdAtEpochMillis)
        assertEquals(1_000L, metadata.updatedAtEpochMillis)
        assertEquals(null, metadata.deletedAtEpochMillis)
        assertEquals(1L, metadata.localRevision)
        assertEquals(mutationA, metadata.clientMutationId)
        assertEquals(deviceId, metadata.deviceId)
    }

    @Test
    fun `mutated bumps revision and updates timestamp and mutation id`() {
        val initial = SyncMetadata.forInsert(1_000L, deviceId, mutationA)
        val bumped = initial.mutated(nowEpochMillis = 1_500L, mutationId = mutationB)
        assertEquals(1_000L, bumped.createdAtEpochMillis, "createdAt is immutable")
        assertEquals(1_500L, bumped.updatedAtEpochMillis)
        assertEquals(null, bumped.deletedAtEpochMillis)
        assertEquals(2L, bumped.localRevision)
        assertEquals(mutationB, bumped.clientMutationId)
        assertEquals(deviceId, bumped.deviceId, "deviceId is immutable for the row's lifetime")
        assertNotEquals(initial.clientMutationId, bumped.clientMutationId)
    }

    @Test
    fun `mutated with deletedAt records tombstone marker`() {
        val initial = SyncMetadata.forInsert(1_000L, deviceId, mutationA)
        val tombstone = initial.mutated(
            nowEpochMillis = 2_000L,
            mutationId = mutationB,
            deletedAtEpochMillis = 2_000L,
        )
        assertEquals(2_000L, tombstone.deletedAtEpochMillis)
        assertEquals(2L, tombstone.localRevision)
        assertEquals(2_000L, tombstone.updatedAtEpochMillis)
    }

    @Test
    fun `successive mutations chain revision monotonically`() {
        var metadata = SyncMetadata.forInsert(1_000L, deviceId, mutationA)
        repeat(5) {
            metadata = metadata.mutated(
                nowEpochMillis = metadata.updatedAtEpochMillis + 100L,
                mutationId = ClientMutationId("mut-${'$'}it"),
            )
        }
        assertEquals(6L, metadata.localRevision)
        assertEquals(1_500L, metadata.updatedAtEpochMillis)
        assertEquals(1_000L, metadata.createdAtEpochMillis)
    }

    @Test
    fun `negative createdAt is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            SyncMetadata(
                createdAtEpochMillis = -1L,
                updatedAtEpochMillis = 0L,
                deletedAtEpochMillis = null,
                deviceId = deviceId,
                localRevision = 1L,
                clientMutationId = mutationA,
            )
        }
    }

    @Test
    fun `updatedAt earlier than createdAt is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SyncMetadata(
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 999L,
                deletedAtEpochMillis = null,
                deviceId = deviceId,
                localRevision = 1L,
                clientMutationId = mutationA,
            )
        }
    }

    @Test
    fun `deletedAt earlier than createdAt is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SyncMetadata(
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 1_000L,
                deletedAtEpochMillis = 999L,
                deviceId = deviceId,
                localRevision = 1L,
                clientMutationId = mutationA,
            )
        }
    }

    @Test
    fun `localRevision below one is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SyncMetadata(
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 1_000L,
                deletedAtEpochMillis = null,
                deviceId = deviceId,
                localRevision = 0L,
                clientMutationId = mutationA,
            )
        }
    }

    @Test
    fun `empty device id and mutation id are rejected at value-class construction`() {
        assertFailsWith<IllegalArgumentException> { DeviceId("") }
        assertFailsWith<IllegalArgumentException> { ClientMutationId("") }
    }
}
