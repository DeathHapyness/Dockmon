package dev.rique.guardiao.face

import dev.rique.guardiao.mood.Expression
import dev.rique.guardiao.mood.Expression.*

/** Sizes are fractions of the view height; tilts are degrees, positive = inner end lower. */
data class EyeShape(
    val width: Float,
    val height: Float,
    val topCut: Float,
    val innerCut: Float,
    val outerCut: Float,
    val bottomArc: Float,
    val browLift: Float = 0.05f,
    val browTilt: Float = 0f,
    val mouthWidth: Float = 0.06f,
    val mouthTilt: Float = 0f,
    val mouthCurve: Float = 0f,
    val heart: Float = 0f,
)

object EyeShapes {
    private val shapes = mapOf(
        CONTENTE to EyeShape(0.34f, 0.36f, 0f, 0f, 0f, 0f, 0.05f, 0f, 0.07f, 0f),
        CALMO to EyeShape(0.34f, 0.30f, 0.35f, 0f, 0f, 0f, 0.04f, 0f, 0.06f, 0f),
        OBSERVANDO to EyeShape(0.34f, 0.34f, 0.10f, 0f, 0f, 0f, 0.05f, 0f, 0.06f, 0f),
        CURIOSO to EyeShape(0.34f, 0.40f, 0f, 0f, 0f, 0f, 0.08f, -8f, 0.05f, 0f),
        ASSUSTADO to EyeShape(0.38f, 0.40f, 0f, 0f, 0f, 0f, 0.09f, -12f, 0.06f, 0f),
        PREOCUPADO to EyeShape(0.32f, 0.34f, 0f, 0f, 0.30f, 0f, 0.06f, -14f, 0.06f, 6f),
        BRAVO to EyeShape(0.34f, 0.32f, 0f, 0.40f, 0f, 0f, 0.03f, 18f, 0.08f, 0f),
        DRAMATICO to EyeShape(0.36f, 0.40f, 0f, 0f, 0.45f, 0f, 0.08f, -20f, 0.10f, -8f),
        COMEMORANDO to EyeShape(0.36f, 0.30f, 0f, 0f, 0f, 1f, 0.08f, 0f, 0.10f, 0f),
        PROCURANDO to EyeShape(0.32f, 0.22f, 0.40f, 0f, 0f, 0f, 0.04f, 6f, 0.05f, 0f),
        SONOLENTO to EyeShape(0.32f, 0.04f, 0f, 0f, 0f, 0f, 0.03f, 0f, 0.04f, 0f),
        APAIXONADO to EyeShape(0.36f, 0.34f, 0f, 0f, 0f, 0f, 0.06f, 0f, 0.12f, 0f, mouthCurve = 1f, heart = 1f),
    )

    fun of(e: Expression): EyeShape = shapes.getValue(e)

    /** Eased morph; when the drawing mode changes (box, arc, heart) the eyes close midway instead of popping. */
    fun transition(from: EyeShape, to: EyeShape, t: Float): EyeShape {
        if (t <= 0f) return from
        if (t >= 1f) return to
        if (mode(from) == mode(to)) return lerp(from, to, smoothstep(t))
        return if (t < 0.5f) lerp(from, from.copy(height = from.height * CLOSED), smoothstep(t * 2f))
        else lerp(to.copy(height = to.height * CLOSED), to, smoothstep((t - 0.5f) * 2f))
    }

    private const val CLOSED = 0.1f

    private fun mode(s: EyeShape) = when {
        s.heart >= 0.5f -> 2
        s.bottomArc >= 0.5f -> 1
        else -> 0
    }

    private fun smoothstep(t: Float) = t * t * (3f - 2f * t)

    fun lerp(a: EyeShape, b: EyeShape, t: Float): EyeShape {
        fun l(x: Float, y: Float) = x + (y - x) * t
        return EyeShape(
            l(a.width, b.width), l(a.height, b.height), l(a.topCut, b.topCut),
            l(a.innerCut, b.innerCut), l(a.outerCut, b.outerCut), l(a.bottomArc, b.bottomArc),
            l(a.browLift, b.browLift), l(a.browTilt, b.browTilt),
            l(a.mouthWidth, b.mouthWidth), l(a.mouthTilt, b.mouthTilt),
            l(a.mouthCurve, b.mouthCurve), l(a.heart, b.heart),
        )
    }
}
