package dev.liftorium.app

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Phase 1 smoke test. Proves the JVM unit-test pipeline runs for `:app`.
 * Replaced by real ViewModel/feature tests as Compose surfaces land.
 */
class AppScaffoldTest {
    @Test
    fun `phase 1 scaffold smoke`() {
        assertEquals(4, 2 + 2)
    }
}
