package dev.rique.guardiao.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {
    private fun sine(freq: Double, amp: Double = 12_000.0, n: Int = 512, rate: Int = 22_050) =
        ShortArray(n) { (amp * sin(2 * PI * freq * it / rate)).toInt().toShort() }

    @Test
    fun sine_1khz_peaks_in_right_bar() {
        val s = Spectrum()
        val bars = s.process(sine(1_000.0))
        val top = bars.indices.maxByOrNull { bars[it] }!!
        assertEquals(s.barFor(1_000.0), top)
    }

    @Test
    fun silence_gives_zero_bars() {
        val bars = Spectrum().process(ShortArray(512))
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun falloff_limited() {
        val s = Spectrum()
        val peak = s.process(sine(1_000.0))
        val i = s.barFor(1_000.0)
        val after = s.process(ShortArray(512))
        assertEquals(peak[i] - 0.04f, after[i], 1e-5f)
    }

    @Test
    fun values_in_0_1() {
        val s = Spectrum()
        repeat(5) { k ->
            val bars = s.process(sine(200.0 * (k + 1), amp = 30_000.0))
            assertTrue(bars.all { it in 0f..1f })
        }
    }

    @Test
    fun decorative_bars_when_no_samples() {
        val s = Spectrum()
        val a = s.decorative(0)
        val b = s.decorative(500)
        assertTrue(a.all { it in 0f..0.25f })
        assertTrue(b.all { it in 0f..0.25f })
        assertNotEquals(a.toList(), b.toList())
    }
}
