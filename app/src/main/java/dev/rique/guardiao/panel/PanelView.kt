package dev.rique.guardiao.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.rique.guardiao.power.PhoneBattery
import dev.rique.guardiao.server.ServerSnapshot
import dev.rique.guardiao.server.Thresholds
import dev.rique.guardiao.ui.Theme
import dev.rique.guardiao.ui.Themes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class PanelView(context: Context) : View(context) {
    var onThemePicked: ((Theme) -> Unit)? = null
    var theme: Theme = Themes.all[0]
        set(v) {
            field = v
            invalidate()
        }

    private var snapshot: ServerSnapshot? = null
    private var thresholds = Thresholds()
    private var cpuHistory: List<Float> = emptyList()
    var battery: PhoneBattery? = null
        set(v) {
            if (v == field) return
            field = v
            invalidate()
        }
    private var batteryOpen = false
    private var batteryToggleMs = 0L
    private val batteryHit = RectF()
    private val closeBattery = Runnable { setBatteryOpen(false) }
    private val batteryFrame = Runnable { invalidate() }

    private val background = 0xFF07070A.toInt()
    private val dim = 0x66FFFFFF
    private val red = 0xFFFF5A3C.toInt()
    private val amber = 0xFFFFB020.toInt()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x1AFFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; letterSpacing = 0.15f }
    private val name = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; color = dim }

    private val rect = RectF()
    private val path = Path()
    private val dotCenters = ArrayList<Pair<Float, Float>>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    fun show(s: ServerSnapshot?, t: Thresholds, history: List<Float>) {
        snapshot = s
        thresholds = t
        cpuHistory = history
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(20f)
        val leftW = w * 0.44f

        label.textSize = sp(11f)
        canvas.drawText("SERVIDOR", pad, pad + sp(11f), label)
        drawBattery(canvas, pad + label.measureText("SERVIDOR") + dp(16f), pad + sp(11f) / 2, leftW)
        val s = snapshot
        if (s == null) {
            value.textSize = sp(16f)
            value.color = dim
            canvas.drawText("sem contato com o servidor", w / 2, h / 2, value)
            drawThemes(canvas, pad, h - pad)
            return
        }
        s.uptime?.let {
            small.textAlign = Paint.Align.LEFT
            small.textSize = sp(11f)
            canvas.drawText("ligado há $it", pad, pad + sp(28f), small)
            small.textAlign = Paint.Align.RIGHT
        }

        val gauges = Panel.gauges(s, thresholds)
        val gaugeTop = pad + sp(42f)
        val cell = min((leftW - pad) / 2, (h * 0.52f) / 2)
        gauges.forEachIndexed { i, g ->
            val cx = pad + cell * (i % 2) + cell / 2
            val cy = gaugeTop + cell * (i / 2) + cell / 2
            drawGauge(canvas, g, cx, cy, cell * 0.36f)
        }

        val sparkTop = gaugeTop + cell * 2 + dp(6f)
        val sparkBottom = h - pad - dp(40f)
        if (sparkBottom - sparkTop > dp(24f)) drawSpark(canvas, pad, sparkTop, leftW, sparkBottom)
        drawThemes(canvas, pad, h - pad)

        drawContainers(canvas, Panel.rows(s.containers), leftW + dp(24f), pad, w - pad, h - pad)
    }

    private fun drawBattery(canvas: Canvas, left: Float, cy: Float, maxRight: Float) {
        val b = battery ?: return
        val now = SystemClock.uptimeMillis()
        val t = ((now - batteryToggleMs) / BATTERY_ANIM_MS.toFloat()).coerceIn(0f, 1f)
        val open = if (batteryOpen) t else 1f - t
        if (t < 1f) postDelayed(batteryFrame, 16)

        val color = when {
            b.critical || b.hot -> red
            b.low -> amber
            else -> theme.glow
        }
        val bw = dp(22f)
        val bh = dp(11f)
        val top = cy - bh / 2
        ring.color = color
        ring.strokeWidth = dp(1.5f)
        rect.set(left, top, left + bw, top + bh)
        canvas.drawRoundRect(rect, dp(2f), dp(2f), ring)
        fill.color = color
        canvas.drawRect(left + bw, top + bh * 0.3f, left + bw + dp(2.5f), top + bh * 0.7f, fill)
        val inner = bw - dp(4f)
        canvas.drawRect(left + dp(2f), top + dp(2f), left + dp(2f) + inner * (b.percent.coerceIn(5, 100) / 100f), top + bh - dp(2f), fill)
        if (b.charging) {
            small.textAlign = Paint.Align.LEFT
            small.textSize = sp(11f)
            small.color = color
            canvas.drawText("⚡", left + bw + dp(5f), cy + sp(4f), small)
        }

        val textLeft = left + bw + dp(if (b.charging) 20f else 8f)
        batteryHit.set(left - dp(12f), cy - dp(18f), textLeft + dp(12f), cy + dp(18f))
        if (open > 0f) {
            val info = "${b.percent}%  ·  ${b.celsius.roundToInt()} °C  ·  ${if (b.charging) "na tomada" else "na bateria"}"
            small.textAlign = Paint.Align.LEFT
            small.textSize = sp(12f)
            val full = min(small.measureText(info), maxRight - textLeft)
            canvas.save()
            canvas.clipRect(textLeft, cy - dp(14f), textLeft + full * open, cy + dp(14f))
            small.color = if (b.alert) color else theme.core
            small.alpha = (255 * open).toInt()
            canvas.drawText(info, textLeft, cy + small.textSize * 0.35f, small)
            canvas.restore()
            batteryHit.right = textLeft + full * open + dp(12f)
        }
        small.alpha = 0xFF
        small.color = dim
        small.textAlign = Paint.Align.RIGHT
    }

    private fun setBatteryOpen(open: Boolean) {
        if (open == batteryOpen) return
        val now = SystemClock.uptimeMillis()
        val done = ((now - batteryToggleMs) / BATTERY_ANIM_MS.toFloat()).coerceIn(0f, 1f)
        batteryToggleMs = now - ((1f - done) * BATTERY_ANIM_MS).toLong()
        batteryOpen = open
        removeCallbacks(closeBattery)
        if (open) postDelayed(closeBattery, BATTERY_OPEN_MS)
        invalidate()
    }

    private fun drawGauge(canvas: Canvas, g: Gauge, cx: Float, cy: Float, r: Float) {
        val stroke = r * 0.16f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        arc.color = if (g.alert) red else theme.glow
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 135f, 270f, false, track)
        if (g.fraction > 0f) canvas.drawArc(rect, 135f, 270f * g.fraction, false, arc)
        value.color = if (g.alert) red else theme.core
        value.textSize = r * 0.5f
        canvas.drawText(g.text, cx, cy + value.textSize * 0.35f, value)
        label.textSize = sp(10f)
        label.textAlign = Paint.Align.CENTER
        canvas.drawText(g.label, cx, cy + r * 0.95f, label)
        label.textAlign = Paint.Align.LEFT
    }

    private fun drawSpark(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        label.textSize = sp(10f)
        canvas.drawText("CPU · ÚLTIMA HORA", left, top + sp(10f), label)
        val t = top + sp(16f)
        track.strokeWidth = 1f
        canvas.drawLine(left, bottom, right, bottom, track)
        if (cpuHistory.size < 2) return
        val step = (right - left) / (HISTORY_SIZE - 1)
        val start = right - step * (cpuHistory.size - 1)
        path.reset()
        cpuHistory.forEachIndexed { i, v ->
            val x = start + i * step
            val y = bottom - (bottom - t) * (v / 100f).coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        line.color = theme.glow
        line.strokeWidth = dp(1.5f)
        canvas.drawPath(path, line)
        path.lineTo(right, bottom)
        path.lineTo(start, bottom)
        path.close()
        fill.color = (theme.glow and 0x00FFFFFF) or 0x22000000
        canvas.drawPath(path, fill)
    }

    private fun drawThemes(canvas: Canvas, left: Float, baseline: Float) {
        label.textSize = sp(10f)
        canvas.drawText("TEMA", left, baseline - dp(4f), label)
        val r = dp(9f)
        val gap = dp(30f)
        val x0 = left + label.measureText("TEMA") + dp(22f)
        val cy = baseline - dp(8f)
        dotCenters.clear()
        Themes.all.forEachIndexed { i, t ->
            val cx = x0 + i * gap
            dot.color = t.glow
            canvas.drawCircle(cx, cy, r, dot)
            if (t.id == theme.id) {
                ring.color = t.core
                ring.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, r + dp(4f), ring)
            }
            dotCenters.add(cx to cy)
        }
    }

    private fun drawContainers(canvas: Canvas, rows: List<Row>, left: Float, top: Float, right: Float, bottom: Float) {
        val ok = rows.count { it.status == Status.OK }
        label.textSize = sp(11f)
        canvas.drawText("CONTAINERS", left, top + sp(11f), label)
        small.textSize = sp(11f)
        small.color = if (ok == rows.size) dim else red
        canvas.drawText("$ok/${rows.size} ok", right, top + sp(11f), small)

        val listTop = top + sp(24f)
        val colGap = dp(16f)
        val colW = (right - left - colGap) / 2
        val rowH = ((bottom - listTop) / ceil(rows.size / 2.0).toFloat().coerceAtLeast(1f)).coerceIn(sp(14f), sp(30f))
        val capacity = ((bottom - listTop) / rowH).toInt() * 2
        name.textSize = min(sp(14f), rowH * 0.62f)
        small.textSize = name.textSize * 0.85f

        val shown = if (rows.size > capacity) rows.take(capacity - 1) else rows
        val perCol = ceil(shown.size / 2.0).toInt().coerceAtLeast(1)
        shown.forEachIndexed { i, row ->
            val x = left + (i / perCol) * (colW + colGap)
            val y = listTop + (i % perCol) * rowH + rowH / 2
            val color = when (row.status) {
                Status.OK -> theme.core
                Status.UNHEALTHY -> amber
                Status.DOWN -> red
            }
            dot.color = if (row.status == Status.OK) (theme.glow and 0x00FFFFFF) or 0x99000000.toInt() else color
            canvas.drawCircle(x + dp(4f), y, dp(3.5f), dot)
            small.color = dim
            val stat = when (row.status) {
                Status.DOWN -> "fora"
                Status.UNHEALTHY -> "mal"
                Status.OK -> "${"%.1f".format(row.cpuPercent)}%"
            }
            val statW = small.measureText("100.0%")
            canvas.drawText(stat, x + colW, y + small.textSize * 0.35f, small)
            name.color = if (row.status == Status.OK) (theme.core and 0x00FFFFFF) or 0xD9000000.toInt() else color
            val text = TextUtils.ellipsize(row.name, name, colW - statW - dp(18f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, x + dp(14f), y + name.textSize * 0.35f, name)
        }
        if (shown.size < rows.size) {
            small.color = dim
            canvas.drawText("+${rows.size - shown.size}", right, bottom, small)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                if (abs(ev.x - downX) < touchSlop && abs(ev.y - downY) < touchSlop) {
                    if (battery != null && batteryHit.contains(ev.x, ev.y)) {
                        setBatteryOpen(!batteryOpen)
                        return true
                    }
                    val hit = dotCenters.indexOfFirst { (cx, cy) -> hypot(ev.x - cx, ev.y - cy) < dp(18f) }
                    if (hit >= 0) onThemePicked?.invoke(Themes.all[hit])
                }
            }
        }
        return true
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    companion object {
        const val HISTORY_SIZE = 720
        const val BATTERY_ANIM_MS = 220L
        const val BATTERY_OPEN_MS = 8_000L
    }
}
