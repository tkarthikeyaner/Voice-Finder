package com.karthi.voicefinder.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerPolicyTest {
    private val both = PowerRules(pauseOnLowBattery = true, lowBatteryPercent = 20, pauseWhileCharging = true)
    private val none = PowerRules(pauseOnLowBattery = false, lowBatteryPercent = 20, pauseWhileCharging = false)

    @Test
    fun pausesAtOrBelowThreshold() {
        assertEquals(PauseReason.LOW_BATTERY, PowerPolicy.pauseReason(20, false, both, null))
        assertNull(PowerPolicy.pauseReason(21, false, both, null))
    }

    @Test
    fun resumesOnlyAboveThresholdPlusMargin() {
        assertEquals(PauseReason.LOW_BATTERY, PowerPolicy.pauseReason(23, false, both, PauseReason.LOW_BATTERY))
        assertNull(PowerPolicy.pauseReason(24, false, both, PauseReason.LOW_BATTERY))
    }

    @Test
    fun chargingPausesOnlyWhenEnabled() {
        assertEquals(PauseReason.CHARGING, PowerPolicy.pauseReason(80, true, both, null))
        assertNull(PowerPolicy.pauseReason(80, true, both.copy(pauseWhileCharging = false), null))
    }

    @Test
    fun lowBatteryWhilePluggedInDoesNotPause() {
        assertNull(PowerPolicy.pauseReason(5, true, both.copy(pauseWhileCharging = false), PauseReason.LOW_BATTERY))
    }

    @Test
    fun disabledRulesNeverPauseAndUnknownLevelIsNotLow() {
        assertNull(PowerPolicy.pauseReason(1, true, none, null))
        assertNull(PowerPolicy.pauseReason(-1, false, both, null))
    }
}
