package dev.rique.guardiao.mood

import dev.rique.guardiao.server.Problem
import dev.rique.guardiao.server.ServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MoodEngineTest {
    private fun engine(hour: Int = 9) = MoodEngine(Random(42)) { hour }

    @Test
    fun idle_is_contente_with_caption() {
        val s = engine().state(emptyList(), 0)
        assertEquals(Expression.CONTENTE, s.expression)
        assertEquals("tudo tranquilo", s.caption)
    }

    @Test
    fun down_event_scares_for_4s() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.Down(listOf("nginx"))), 1_000)
        val down = listOf(Problem.ContainersDown(listOf("nginx")))
        val scared = e.state(down, 4_999)
        assertEquals(Expression.ASSUSTADO, scared.expression)
        assertEquals(Effect.SHAKE, scared.effect)
        assertEquals("nginx caiu!", scared.caption)
        val after = e.state(down, 5_001)
        assertEquals(Expression.PREOCUPADO, after.expression)
        assertEquals("nginx fora do ar", after.caption)
    }

    @Test
    fun many_downs_single_scare_with_count() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.Down(listOf("a", "b", "c", "d", "e"))), 0)
        assertEquals("5 containers caíram!", e.state(emptyList(), 100).caption)
    }

    @Test
    fun up_event_celebrates_4s() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.Up(listOf("nginx"))), 0)
        val s = e.state(emptyList(), 3_999)
        assertEquals(Expression.COMEMORANDO, s.expression)
        assertEquals(Effect.BOUNCE, s.effect)
        assertEquals("nginx voltou!", s.caption)
        assertEquals(Effect.NONE, e.state(emptyList(), 4_001).effect)
    }

    @Test
    fun new_event_fireworks_6s() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.New(listOf("redis", "pg"))), 0)
        val s = e.state(emptyList(), 5_999)
        assertEquals(Expression.COMEMORANDO, s.expression)
        assertEquals(Effect.FIREWORKS, s.effect)
        assertEquals("2 containers novos!", s.caption)
        assertEquals(Effect.NONE, e.state(emptyList(), 6_001).effect)
    }

    @Test
    fun down_beats_new_in_same_batch() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.New(listOf("y")), ServerEvent.Down(listOf("x"))), 0)
        assertEquals(Expression.ASSUSTADO, e.state(emptyList(), 10).expression)
    }

    @Test
    fun problem_maps_to_expression() {
        val cases = mapOf(
            Problem.TempCritical("Core 0", 95.4) to (Expression.DRAMATICO to "Core 0 a 95 °C"),
            Problem.DiskFull("/media", 92.6) to (Expression.DRAMATICO to "disco /media em 93%"),
            Problem.ContainersDown(listOf("a", "b")) to (Expression.PREOCUPADO to "2 containers fora do ar"),
            Problem.Unhealthy(listOf("immich")) to (Expression.PREOCUPADO to "immich não está bem"),
            Problem.Unhealthy(listOf("a", "b", "c")) to (Expression.PREOCUPADO to "3 containers não estão bem"),
            Problem.TempWarning("Core 1", 76.0) to (Expression.BRAVO to "Core 1 a 76 °C"),
            Problem.CpuHigh(93.2) to (Expression.BRAVO to "CPU em 93%"),
            Problem.Unreachable to (Expression.PROCURANDO to "sem contato com o servidor"),
        )
        for ((problem, expected) in cases) {
            val s = engine().state(listOf(problem), 0)
            assertEquals(problem.toString(), expected.first, s.expression)
            assertEquals(problem.toString(), expected.second, s.caption)
        }
    }

    @Test
    fun no_quirks_while_problem() {
        val e = engine()
        val p = listOf(Problem.CpuHigh(90.0))
        var t = 0L
        while (t <= 60_000) {
            val s = e.state(p, t)
            assertEquals(Expression.BRAVO, s.expression)
            assertEquals(0f, s.gazeX, 0f)
            assertEquals(0f, s.tilt, 0f)
            t += 100
        }
    }

    @Test
    fun idle_quirk_happens_within_20s() {
        val e = engine()
        val base = e.state(emptyList(), 0)
        var changed = false
        var t = 0L
        while (t <= 20_000) {
            val s = e.state(emptyList(), t)
            if (s.copy(blink = false) != base.copy(blink = false)) changed = true
            t += 100
        }
        assertTrue(changed)
    }

    @Test
    fun heart_quirk_happens_eventually() {
        val e = engine()
        var seen = false
        var t = 0L
        while (t <= 3_600_000 && !seen) {
            if (e.state(emptyList(), t).expression == Expression.APAIXONADO) seen = true
            t += 100
        }
        assertTrue(seen)
    }

    @Test
    fun blink_occurs() {
        val e = engine()
        var blinked = false
        var t = 0L
        while (t <= 10_000) {
            if (e.state(emptyList(), t).blink) blinked = true
            t += 100
        }
        assertTrue(blinked)
    }

    @Test
    fun glance_looks_both_ways() {
        val e = engine()
        var t = 0L
        var lastSideAt = -1L
        var lastSide = 0f
        var found = false
        while (t <= 1_800_000 && !found) {
            val g = e.state(emptyList(), t).gazeX
            if (g >= 0.8f || g <= -0.8f) {
                val side = if (g > 0) 1f else -1f
                if (lastSide != 0f && side != lastSide && t - lastSideAt <= 1_500) found = true
                lastSide = side
                lastSideAt = t
            }
            t += 100
        }
        assertTrue(found)
    }

    private fun idleRun(hour: Int, ms: Long = 3_600_000): List<MoodState> {
        val e = engine(hour)
        val out = ArrayList<MoodState>()
        var t = 0L
        while (t <= ms) {
            out.add(e.state(emptyList(), t))
            t += 100
        }
        return out
    }

    private fun quirkCount(states: List<MoodState>) =
        states.zipWithNext().count { (a, b) -> a.copy(blink = false) != b.copy(blink = false) }

    private fun sleepyShare(states: List<MoodState>) =
        states.count { it.expression == Expression.SONOLENTO }.toDouble() / states.size

    @Test
    fun morning_is_more_agitated_than_night() {
        assertTrue(quirkCount(idleRun(9)) > 2 * quirkCount(idleRun(22)))
    }

    @Test
    fun morning_never_sleepy() {
        assertEquals(0.0, sleepyShare(idleRun(9)), 0.0)
    }

    @Test
    fun afternoon_gets_more_tired_as_it_goes() {
        val early = sleepyShare(idleRun(13))
        val late = sleepyShare(idleRun(17))
        assertTrue("early=$early late=$late", early > 0.0)
        assertTrue("early=$early late=$late", late > early)
    }

    @Test
    fun night_rests_calm() {
        val s = engine(22).state(emptyList(), 0)
        assertEquals(Expression.CALMO, s.expression)
        assertEquals("tudo tranquilo", s.caption)
        assertTrue(idleRun(22).count { it.expression == Expression.CALMO } > idleRun(22).size / 2)
    }

    @Test
    fun morning_hops_sometimes() {
        assertTrue(idleRun(9).any { it.effect == Effect.BOUNCE })
    }

    @Test
    fun day_periods_by_hour() {
        assertEquals(DayPeriod.NOITE, DayPeriod.of(5))
        assertEquals(DayPeriod.MANHA, DayPeriod.of(6))
        assertEquals(DayPeriod.MANHA, DayPeriod.of(11))
        assertEquals(DayPeriod.TARDE, DayPeriod.of(12))
        assertEquals(DayPeriod.TARDE, DayPeriod.of(17))
        assertEquals(DayPeriod.NOITE, DayPeriod.of(18))
        assertEquals(DayPeriod.NOITE, DayPeriod.of(0))
    }

    private fun taps(e: MoodEngine, n: Int, start: Long = 1_000, gap: Long = 300): Long {
        var t = start
        repeat(n) {
            e.onTouch(0.8f, -0.5f, t)
            t += gap
        }
        return t - gap
    }

    @Test
    fun tap_looks_at_finger_and_hops() {
        val e = engine()
        e.state(emptyList(), 0)
        e.onTouch(0.8f, -0.5f, 1_000)
        val s = e.state(emptyList(), 1_100)
        assertEquals(Expression.CURIOSO, s.expression)
        assertEquals(0.8f, s.gazeX, 0.001f)
        assertEquals(-0.5f, s.gazeY, 0.001f)
        assertEquals(Effect.BOUNCE, s.effect)
        assertEquals("oi!", s.caption)
    }

    @Test
    fun tap_reaction_wears_off() {
        val e = engine()
        e.onTouch(0.8f, -0.5f, 1_000)
        val s = e.state(emptyList(), 2_700)
        assertEquals(Expression.CONTENTE, s.expression)
        assertEquals("tudo tranquilo", s.caption)
    }

    @Test
    fun second_tap_tickles() {
        val e = engine()
        val t = taps(e, 2)
        val s = e.state(emptyList(), t + 100)
        assertEquals(Expression.COMEMORANDO, s.expression)
        assertEquals("hehe, cócegas!", s.caption)
    }

    @Test
    fun poking_makes_him_angry_then_dramatic() {
        val e = engine()
        val t3 = taps(e, 3)
        val angry = e.state(emptyList(), t3 + 100)
        assertEquals(Expression.BRAVO, angry.expression)
        assertEquals(Effect.SHAKE, angry.effect)
        assertEquals("ei, para de me cutucar!", angry.caption)
        val t5 = taps(e, 2, t3 + 300)
        val drama = e.state(emptyList(), t5 + 100)
        assertEquals(Expression.DRAMATICO, drama.expression)
        assertEquals("socorro!", drama.caption)
    }

    @Test
    fun each_tap_restarts_its_effect() {
        val e = engine()
        val t3 = taps(e, 3)
        val a = e.state(emptyList(), t3 + 100)
        e.onTouch(0f, 0f, t3 + 300)
        val b = e.state(emptyList(), t3 + 400)
        assertTrue(a.effectKey != b.effectKey)
    }

    @Test
    fun taps_count_resets_after_quiet() {
        val e = engine()
        val t = taps(e, 3)
        e.onTouch(0f, 0f, t + 4_000)
        assertEquals("oi!", e.state(emptyList(), t + 4_100).caption)
    }

    @Test
    fun night_tap_is_sleepy() {
        val e = engine(22)
        e.onTouch(-0.6f, 0.2f, 1_000)
        val s = e.state(emptyList(), 1_100)
        assertEquals(Expression.SONOLENTO, s.expression)
        assertEquals(-0.6f, s.gazeX, 0.001f)
        assertEquals("hm? é de noite...", s.caption)
    }

    @Test
    fun server_event_beats_tap_but_tap_beats_problem() {
        val e = engine()
        e.onEvents(listOf(ServerEvent.Down(listOf("nginx"))), 1_000)
        e.onTouch(0f, 0f, 1_200)
        assertEquals(Expression.ASSUSTADO, e.state(emptyList(), 1_300).expression)

        val f = engine()
        f.onTouch(0f, 0f, 1_000)
        assertEquals(Expression.CURIOSO, f.state(listOf(Problem.CpuHigh(90.0)), 1_100).expression)
    }

    @Test
    fun short_music_visit_is_fine() {
        val e = engine()
        e.onBackFromMusic(10_000, 1_000)
        assertEquals("tudo tranquilo", e.state(emptyList(), 1_100).caption)
    }

    @Test
    fun long_music_visit_makes_him_jealous() {
        val e = engine()
        e.onBackFromMusic(40_000, 1_000)
        val s = e.state(emptyList(), 1_100)
        assertEquals(Expression.BRAVO, s.expression)
        assertEquals("hmpf... trocou eu pela música", s.caption)
        assertTrue(s.gazeX < -0.5f)
        assertEquals("tudo tranquilo", e.state(emptyList(), 5_200).caption)
    }

    @Test
    fun very_long_music_visit_is_dramatic() {
        val e = engine()
        e.onBackFromMusic(200_000, 1_000)
        val s = e.state(emptyList(), 1_100)
        assertEquals(Expression.DRAMATICO, s.expression)
        assertEquals("achei que tinha me esquecido!", s.caption)
    }

    @Test
    fun tap_cheers_up_jealous() {
        val e = engine()
        e.onBackFromMusic(40_000, 1_000)
        e.onTouch(0f, 0f, 2_000)
        assertEquals("oi!", e.state(emptyList(), 2_100).caption)
    }
}
