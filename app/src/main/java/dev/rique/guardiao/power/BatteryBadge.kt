package dev.rique.guardiao.power

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

class BatteryBadge(context: Context) : View(context) {
    var battery: PhoneBattery? = null
        set(v) {
            if (v == field) return
            field = v
            invalidate()
        }

    private val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 14 * dp
    }
    private val rect = RectF()
    private val blink = Runnable { invalidate() }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        removeCallbacks(blink)
        val b = battery ?: return
        if (!b.alert) return
        if (b.critical || b.hot) postDelayed(blink, 500)
        if ((b.critical || b.hot) && (SystemClock.uptimeMillis() / 500) % 2 == 1L) return

        val color = when {
            b.critical || b.hot -> RED
            b.low -> AMBER
            else -> GREY
        }
        outline.color = color
        fill.color = color
        text.color = color

        val right = width - 14 * dp
        val top = 12 * dp
        val bw = 30 * dp
        val bh = 15 * dp
        rect.set(right - bw, top, right, top + bh)
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, outline)
        canvas.drawRect(right, top + bh * 0.3f, right + 3 * dp, top + bh * 0.7f, fill)
        val inner = bw - 6 * dp
        rect.set(right - bw + 3 * dp, top + 3 * dp, right - bw + 3 * dp + inner * (b.percent.coerceIn(5, 100) / 100f), top + bh - 3 * dp)
        canvas.drawRect(rect, fill)

        val label = if (b.hot) "${b.celsius.roundToInt()}°C  ${b.percent}%" else "${b.percent}%"
        canvas.drawText(label, right - bw - 6 * dp, top + bh - 2 * dp, text)
    }

    private companion object {
        const val RED = 0xFFFF5A3C.toInt()
        const val AMBER = 0xFFFFA62E.toInt()
        const val GREY = 0x80FFFFFF.toInt()
    }
}
