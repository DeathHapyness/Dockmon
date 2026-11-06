package dev.rique.guardiao.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class Spectrum(val bars: Int = 24, val fftSize: Int = 512, val sampleRate: Int = 22_050) {
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val window = FloatArray(fftSize) { (0.5 - 0.5 * cos(2 * PI * it / (fftSize - 1))).toFloat() }
    private val edges = DoubleArray(bars + 1) { LOW_HZ * (HIGH_HZ / LOW_HZ).pow(it.toDouble() / bars) }
    private val bandBins: Array<IntRange> = Array(bars) { b -> binsFor(b) }
    private val raw = FloatArray(bars)
    private val out = FloatArray(bars)
    private var peak = FLOOR

    fun barFor(freq: Double): Int = edges.indexOfLast { it <= freq }.coerceIn(0, bars - 1)

    fun process(samples: ShortArray): FloatArray {
        for (i in 0 until fftSize) {
            re[i] = if (i < samples.size) samples[i] * window[i] else 0f
            im[i] = 0f
        }
        fft()
        var loudest = 0f
        for (b in 0 until bars) {
            var m = 0f
            for (k in bandBins[b]) m = max(m, hypot(re[k], im[k]))
            raw[b] = m
            loudest = max(loudest, m)
        }
        peak = max(max(peak * 0.995f, loudest), FLOOR)
        for (b in 0 until bars) {
            val target = sqrt(raw[b] / peak).coerceIn(0f, 1f)
            out[b] = max(target, out[b] - FALL)
        }
        return out.copyOf()
    }

    fun decorative(tMs: Long): FloatArray = FloatArray(bars) { b ->
        val t = tMs / 1000.0
        (0.125 + 0.06 * sin(t * 1.3 + b * 0.45) + 0.06 * sin(t * 2.1 - b * 0.8)).toFloat().coerceIn(0f, 0.25f)
    }

    private fun binsFor(b: Int): IntRange {
        val hzPerBin = sampleRate.toDouble() / fftSize
        val first = (edges[b] / hzPerBin).toInt() + 1
        val last = (edges[b + 1] / hzPerBin).toInt()
        if (first <= last) return first..last.coerceAtMost(fftSize / 2 - 1)
        val center = sqrt(edges[b] * edges[b + 1]) / hzPerBin
        val nearest = center.toInt().coerceIn(1, fftSize / 2 - 1)
        return nearest..nearest
    }

    private fun fft() {
        val n = fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val LOW_HZ = 50.0
        const val HIGH_HZ = 10_000.0
        const val FALL = 0.04f
        // Roughly the FFT magnitude of a quiet (~800 amplitude) tone; keeps mic hiss from filling the bars.
        const val FLOOR = 100_000f
    }
}
