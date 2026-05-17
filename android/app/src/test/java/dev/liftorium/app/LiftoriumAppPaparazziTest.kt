package dev.liftorium.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi (LayoutLib-based) snapshot of the Phase 1 scaffold.
 *
 * Runs via `:app:recordPaparazziDebug` (generates fresh PNGs). PNGs land
 * under `app/src/test/snapshots/` (gitignored). Visual review by the agent
 * is required for any UI deliverable — see
 * `.github/skills/android-implementation/SKILL.md`.
 *
 * Fidelity: LayoutLib renders are close to but not identical to device
 * rendering. Real-device evidence is still required for runtime-critical
 * UI (timer, lifecycle, process death) per docs/testing-strategy.md.
 */
class LiftoriumAppPaparazziTest {
    @get:Rule
    val paparazzi: Paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
    )

    @Test
    fun liftoriumApp_initial() {
        paparazzi.snapshot {
            LiftoriumApp()
        }
    }
}
