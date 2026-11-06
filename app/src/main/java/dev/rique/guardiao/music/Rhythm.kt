package dev.rique.guardiao.music

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Music-like bars generated on the phone: the real audio never reaches it (headphones on the PC). */
class Rhythm(private val random: Random = Random(), val bars: Int = 24) {
    private val out = FloatArray(bars)
    private val noise = FloatArray(bars)
    private val kickGain = FloatArray(bars)
    private val hatGain = FloatArray(bars)
    private val lagMs = DoubleArray(bars) { random.nextDouble() * 70 }
    private val fall = FloatArray(bars) { 0.035f + random.nextFloat() * 0.06f }
    private var lastBeat = -1L

    fun frame(tMs: Long, playing: Boolean, track: String): FloatArray {
        val beatMs = 60_000.0 / bpmFor(track)
        val beat = (tMs / beatMs).toLong()
        if (beat != lastBeat) {
            lastBeat = beat
            for (b in 0 until bars) {
                kickGain[b] = 0.45f + random.nextFloat() * 0.55f
                hatGain[b] = 0.1f + random.nextFloat() * 0.9f
            }
        }
        val accent = if (beat % 4 == 0L) 1.0 else 0.85

        for (b in 0 until bars) {
            val t = tMs - lagMs[b]
            val phase = ((t % beatMs) + beatMs) % beatMs / beatMs
            val kick = exp(-phase * 6) * accent
            val hatPhase = (((t + beatMs / 2) % beatMs) + beatMs) % beatMs / beatMs
            val hat = exp(-hatPhase * 10) * 0.6
            noise[b] = noise[b] * 0.4f + random.nextFloat() * 0.6f

            val x = b / (bars - 1).toDouble()
            val low = (1 - x) * (1 - x)
            val high = x * x
            val mid = 1 - abs(2 * x - 1)
            val target = if (!playing) 0.0
            else 1.4 * kick * low * kickGain[b] + hat * high * hatGain[b] + noise[b] * (0.15 + 0.35 * mid) * (0.4 + 0.6 * kick)
            out[b] = max(target.toFloat().coerceIn(0f, 1f), out[b] - fall[b]).coerceAtLeast(0f)
        }
        return out.copyOf()
    }

    companion object {
        fun bpmFor(track: String): Int = 90 + abs(track.hashCode() % 51)
    }
}
