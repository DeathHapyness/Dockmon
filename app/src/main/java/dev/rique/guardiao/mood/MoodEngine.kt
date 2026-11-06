package dev.rique.guardiao.mood

import dev.rique.guardiao.server.Problem
import dev.rique.guardiao.server.ServerEvent
import java.util.Random
import kotlin.math.roundToInt

class MoodEngine(private val random: Random) {
    private class Quirk(
        val expression: Expression = Expression.CONTENTE,
        val gazeX: Float = 0f,
        val gazeY: Float = 0f,
        val offsetX: Float = 0f,
        val tilt: Float = 0f,
        val durationMs: Long = 0,
        val glanceSide: Float = 0f,
    )

    private var eventExpression = Expression.CONTENTE
    private var eventEffect = Effect.NONE
    private var eventCaption = ""
    private var eventUntilMs = -1L

    private var quirk: Quirk? = null
    private var quirkUntilMs = -1L
    private var quirkStartMs = -1L
    private var nextQuirkAtMs = -1L
    private var nextBlinkAtMs = -1L
    private var lastProblemMs = Long.MIN_VALUE / 2

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

    fun state(problems: List<Problem>, nowMs: Long): MoodState {
        if (nextQuirkAtMs < 0) nextQuirkAtMs = nowMs + 4_000 + random.nextInt(16_000)
        if (nextBlinkAtMs < 0) nextBlinkAtMs = nowMs + 2_500 + random.nextInt(3_500)

        if (nowMs < eventUntilMs) {
            postponeQuirk(nowMs)
            return MoodState(eventExpression, effect = eventEffect, caption = eventCaption, blink = blink(eventExpression, nowMs))
        }

        val top = problems.firstOrNull()
        if (top != null) {
            lastProblemMs = nowMs
            postponeQuirk(nowMs)
            val expression = expressionFor(top)
            return MoodState(expression, caption = captionFor(top), blink = blink(expression, nowMs))
        }

        val q = currentQuirk(nowMs)
        val expression = q?.expression ?: Expression.CONTENTE
        return MoodState(
            expression,
            gazeX = q?.let { gaze(it, nowMs) } ?: 0f,
            gazeY = q?.gazeY ?: 0f,
            offsetX = q?.offsetX ?: 0f,
            tilt = q?.tilt ?: 0f,
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
        if (nextQuirkAtMs <= nowMs) nextQuirkAtMs = nowMs + 4_000 + random.nextInt(16_000)
    }

    private fun currentQuirk(nowMs: Long): Quirk? {
        if (quirk != null && nowMs >= quirkUntilMs) {
            quirk = null
            nextQuirkAtMs = nowMs + 4_000 + random.nextInt(16_000)
        }
        if (quirk == null && nowMs >= nextQuirkAtMs) {
            val q = pickQuirk(nowMs)
            quirk = q
            quirkStartMs = nowMs
            quirkUntilMs = nowMs + q.durationMs
        }
        return quirk
    }

    private fun pickQuirk(nowMs: Long): Quirk {
        val calm = nowMs - lastProblemMs >= 600_000
        val short = 1_200L + random.nextInt(1_300)
        val side = if (random.nextBoolean()) 1f else -1f
        val options = listOf(
            2 to Quirk(gazeX = 0.9f * side, durationMs = short),
            2 to Quirk(glanceSide = side, durationMs = GLANCE_MS),
            1 to Quirk(gazeY = -0.7f, durationMs = short),
            2 to Quirk(Expression.CURIOSO, durationMs = short),
            2 to Quirk(Expression.OBSERVANDO, gazeX = 0.4f * side, durationMs = short),
            (if (calm) 2 else 1) to Quirk(Expression.SONOLENTO, durationMs = short),
            (if (calm) 2 else 1) to Quirk(Expression.CALMO, durationMs = short),
            2 to Quirk(offsetX = (random.nextFloat() - 0.5f) * 0.6f, durationMs = short),
            2 to Quirk(tilt = 6f * side, durationMs = short),
            1 to Quirk(Expression.APAIXONADO, durationMs = 3_000),
        )
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
    }
}
