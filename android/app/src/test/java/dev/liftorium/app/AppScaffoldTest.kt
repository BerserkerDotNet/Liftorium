package dev.liftorium.app

import org.junit.Test
import kotlin.test.assertEquals

/**
 * App-module smoke test. Proves the JVM unit-test pipeline runs for `:app`.
 * Replaced by real ViewModel/feature tests as Compose surfaces land.
 */
class AppScaffoldTest {
    @Test
    fun `app scaffold smoke`() {
        assertEquals(4, 2 + 2)
    }
}
