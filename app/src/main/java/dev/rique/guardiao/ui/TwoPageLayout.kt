package dev.rique.guardiao.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class TwoPageLayout(context: Context, private val first: View, private val second: View) : FrameLayout(context) {
    var onPageChanged: ((Int) -> Unit)? = null
    var page = 0
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    init {
        addView(first, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(second, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applyOffset(0f)
    }

    fun showPage(i: Int) {
        val target = i.coerceIn(0, 1)
        val changed = target != page
        page = target
        animateTo()
        if (changed) onPageChanged?.invoke(page)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dx = ev.x - downX
                if (abs(dx) > touchSlop && abs(dx) > abs(ev.y - downY)) dragging = true
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> applyOffset(ev.x - downX)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = ev.x - downX
                val v = velocity?.let { it.computeCurrentVelocity(1000); it.xVelocity } ?: 0f
                velocity?.recycle()
                velocity = null
                dragging = false
                val next = when {
                    dx < -width * 0.25f || v < -minFling -> 1
                    dx > width * 0.25f || v > minFling -> 0
                    else -> page
                }
                showPage(next)
            }
        }
        return true
    }

    private fun applyOffset(drag: Float) {
        val base = -page * width.toFloat()
        val x = (base + drag).coerceIn(-width.toFloat(), 0f)
        first.translationX = x
        second.translationX = x + width
    }

    private fun animateTo() {
        val x = -page * width.toFloat()
        first.animate().translationX(x).setDuration(250).start()
        second.animate().translationX(x + width).setDuration(250).start()
    }
}
