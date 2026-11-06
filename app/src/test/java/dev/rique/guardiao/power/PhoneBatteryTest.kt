package dev.rique.guardiao.power

import dev.rique.guardiao.server.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBatteryTest {
    @Test
    fun healthy_battery_has_no_alert() {
        val b = PhoneBattery(80, charging = true, celsius = 32.0)
        assertFalse(b.alert)
        assertEquals(emptyList<Problem>(), b.problems())
    }

    @Test
    fun low_only_when_unplugged() {
        assertTrue(PhoneBattery(20, charging = false, celsius = 30.0).low)
        assertFalse(PhoneBattery(21, charging = false, celsius = 30.0).low)
        assertFalse(PhoneBattery(5, charging = true, celsius = 30.0).low)
    }

    @Test
    fun critical_below_ten() {
        val b = PhoneBattery(9, charging = false, celsius = 30.0)
        assertTrue(b.critical)
        assertEquals(listOf(Problem.BatteryLow(9)), b.problems())
        assertTrue(Problem.BatteryLow(9).severity > Problem.BatteryLow(15).severity)
    }

    @Test
    fun hot_beats_everything() {
        val b = PhoneBattery(8, charging = false, celsius = 46.0)
        assertTrue(b.hot)
        assertEquals(listOf(Problem.PhoneHot(46.0), Problem.BatteryLow(8)), b.problems())
        assertTrue(Problem.PhoneHot(46.0).severity > Problem.TempCritical("cpu", 99.0).severity)
    }
}
