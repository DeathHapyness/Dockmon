package dev.rique.guardiao.face

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.rique.guardiao.mood.Effect
import dev.rique.guardiao.mood.Expression
import dev.rique.guardiao.mood.MoodState
import dev.rique.guardiao.ui.Theme
import dev.rique.guardiao.ui.Themes
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class FaceView(context: Context) : View(context) {
    private val background = 0xFF07070A.toInt()
    var theme: Theme = Themes.all[0]
        set(v) {
            field = v
            invalidate()
        }
    private val coreScared = 0xFFF4A08F.toInt()
    private val glowScared = 0xFFFF6A50.toInt()
    private val scanRed = 0xFFFF5A3C.toInt()
    private val heartPink = 0xFFE8577F.toInt()
    private val heartGlow = 0xFFFF7AA2.toInt()
    private val sparkleBlue = 0xFFCFE6FF.toInt()
    private val smileViolet = 0xFFB3A6FF.toInt()

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scanRed; strokeWidth = 3f }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
    }

    private val rect = RectF()
    private val lidPath = Path()
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fireworks = Fireworks()
    private val random = Random()
    private val glitch = FloatArray(3)
    private var glitchAtMs = 0L

    private var state = MoodState(Expression.CONTENTE)
    private var fromShape = EyeShapes.of(Expression.CONTENTE)
    private var toShape = fromShape
    private var shapeStartMs = 0L
    private var gazeX = 0f
    private var gazeY = 0f
    private var offX = 0f
    private var offY = 0f
    private var tilt = 0f
    private var effect = Effect.NONE
    private var effectStartMs = 0L
    private var lastFrameMs = 0L
    private var scaredMix = 0f
    private val nextFrame = Runnable { invalidate() }
    private var loveMix = 0f

    var onTap: ((Float, Float) -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_UP -> if (abs(ev.x - downX) < touchSlop && abs(ev.y - downY) < touchSlop) {
                onTap?.invoke(
                    ((ev.x - width / 2f) / (width / 2f)).coerceIn(-1f, 1f),
                    ((ev.y - height / 2f) / (height / 2f)).coerceIn(-1f, 1f),
                )
            }
        }
        return true
    }

    fun update(next: MoodState) {
        if (next == state) return
        val now = SystemClock.uptimeMillis()
        if (next.expression != state.expression) {
            fromShape = currentShape(now)
            toShape = EyeShapes.of(next.expression)
            shapeStartMs = now
        }
        if (next.effect != Effect.NONE && (next.effect != effect || next.effectKey != state.effectKey)) {
            effectStartMs = now
            if (next.effect == Effect.FIREWORKS) fireworks.start(now, width.toFloat(), height.toFloat())
        }
        effect = next.effect
        state = next
        invalidate()
    }

    private fun currentShape(now: Long): EyeShape {
        val t = ((now - shapeStartMs) / TRANSITION_MS.toFloat()).coerceIn(0f, 1f)
        return EyeShapes.transition(fromShape, toShape, t)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 16L else (now - lastFrameMs).coerceAtMost(100L)
        lastFrameMs = now
        val k = 1f - Math.pow(0.02, dt / 1000.0).toFloat()
        val saccade = 1f - Math.pow(0.00002, dt / 1000.0).toFloat()
        gazeX += (state.gazeX - gazeX) * saccade
        gazeY += (state.gazeY - gazeY) * k
        offX += (state.offsetX - offX) * k * 0.5f
        offY += (state.offsetY - offY) * k * 0.5f
        tilt += (state.tilt - tilt) * k * 0.5f

        canvas.drawColor(background)

        val w = width.toFloat()
        val h = height.toFloat()
        val u = min(h, w * 0.56f) * 0.95f
        val scared = state.expression == Expression.ASSUSTADO
        val breathe = sin(now / 4000.0 * 2 * PI).toFloat() * u * 0.008f
        var cx = w / 2f + offX * u * 0.06f + gazeX * u * 0.16f
        var cy = h * 0.46f + offY * u * 0.06f + gazeY * u * 0.07f + breathe

        val effectAge = now - effectStartMs
        when (effect) {
            Effect.SHAKE -> if (effectAge < 600) cx += sin(effectAge / 16.0).toFloat() * u * 0.02f * (1f - effectAge / 600f)
            Effect.BOUNCE -> if (effectAge < 900) cy -= abs(sin(effectAge / 300.0 * PI)).toFloat() * u * 0.07f
            else -> {}
        }

        if (scared && now - glitchAtMs > 90) {
            glitchAtMs = now
            for (i in glitch.indices) glitch[i] = if (random.nextInt(3) == 0) 0f else (random.nextFloat() - 0.5f) * u * 0.06f
        }

        var s = currentShape(now)
        if (state.blink && s.bottomArc < 0.5f) s = s.copy(height = s.height * 0.08f)

        val love = s.heart >= 0.5f
        scaredMix += ((if (scared) 1f else 0f) - scaredMix) * k * 1.5f
        loveMix += ((if (state.expression == Expression.APAIXONADO) 1f else 0f) - loveMix) * k * 1.5f
        eyePaint.color = blend(blend(theme.core, heartPink, loveMix), coreScared, scaredMix)
        glowPaint.color = blend(blend(theme.glow, heartGlow, loveMix), glowScared, scaredMix)
        barPaint.color = theme.glow

        canvas.save()
        canvas.rotate(tilt, cx, cy)
        val ew = s.width * u
        val eh = s.height * u
        val dx = ew / 2f + u * 0.035f
        val near = 1f + PERSPECTIVE * abs(gazeX)
        val far = 1f - PERSPECTIVE * abs(gazeX)
        val leftScale = if (gazeX < 0) near else far
        val rightScale = if (gazeX < 0) far else near
        drawEye(canvas, cx - dx, cy, ew * leftScale, eh * leftScale * leftScale, s, true, scared, u)
        drawEye(canvas, cx + dx, cy, ew * rightScale, eh * rightScale * rightScale, s, false, scared, u)

        barPaint.strokeWidth = u * 0.014f
        val maxHalf = EyeShapes.of(Expression.ASSUSTADO).height * u / 2f
        if (love) {
            drawBlush(canvas, cx - dx, cy + maxHalf * 0.75f, ew)
            drawBlush(canvas, cx + dx, cy + maxHalf * 0.75f, ew)
        } else {
            val browY = cy - maxHalf - s.browLift * u
            val lift = u * 0.035f
            drawBar(canvas, cx - dx, browY - lift * (-gazeX).coerceAtLeast(0f), ew * 0.55f * leftScale, s.browTilt)
            drawBar(canvas, cx + dx, browY - lift * gazeX.coerceAtLeast(0f), ew * 0.55f * rightScale, -s.browTilt)
        }
        val mouthY = cy + maxHalf + u * 0.07f
        if (s.mouthCurve > 0.05f) drawSmile(canvas, cx, mouthY, s.mouthWidth * u, s.mouthCurve, u)
        else drawBar(canvas, cx, mouthY, s.mouthWidth * u, s.mouthTilt)
        canvas.restore()

        if (love) drawLoveExtras(canvas, cx, cy, u, now)

        if (scared) drawScanLines(canvas, w, h, now)

        if (state.caption.isNotEmpty()) canvas.drawText(state.caption, w / 2f, h - captionPaint.textSize * 1.6f, captionPaint)

        if (fireworks.active) {
            fireworks.update(now, dt)
            fireworks.draw(canvas, u * 0.008f)
        }

        val moving = abs(state.gazeX - gazeX) > 0.002f || abs(state.gazeY - gazeY) > 0.002f ||
            abs(state.offsetX - offX) > 0.002f || abs(state.offsetY - offY) > 0.002f || abs(state.tilt - tilt) > 0.05f
        val busy = scared || love || moving || fireworks.active || now - shapeStartMs < TRANSITION_MS + 100 || effectAge < 1000
        removeCallbacks(nextFrame)
        postDelayed(nextFrame, if (busy) 33L else 66L)
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, ew: Float, eh: Float, s: EyeShape, leftEye: Boolean, scared: Boolean, u: Float) {
        if (s.heart >= 0.5f) {
            accentPaint.color = sparkleBlue
            accentPaint.alpha = 0xFF
            drawHeart(canvas, x, y, ew * 0.8f)
            return
        }
        if (s.bottomArc >= 0.5f) {
            rect.set(x - ew * 0.38f, y - eh * 0.4f, x + ew * 0.38f, y + eh * 0.6f)
            arcPaint.color = glowPaint.color
            arcPaint.alpha = 0x40
            arcPaint.strokeWidth = ew * 0.30f
            canvas.drawArc(rect, 200f, 140f, false, arcPaint)
            arcPaint.color = eyePaint.color
            arcPaint.strokeWidth = ew * 0.20f
            canvas.drawArc(rect, 200f, 140f, false, arcPaint)
            return
        }

        if (!scared) {
            drawEyeBody(canvas, x, y, ew, eh)
        } else {
            val band = eh / glitch.size
            val g = ew * 0.12f
            for (i in glitch.indices) {
                val top = if (i == 0) y - eh / 2 - g else y - eh / 2 + band * i
                val bottom = if (i == glitch.size - 1) y + eh / 2 + g else y - eh / 2 + band * (i + 1)
                canvas.save()
                canvas.clipRect(x - ew, top, x + ew, bottom)
                drawEyeBody(canvas, x + glitch[i], y, ew, eh)
                canvas.restore()
            }
            canvas.drawCircle(x - ew * 0.22f + glitch[0], y - eh * 0.12f, u * 0.035f, pupilPaint)
        }

        val outerDrop = (s.topCut + s.outerCut).coerceAtMost(0.95f) * eh
        val innerDrop = (s.topCut + s.innerCut).coerceAtMost(0.95f) * eh
        if (outerDrop <= 0f && innerDrop <= 0f) return
        val m = ew * 0.1f
        val top = y - eh / 2
        val leftDrop = if (leftEye) outerDrop else innerDrop
        val rightDrop = if (leftEye) innerDrop else outerDrop
        lidPath.rewind()
        lidPath.moveTo(x - ew / 2 - m, top - m)
        lidPath.lineTo(x + ew / 2 + m, top - m)
        lidPath.lineTo(x + ew / 2 + m, top + rightDrop)
        lidPath.lineTo(x - ew / 2 - m, top + leftDrop)
        lidPath.close()
        canvas.drawPath(lidPath, lidPaint)
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float) {
        val cell = size / HEART[0].length
        val left = x - size / 2
        val top = y - cell * HEART.size / 2
        for (row in HEART.indices) {
            for (col in HEART[row].indices) {
                val c = HEART[row][col]
                if (c == '.') continue
                val paint = if (c == 'o') accentPaint else eyePaint
                val l = left + col * cell
                val t = top + row * cell
                canvas.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, paint)
            }
        }
    }

    private fun drawBlush(canvas: Canvas, x: Float, y: Float, ew: Float) {
        accentPaint.color = heartGlow
        accentPaint.alpha = 0x38
        rect.set(x - ew * 0.32f, y - ew * 0.09f, x + ew * 0.32f, y + ew * 0.09f)
        canvas.drawOval(rect, accentPaint)
    }

    private fun drawSmile(canvas: Canvas, x: Float, y: Float, width: Float, curve: Float, u: Float) {
        arcPaint.color = smileViolet
        arcPaint.alpha = 0xFF
        arcPaint.strokeWidth = u * 0.016f
        val depth = width * 0.35f * curve
        rect.set(x - width / 2, y - depth, x + width / 2, y + depth)
        canvas.drawArc(rect, 20f, 140f, false, arcPaint)
    }

    private fun drawLoveExtras(canvas: Canvas, cx: Float, cy: Float, u: Float, now: Long) {
        val phase = (now % 2400) / 2400f
        eyePaint.color = heartPink
        eyePaint.alpha = (255 * sin(phase * PI)).toInt()
        accentPaint.alpha = eyePaint.alpha
        drawHeart(canvas, cx, cy - u * 0.30f - phase * u * 0.08f, u * 0.07f)
        eyePaint.alpha = 0xFF
    }

    private fun drawEyeBody(canvas: Canvas, x: Float, y: Float, ew: Float, eh: Float) {
        val r = min(ew, eh) * 0.24f
        for ((grow, alpha) in GLOW) {
            val g = ew * grow
            rect.set(x - ew / 2 - g, y - eh / 2 - g, x + ew / 2 + g, y + eh / 2 + g)
            glowPaint.alpha = alpha
            canvas.drawRoundRect(rect, r + g, r + g, glowPaint)
        }
        rect.set(x - ew / 2, y - eh / 2, x + ew / 2, y + eh / 2)
        canvas.drawRoundRect(rect, r, r, eyePaint)
    }

    private fun drawBar(canvas: Canvas, x: Float, y: Float, length: Float, tiltDeg: Float) {
        canvas.save()
        canvas.rotate(tiltDeg, x, y)
        canvas.drawLine(x - length / 2, y, x + length / 2, y, barPaint)
        canvas.restore()
    }

    private fun drawScanLines(canvas: Canvas, w: Float, h: Float, now: Long) {
        val shift = (now % 1200) / 1200f * h * 0.4f
        for (i in 0 until 3) {
            val y = h * (0.15f + 0.33f * i) + shift - h * 0.2f
            canvas.drawLine(-w * 0.1f, y + h * 0.18f, w * 1.1f, y - h * 0.18f, scanPaint)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * u).toInt() shl shift
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    private companion object {
        const val TRANSITION_MS = 450L
        const val PERSPECTIVE = 0.13f
        val HEART = arrayOf(
            ".XXX...XXX.",
            "XoXXX.XXXXX",
            "XXoXXXXXXXX",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            ".XXXXXXXXX.",
            "..XXXXXXX..",
            "...XXXXX...",
            "....XXX....",
            ".....X.....",
        )
        val GLOW = arrayOf(0.035f to 0x14, 0.015f to 0x40)
    }
}
