package dev.rique.guardiao.face

import android.graphics.Canvas
import android.graphics.Paint
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class Fireworks(private val random: Random = Random()) {
    private val max = 200
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val bright = BooleanArray(max)
    private var next = 0

    private val burstTimes = longArrayOf(0, 1000, 2000, 3000, 4200)
    private var startMs = -1L
    private var fired = 0
    private var width = 0f
    private var height = 0f

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val grey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8C8C8.toInt() }

    val active: Boolean get() = startMs >= 0 || life.any { it > 0f }

    fun start(nowMs: Long, w: Float, h: Float) {
        startMs = nowMs
        fired = 0
        width = w
        height = h
    }

    fun update(nowMs: Long, dtMs: Long) {
        if (startMs >= 0) {
            while (fired < burstTimes.size && nowMs - startMs >= burstTimes[fired]) {
                burst(width * (0.2f + random.nextFloat() * 0.6f), height * (0.12f + random.nextFloat() * 0.3f))
                fired++
            }
            if (fired == burstTimes.size) startMs = -1
        }
        val dt = dtMs / 1000f
        for (i in 0 until max) {
            if (life[i] <= 0f) continue
            vy[i] += 320f * dt
            vx[i] *= 0.985f
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            life[i] -= dt / 1.6f
        }
    }

    fun draw(canvas: Canvas, radius: Float) {
        for (i in 0 until max) {
            if (life[i] <= 0f) continue
            val p = if (bright[i]) white else grey
            p.alpha = (255 * life[i].coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(x[i], y[i], radius, p)
        }
    }

    private fun burst(cx: Float, cy: Float) {
        val speed = 260f + random.nextFloat() * 160f
        repeat(40) { k ->
            val i = next
            next = (next + 1) % max
            val a = (k / 40f) * 2f * Math.PI.toFloat() + random.nextFloat() * 0.2f
            val s = speed * (0.6f + random.nextFloat() * 0.4f)
            x[i] = cx
            y[i] = cy
            vx[i] = cos(a) * s
            vy[i] = sin(a) * s
            life[i] = 1f
            bright[i] = random.nextBoolean()
        }
    }
}
