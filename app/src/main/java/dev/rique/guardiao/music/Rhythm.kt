package dev.rique.guardiao.music

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Music-like bars generated on the phone: the real audio never reaches it (headphones on the PC). */
class Rhythm(private val random: Random = Random(), val bars: Int = 24) {
    private val out = FloatArray(bars)
    private val noise = FloatArray(bars)

    fun frame(tMs: Long, playing: Boolean, track: String): FloatArray {
        val beatMs = 60_000.0 / bpmFor(track)
        val beat = (tMs / beatMs).toLong()
        val phase = (tMs % beatMs) / beatMs
        val kick = exp(-phase * 6) * if (beat % 4 == 0L) 1.0 else 0.85
        val hatPhase = ((tMs + beatMs / 2) % beatMs) / beatMs
        val hat = exp(-hatPhase * 10) * 0.6

        for (b in 0 until bars) {
            noise[b] = noise[b] * 0.7f + random.nextFloat() * 0.3f
            val x = b / (bars - 1).toDouble()
            val low = (1 - x) * (1 - x)
            val high = x * x
            val mid = 1 - abs(2 * x - 1)
            val target = if (!playing) 0.0
            else kick * low + hat * high + noise[b] * mid * (0.25 + 0.35 * kick)
            out[b] = max(target.toFloat().coerceIn(0f, 1f), out[b] - FALL).coerceAtLeast(0f)
        }
        return out.copyOf()
    }

    companion object {
        private const val FALL = 0.06f

        fun bpmFor(track: String): Int = 90 + abs(track.hashCode() % 51)
    }
}
