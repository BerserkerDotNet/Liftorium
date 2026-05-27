package dev.liftorium.domain.weight

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MRoundTest {

    @Test
    fun `bbb back squat 65 percent of 315 rounds to 205 lb at 5 lb increment`() {
        assertEquals(205.0, mround(315.0 * 0.65, 5.0), 1e-9)
    }

    @Test
    fun `bbb 75 percent rounds to 235 lb`() {
        assertEquals(235.0, mround(315.0 * 0.75, 5.0), 1e-9)
    }

    @Test
    fun `bbb 85 percent rounds to 270 lb`() {
        // 315 * 0.85 = 267.75 -> nearest 5 = 270
        assertEquals(270.0, mround(315.0 * 0.85, 5.0), 1e-9)
    }

    @Test
    fun `bbb back-off 50 percent rounds to 160 lb`() {
        // 315 * 0.50 = 157.5 -> ties go away from zero (up) = 160
        assertEquals(160.0, mround(315.0 * 0.50, 5.0), 1e-9)
    }

    @Test
    fun `kg increment of 2 point 5 rounds half up`() {
        assertEquals(67.5, mround(67.5, 2.5), 1e-9)
        assertEquals(60.0, mround(61.0, 2.5), 1e-9)
        assertEquals(62.5, mround(62.5, 2.5), 1e-9)
        assertEquals(65.0, mround(63.75, 2.5), 1e-9)
    }

    @Test
    fun `tie at lb increment rounds up`() {
        assertEquals(135.0, mround(132.5, 5.0), 1e-9)
    }

    @Test
    fun `zero value returns zero regardless of multiple`() {
        assertEquals(0.0, mround(0.0, 5.0), 1e-9)
        assertEquals(0.0, mround(0.0, 2.5), 1e-9)
    }

    @Test
    fun `negative value rounds away from zero on ties`() {
        assertEquals(-5.0, mround(-2.5, 5.0), 1e-9)
        assertEquals(0.0, mround(-1.0, 5.0), 1e-9)
    }

    @Test
    fun `zero multiple is rejected`() {
        assertFailsWith<IllegalArgumentException> { mround(100.0, 0.0) }
    }

    @Test
    fun `negative multiple is rejected`() {
        assertFailsWith<IllegalArgumentException> { mround(100.0, -5.0) }
    }

    @Test
    fun `NaN value is rejected`() {
        assertFailsWith<IllegalArgumentException> { mround(Double.NaN, 5.0) }
    }
}
