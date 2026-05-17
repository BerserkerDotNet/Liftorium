package dev.liftorium.data

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Phase 1 smoke test so `:data:testDebugUnitTest` has something real to run
 * once the Android SDK is installed. Replaced by repository/DAO tests when
 * Phase 4 adds the first Room database.
 */
class DataScaffoldTest {
    @Test
    fun `phase 1 scaffold smoke`() {
        assertEquals(1, DataModuleMarker.PHASE)
    }
}
