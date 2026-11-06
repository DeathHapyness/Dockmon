package dev.rique.guardiao.face

import dev.rique.guardiao.mood.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeShapeTest {
    private val a = EyeShape(0.2f, 0.4f, 0f, 0f, 0f, 0f)
    private val b = EyeShape(0.4f, 0.2f, 0.5f, 0.3f, 0.1f, 1f)

    @Test
    fun lerp_endpoints() {
        assertEquals(a, EyeShapes.lerp(a, b, 0f))
        assertEquals(b, EyeShapes.lerp(a, b, 1f))
    }

    @Test
    fun lerp_midpoint() {
        val m = EyeShapes.lerp(a, b, 0.5f)
        assertEquals(0.3f, m.width, 1e-6f)
        assertEquals(0.3f, m.height, 1e-6f)
        assertEquals(0.25f, m.topCut, 1e-6f)
        assertEquals(0.15f, m.innerCut, 1e-6f)
        assertEquals(0.05f, m.outerCut, 1e-6f)
        assertEquals(0.5f, m.bottomArc, 1e-6f)
    }

    @Test
    fun every_expression_has_shape() {
        Expression.values().forEach { e ->
            val s = EyeShapes.of(e)
            assertTrue("$e width", s.width > 0f)
            assertTrue("$e height", s.height > 0f)
        }
    }

    @Test
    fun contente_has_open_eyes_and_comemorando_is_arc() {
        assertEquals(0f, EyeShapes.of(Expression.CONTENTE).bottomArc, 0f)
        assertTrue(EyeShapes.of(Expression.COMEMORANDO).bottomArc > 0.5f)
    }

    @Test
    fun transition_endpoints() {
        val from = EyeShapes.of(Expression.CONTENTE)
        val to = EyeShapes.of(Expression.BRAVO)
        assertEquals(from, EyeShapes.transition(from, to, 0f))
        assertEquals(to, EyeShapes.transition(from, to, 1f))
    }

    @Test
    fun transition_is_eased() {
        val from = EyeShapes.of(Expression.CONTENTE)
        val to = EyeShapes.of(Expression.ASSUSTADO)
        val linear = EyeShapes.lerp(from, to, 0.2f)
        val eased = EyeShapes.transition(from, to, 0.2f)
        assertTrue(eased.width - from.width < linear.width - from.width)
    }

    @Test
    fun transition_across_modes_closes_eyes_midway() {
        val from = EyeShapes.of(Expression.CONTENTE)
        val to = EyeShapes.of(Expression.COMEMORANDO)
        val mid = EyeShapes.transition(from, to, 0.5f)
        assertTrue(mid.height < from.height * 0.2f)
        assertTrue(EyeShapes.transition(from, to, 0.3f).bottomArc < 0.5f)
        assertTrue(EyeShapes.transition(from, to, 0.7f).bottomArc >= 0.5f)
        val love = EyeShapes.transition(from, EyeShapes.of(Expression.APAIXONADO), 0.3f)
        assertTrue(love.heart < 0.5f)
    }

    @Test
    fun assustado_bigger_than_calmo() {
        val big = EyeShapes.of(Expression.ASSUSTADO)
        val calm = EyeShapes.of(Expression.CALMO)
        assertTrue(big.width * big.height > calm.width * calm.height)
    }

    @Test
    fun lerp_brows_and_mouth() {
        val x = a.copy(browLift = 0.02f, browTilt = 0f, mouthWidth = 0.04f, mouthTilt = 0f)
        val y = a.copy(browLift = 0.10f, browTilt = 20f, mouthWidth = 0.08f, mouthTilt = -10f)
        val m = EyeShapes.lerp(x, y, 0.5f)
        assertEquals(0.06f, m.browLift, 1e-6f)
        assertEquals(10f, m.browTilt, 1e-6f)
        assertEquals(0.06f, m.mouthWidth, 1e-6f)
        assertEquals(-5f, m.mouthTilt, 1e-6f)
    }

    @Test
    fun assustado_raises_brows_and_bravo_tilts_inward() {
        assertTrue(EyeShapes.of(Expression.ASSUSTADO).browLift > EyeShapes.of(Expression.CALMO).browLift)
        assertTrue(EyeShapes.of(Expression.BRAVO).browTilt > 0f)
        assertTrue(EyeShapes.of(Expression.PREOCUPADO).browTilt < 0f)
    }

    @Test
    fun lerp_mouth_curve() {
        val m = EyeShapes.lerp(a.copy(mouthCurve = 0f), a.copy(mouthCurve = 1f), 0.25f)
        assertEquals(0.25f, m.mouthCurve, 1e-6f)
    }

    @Test
    fun apaixonado_has_heart_eyes_and_smile() {
        val s = EyeShapes.of(Expression.APAIXONADO)
        assertTrue(s.heart > 0.5f)
        assertTrue(s.mouthCurve > 0f)
        assertEquals(0f, EyeShapes.of(Expression.CONTENTE).heart, 0f)
    }
}
