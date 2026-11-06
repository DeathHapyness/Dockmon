package dev.rique.guardiao.mood

import dev.rique.guardiao.server.Problem
import dev.rique.guardiao.server.ServerEvent
import java.util.Random
import kotlin.math.roundToInt

class MoodEngine(
    private val random: Random,
    private val hourOf: (Long) -> Int = DayPeriod::localHour,
) {
    private class Quirk(
        val expression: Expression = Expression.CONTENTE,
        val gazeX: Float = 0f,
        val gazeY: Float = 0f,
        val offsetX: Float = 0f,
        val tilt: Float = 0f,
        val durationMs: Long = 0,
        val glanceSide: Float = 0f,
        val effect: Effect = Effect.NONE,
    )

    private var eventExpression = Expression.CONTENTE
    private var eventEffect = Effect.NONE
    private var eventCaption = ""
    private var eventUntilMs = -1L

    private var touchState: MoodState? = null
    private var touchUntilMs = -1L
    private var touchCount = 0
    private var lastTouchMs = Long.MIN_VALUE / 2

    private var quirk: Quirk? = null
    private var quirkUntilMs = -1L
    private var quirkStartMs = -1L
    private var nextQuirkAtMs = -1L
    private var nextBlinkAtMs = -1L

    fun onEvents(events: List<ServerEvent>, nowMs: Long) {
        val e = events.firstOrNull { it is ServerEvent.Down }
            ?: events.firstOrNull { it is ServerEvent.New }
            ?: events.firstOrNull { it is ServerEvent.Up }
            ?: return
        when (e) {
            is ServerEvent.Down -> setEvent(Expression.ASSUSTADO, Effect.SHAKE, 4_000, plural(e.names, "caiu!", "containers caíram!"), nowMs)
            is ServerEvent.Up -> setEvent(Expression.COMEMORANDO, Effect.BOUNCE, 4_000, plural(e.names, "voltou!", "containers voltaram!"), nowMs)
            is ServerEvent.New -> setEvent(
                Expression.COMEMORANDO, Effect.FIREWORKS, 6_000,
                if (e.names.size == 1) "container novo: ${e.names[0]}" else "${e.names.size} containers novos!", nowMs,
            )
        }
    }

    fun onTouch(x: Float, y: Float, nowMs: Long) {
        if (nowMs - lastTouchMs > TOUCH_WINDOW_MS) touchCount = 0
        touchCount++
        lastTouchMs = nowMs
        val night = DayPeriod.of(hourOf(nowMs)) == DayPeriod.NOITE
        val (expression, effect, caption) = when {
            touchCount >= 5 -> Triple(Expression.DRAMATICO, Effect.SHAKE, "socorro!")
            touchCount >= 3 -> Triple(Expression.BRAVO, Effect.SHAKE, "ei, para de me cutucar!")
            touchCount == 2 -> Triple(Expression.COMEMORANDO, Effect.BOUNCE, "hehe, cócegas!")
            night -> Triple(Expression.SONOLENTO, Effect.NONE, "hm? é de noite...")
            else -> Triple(Expression.CURIOSO, Effect.BOUNCE, "oi!")
        }
        touchState = MoodState(expression, gazeX = x, gazeY = y, effect = effect, caption = caption, effectKey = nowMs)
        touchUntilMs = nowMs + if (touchCount >= 3) 2_200 else 1_500
    }

    fun onBackFromMusic(stayedMs: Long, nowMs: Long) {
        val (expression, caption) = when {
            stayedMs >= 180_000 -> Expression.DRAMATICO to "achei que tinha me esquecido!"
            stayedMs >= 30_000 -> Expression.BRAVO to "hmpf... trocou eu pela música"
            else -> return
        }
        touchState = MoodState(expression, gazeX = -0.9f, gazeY = 0.3f, caption = caption)
        touchUntilMs = nowMs + 4_000
    }

    fun state(problems: List<Problem>, nowMs: Long): MoodState {
        if (nextQuirkAtMs < 0) nextQuirkAtMs = nowMs + quirkGap(nowMs)
        if (nextBlinkAtMs < 0) nextBlinkAtMs = nowMs + 2_500 + random.nextInt(3_500)

        if (nowMs < eventUntilMs) {
            postponeQuirk(nowMs)
            return MoodState(eventExpression, effect = eventEffect, caption = eventCaption, blink = blink(eventExpression, nowMs))
        }

        val touch = touchState
        if (touch != null && nowMs < touchUntilMs) {
            postponeQuirk(nowMs)
            return touch.copy(blink = blink(touch.expression, nowMs))
        }

        val top = problems.firstOrNull()
        if (top != null) {
            postponeQuirk(nowMs)
            val expression = expressionFor(top)
            return MoodState(expression, caption = captionFor(top), blink = blink(expression, nowMs))
        }

        val q = currentQuirk(nowMs)
        val rest = if (DayPeriod.of(hourOf(nowMs)) == DayPeriod.NOITE) Expression.CALMO else Expression.CONTENTE
        val expression = q?.expression ?: rest
        return MoodState(
            expression,
            gazeX = q?.let { gaze(it, nowMs) } ?: 0f,
            gazeY = q?.gazeY ?: 0f,
            offsetX = q?.offsetX ?: 0f,
            tilt = q?.tilt ?: 0f,
            effect = q?.effect ?: Effect.NONE,
            caption = "tudo tranquilo",
            blink = blink(expression, nowMs),
        )
    }

    private fun setEvent(expression: Expression, effect: Effect, durationMs: Long, caption: String, nowMs: Long) {
        eventExpression = expression
        eventEffect = effect
        eventCaption = caption
        eventUntilMs = nowMs + durationMs
    }

    private fun postponeQuirk(nowMs: Long) {
        quirk = null
        if (nextQuirkAtMs <= nowMs) nextQuirkAtMs = nowMs + quirkGap(nowMs)
    }

    private fun currentQuirk(nowMs: Long): Quirk? {
        if (quirk != null && nowMs >= quirkUntilMs) {
            quirk = null
            nextQuirkAtMs = nowMs + quirkGap(nowMs)
        }
        if (quirk == null && nowMs >= nextQuirkAtMs) {
            val q = pickQuirk(nowMs)
            quirk = q
            quirkStartMs = nowMs
            quirkUntilMs = nowMs + q.durationMs
        }
        return quirk
    }

    private fun quirkGap(nowMs: Long): Long = when (DayPeriod.of(hourOf(nowMs))) {
        DayPeriod.MANHA -> 2_000L + random.nextInt(5_000)
        DayPeriod.TARDE -> 4_000L + random.nextInt(10_000)
        DayPeriod.NOITE -> 9_000L + random.nextInt(18_000)
    }

    private fun pickQuirk(nowMs: Long): Quirk {
        val hour = hourOf(nowMs)
        val short = 1_200L + random.nextInt(1_300)
        val side = if (random.nextBoolean()) 1f else -1f
        val look = Quirk(gazeX = 0.9f * side, durationMs = short)
        val glance = Quirk(glanceSide = side, durationMs = GLANCE_MS)
        val up = Quirk(gazeY = -0.7f, durationMs = short)
        val curious = Quirk(Expression.CURIOSO, durationMs = short)
        val watch = Quirk(Expression.OBSERVANDO, gazeX = 0.4f * side, durationMs = short)
        val slide = Quirk(offsetX = (random.nextFloat() - 0.5f) * 0.6f, durationMs = short)
        val tilt = Quirk(tilt = 6f * side, durationMs = short)
        val heart = Quirk(Expression.APAIXONADO, durationMs = 3_000)
        val options = when (DayPeriod.of(hour)) {
            DayPeriod.MANHA -> listOf(
                3 to look, 3 to glance, 2 to up, 3 to curious, 2 to watch, 3 to slide, 3 to tilt, 1 to heart,
                2 to Quirk(effect = Effect.BOUNCE, durationMs = 900),
            )
            DayPeriod.TARDE -> {
                val tired = ((hour - 12) / 5f).coerceIn(0f, 1f)
                listOf(
                    2 to look, 2 to glance, 1 to up, 2 to curious, 2 to watch, 1 to slide, 2 to tilt, 1 to heart,
                    2 to Quirk(Expression.CALMO, durationMs = short),
                    (1 + (tired * 6).roundToInt()) to Quirk(Expression.SONOLENTO, durationMs = short + (tired * 2_500).toLong()),
                )
            }
            DayPeriod.NOITE -> listOf(
                1 to look, 1 to glance, 1 to watch, 1 to tilt, 1 to heart,
                2 to Quirk(Expression.SONOLENTO, durationMs = short),
                1 to Quirk(Expression.CONTENTE, durationMs = short),
            )
        }
        var roll = random.nextInt(options.sumOf { it.first })
        for ((weight, q) in options) {
            if (roll < weight) return q
            roll -= weight
        }
        return options.last().second
    }

    private fun gaze(q: Quirk, nowMs: Long): Float {
        if (q.glanceSide == 0f) return q.gazeX
        val elapsed = nowMs - quirkStartMs
        return when {
            elapsed < GLANCE_MS * 3 / 8 -> 0.95f * q.glanceSide
            elapsed < GLANCE_MS * 6 / 8 -> -0.95f * q.glanceSide
            else -> 0f
        }
    }

    private fun blink(expression: Expression, nowMs: Long): Boolean {
        if (nowMs >= nextBlinkAtMs + BLINK_MS) nextBlinkAtMs = nowMs + 2_500 + random.nextInt(3_500)
        return expression != Expression.SONOLENTO && nowMs >= nextBlinkAtMs && nowMs < nextBlinkAtMs + BLINK_MS
    }

    private fun expressionFor(p: Problem) = when (p) {
        is Problem.TempCritical, is Problem.DiskFull -> Expression.DRAMATICO
        is Problem.ContainersDown, is Problem.Unhealthy -> Expression.PREOCUPADO
        is Problem.TempWarning, is Problem.CpuHigh -> Expression.BRAVO
        Problem.Unreachable -> Expression.PROCURANDO
    }

    private fun captionFor(p: Problem) = when (p) {
        is Problem.TempCritical -> "${p.label} a ${p.celsius.roundToInt()} °C"
        is Problem.TempWarning -> "${p.label} a ${p.celsius.roundToInt()} °C"
        is Problem.DiskFull -> "disco ${p.mount} em ${p.percent.roundToInt()}%"
        is Problem.CpuHigh -> "CPU em ${p.percent.roundToInt()}%"
        is Problem.ContainersDown -> plural(p.names, "fora do ar", "containers fora do ar")
        is Problem.Unhealthy -> plural(p.names, "não está bem", "containers não estão bem")
        Problem.Unreachable -> "sem contato com o servidor"
    }

    private fun plural(names: List<String>, one: String, many: String) =
        if (names.size == 1) "${names[0]} $one" else "${names.size} $many"

    private companion object {
        const val BLINK_MS = 120L
        const val GLANCE_MS = 2_400L
        const val TOUCH_WINDOW_MS = 3_000L
    }
}
