package dev.rique.guardiao.mood

import java.util.Calendar

enum class DayPeriod {
    MANHA, TARDE, NOITE;

    companion object {
        fun of(hour: Int) = when (hour) {
            in 6..11 -> MANHA
            in 12..17 -> TARDE
            else -> NOITE
        }

        fun localHour(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
    }
}
