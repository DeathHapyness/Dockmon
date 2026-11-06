package dev.rique.guardiao.music

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.text.TextPaint
import dev.rique.guardiao.ui.Theme
import dev.rique.guardiao.ui.Themes
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class MusicView(context: Context) : View(context) {
    var onControl: ((String) -> Unit)? = null
    var bars = FloatArray(24)
        set(v) {
            field = v
            postInvalidateOnAnimation()
        }

    private var result: PlayResult = PlayResult.Nothing
    private var cover: Bitmap? = null

    private val background = 0xFF07070A.toInt()
    private val dp = resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16161B.toInt() }
    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = sp(22f); isFakeBoldText = true }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = sp(16f) }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var theme: Theme = Themes.all[0]
        set(v) {
            field = v
            barPaint.color = (v.glow and 0x00FFFFFF) or 0x40000000
            iconPaint.color = v.core
            invalidate()
        }

    init {
        theme = Themes.all[0]
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }

    private val coverRect = RectF()
    private val shaderMatrix = Matrix()
    private val icon = Path()
    private val buttons = Array(3) { RectF() }

    fun show(r: PlayResult, cover: Bitmap?) {
        result = r
        if (cover !== this.cover) {
            this.cover = cover
            coverPaint.shader = cover?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(background)

        val size = min(h * 0.62f, 300 * dp)
        val left = w * 0.12f
        val top = (h - size) / 2f
        coverRect.set(left, top, left + size, top + size)
        val r = 12 * dp
        val bmp = cover
        if (bmp != null) {
            shaderMatrix.setScale(size / bmp.width, size / bmp.height)
            shaderMatrix.postTranslate(left, top)
            coverPaint.shader?.setLocalMatrix(shaderMatrix)
            canvas.drawRoundRect(coverRect, r, r, coverPaint)
        } else {
            canvas.drawRoundRect(coverRect, r, r, placeholder)
        }

        val textX = coverRect.right + w * 0.06f
        val textW = w - textX - w * 0.06f
        val (title, artist) = texts()
        canvas.drawText(ellipsize(title, titlePaint, textW), textX, top + size * 0.28f, titlePaint)
        canvas.drawText(ellipsize(artist, artistPaint, textW), textX, top + size * 0.28f + sp(28f), artistPaint)

        val playing = (result as? PlayResult.Playing)?.np?.isPlaying == true
        val btn = 64 * dp
        val cy = top + size * 0.72f
        for (i in 0..2) {
            val cx = textX + btn / 2 + i * btn * 1.4f
            buttons[i].set(cx - btn / 2, cy - btn / 2, cx + btn / 2, cy + btn / 2)
            if (i == 1) canvas.drawCircle(cx, cy, btn / 2, ringPaint)
            drawIcon(canvas, i, cx, cy, btn * 0.22f, playing)
        }
        drawBars(canvas, w, h)
    }

    private fun texts(): Pair<String, String> = when (val r = result) {
        is PlayResult.Playing -> r.np.title to r.np.artist
        PlayResult.Nothing -> "nada tocando" to ""
        PlayResult.NoDevice -> "abra o Spotify no PC" to ""
        PlayResult.AuthError -> "reconecte o Spotify nas configurações" to ""
        PlayResult.NetError -> "sem internet" to ""
    }

    private fun ellipsize(s: String, p: TextPaint, width: Float) =
        TextUtils.ellipsize(s, p, width.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()

    private fun drawBars(canvas: Canvas, w: Float, h: Float) {
        val n = bars.size
        val slot = w / n
        val bw = slot * 0.6f
        for (i in 0 until n) {
            val bh = (bars[i].coerceIn(0f, 1f) * h * 0.8f).coerceAtLeast(2 * dp)
            val x = i * slot + (slot - bw) / 2
            canvas.drawRect(x, h - bh, x + bw, h, barPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, i: Int, cx: Float, cy: Float, s: Float, playing: Boolean) {
        icon.rewind()
        when (i) {
            0 -> {
                triangle(cx + s * 0.2f, cy, s, left = true)
                canvas.drawRect(cx - s * 1.05f, cy - s, cx - s * 0.75f, cy + s, iconPaint)
            }
            1 -> if (playing) {
                canvas.drawRect(cx - s * 0.7f, cy - s, cx - s * 0.2f, cy + s, iconPaint)
                canvas.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.7f, cy + s, iconPaint)
            } else {
                triangle(cx + s * 0.25f, cy, s, left = false)
            }
            2 -> {
                triangle(cx - s * 0.2f, cy, s, left = false)
                canvas.drawRect(cx + s * 0.75f, cy - s, cx + s * 1.05f, cy + s, iconPaint)
            }
        }
        canvas.drawPath(icon, iconPaint)
    }

    private fun triangle(cx: Float, cy: Float, s: Float, left: Boolean) {
        val d = if (left) -1f else 1f
        icon.moveTo(cx + d * s * 0.8f, cy)
        icon.lineTo(cx - d * s * 0.8f, cy - s)
        icon.lineTo(cx - d * s * 0.8f, cy + s)
        icon.close()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (e.actionMasked != MotionEvent.ACTION_UP) return false
        val i = buttons.indexOfFirst { it.contains(e.x, e.y) }
        when (i) {
            0 -> onControl?.invoke("previous")
            1 -> onControl?.invoke(if ((result as? PlayResult.Playing)?.np?.isPlaying == true) "pause" else "play")
            2 -> onControl?.invoke("next")
        }
        return i >= 0
    }
}
