package dev.rique.guardiao.power

import android.content.Intent
import android.os.BatteryManager
import dev.rique.guardiao.server.Problem

data class PhoneBattery(val percent: Int, val charging: Boolean, val celsius: Double) {
    val low get() = !charging && percent <= LOW_PERCENT
    val critical get() = !charging && percent <= CRITICAL_PERCENT
    val hot get() = celsius >= HOT_C
    val alert get() = low || hot

    fun problems(): List<Problem> = buildList {
        if (hot) add(Problem.PhoneHot(celsius))
        if (low) add(Problem.BatteryLow(percent))
    }

    companion object {
        const val LOW_PERCENT = 20
        const val CRITICAL_PERCENT = 10
        const val HOT_C = 45.0
        const val COOL_DOWN_BRIGHTNESS = 0.05f

        fun from(intent: Intent): PhoneBattery? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            return PhoneBattery(level * 100 / scale, plugged, tenths / 10.0)
        }
    }
}
