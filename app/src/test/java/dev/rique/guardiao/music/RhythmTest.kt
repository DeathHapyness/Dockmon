package dev.rique.guardiao.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RhythmTest {
    private fun rhythm() = Rhythm(Random(7))

    @Test
    fun bpm_in_range_and_depends_on_track() {
        val a = Rhythm.bpmFor("Not Sorry - Radio Edit")
        val b = Rhythm.bpmFor("Another Song")
        assertTrue(a in 90..140)
        assertTrue(b in 90..140)
        assertNotEquals(a, b)
    }

    @Test
    fun kick_on_beat_raises_low_bars() {
        val r = rhythm()
        val bars = r.frame(0, playing = true, track = "x")
        val low = bars.take(6).average()
        val high = bars.takeLast(6).average()
        assertTrue("low=$low high=$high", low > high)
        assertTrue(low > 0.6)
    }

    @Test
    fun values_in_0_1() {
        val r = rhythm()
        var t = 0L
        while (t < 10_000) {
            assertTrue(r.frame(t, true, "x").all { it in 0f..1f })
            t += 33
        }
    }

    @Test
    fun paused_bars_fall_to_zero() {
        val r = rhythm()
        var t = 0L
        repeat(30) { r.frame(t, true, "x"); t += 33 }
        var bars = FloatArray(0)
        repeat(30) { bars = r.frame(t, false, "x"); t += 33 }
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun bars_move_while_playing() {
        val r = rhythm()
        val a = r.frame(0, true, "x").toList()
        val b = r.frame(250, true, "x").toList()
        assertNotEquals(a, b)
        assertEquals(24, a.size)
    }
}
