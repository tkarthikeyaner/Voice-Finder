package com.karthi.voicefinder.power

enum class PauseReason { LOW_BATTERY, CHARGING }

data class PowerRules(val pauseOnLowBattery: Boolean, val lowBatteryPercent: Int, val pauseWhileCharging: Boolean)

/** Decides when listening should pause to save battery. Pure logic, so it is unit-tested on the JVM. */
object PowerPolicy {
    /** Resume only this many points above the threshold, so listening doesn't flicker on and off at it. */
    const val RESUME_MARGIN = 3

    /** [levelPercent] < 0 means unknown, which never counts as low. */
    fun pauseReason(levelPercent: Int, pluggedIn: Boolean, rules: PowerRules, current: PauseReason?): PauseReason? {
        if (rules.pauseWhileCharging && pluggedIn) return PauseReason.CHARGING
        // While plugged in the battery is filling up, so a low level is no reason to stay paused.
        if (rules.pauseOnLowBattery && !pluggedIn && levelPercent >= 0) {
            val limit = rules.lowBatteryPercent + if (current == PauseReason.LOW_BATTERY) RESUME_MARGIN else 0
            if (levelPercent <= limit) return PauseReason.LOW_BATTERY
        }
        return null
    }
}
