package dev.liftorium.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeSourceTest {
    @Test
    fun `system time source returns monotonically increasing instants`() {
        val source = TimeSource.system()
        val a = source.now()
        val b = source.now()
        assertTrue(!b.isBefore(a), "Expected b ($b) not to precede a ($a)")
    }

    @Test
    fun `fixed time source returns the supplied instant`() {
        val pinned = Instant.parse("2026-05-16T12:00:00Z")
        val source = TimeSource.fixed(pinned)
        assertEquals(pinned, source.now())
        assertEquals(pinned, source.now())
    }
}
