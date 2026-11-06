package dev.rique.guardiao.mood

enum class Expression {
    CONTENTE, CALMO, OBSERVANDO, CURIOSO, ASSUSTADO, PREOCUPADO,
    BRAVO, DRAMATICO, COMEMORANDO, PROCURANDO, SONOLENTO, APAIXONADO
}

enum class Effect { NONE, FIREWORKS, SHAKE, BOUNCE }

data class MoodState(
    val expression: Expression,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val blink: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val tilt: Float = 0f,
    val effect: Effect = Effect.NONE,
    val caption: String = "",
    val effectKey: Long = 0,
)
