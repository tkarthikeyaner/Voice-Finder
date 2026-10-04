package com.karthi.voicefinder.service

import android.content.Context
import android.content.SharedPreferences
import com.karthi.voicefinder.power.PowerRules

/** Small user preferences that the service, boot receiver and UI share. */
class FinderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("finder_settings", Context.MODE_PRIVATE)

    /** Whether the user wants listening on; used to resume after a reboot or app update. */
    var listeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)).apply()

    /** How many times the response plays before stopping by itself. */
    var repeatCount: Int
        get() = prefs.getInt(KEY_REPEAT, DEFAULT_REPEAT)
        set(value) = prefs.edit().putInt(KEY_REPEAT, value.coerceIn(MIN_REPEAT, MAX_REPEAT)).apply()

    /** Display name of the user's custom response file; null means the bundled clip. */
    var customSoundName: String?
        get() = prefs.getString(KEY_SOUND_NAME, null)
        set(value) = prefs.edit().putString(KEY_SOUND_NAME, value).apply()

    var pauseOnLowBattery: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_LOW, false)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_LOW, value).apply()

    var lowBatteryPercent: Int
        get() = prefs.getInt(KEY_LOW_PERCENT, DEFAULT_LOW_PERCENT)
        set(value) = prefs.edit().putInt(KEY_LOW_PERCENT, value.coerceIn(MIN_LOW_PERCENT, MAX_LOW_PERCENT)).apply()

    var pauseWhileCharging: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_CHARGING, false)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_CHARGING, value).apply()

    val powerRules: PowerRules get() = PowerRules(pauseOnLowBattery, lowBatteryPercent, pauseWhileCharging)

    /** Big text on the "found" screen and notification title. */
    var bannerTitle: String
        get() = prefs.getString(KEY_BANNER_TITLE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BANNER_TITLE
        set(value) = prefs.edit().putString(KEY_BANNER_TITLE, value.take(MAX_TITLE_LENGTH)).apply()

    var bannerMessage: String
        get() = prefs.getString(KEY_BANNER_MESSAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BANNER_MESSAGE
        set(value) = prefs.edit().putString(KEY_BANNER_MESSAGE, value.take(MAX_MESSAGE_LENGTH)).apply()

    /** Index into the banner colour themes. */
    var bannerTheme: Int
        get() = prefs.getInt(KEY_BANNER_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_BANNER_THEME, value).apply()

    fun resetBanner() {
        prefs.edit().remove(KEY_BANNER_TITLE).remove(KEY_BANNER_MESSAGE).remove(KEY_BANNER_THEME).apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_SENSITIVITY = 0.7f
        const val MAX_SENSITIVITY = 1.5f
        const val DEFAULT_SENSITIVITY = 1.0f
        const val MIN_REPEAT = 1
        const val MAX_REPEAT = 10
        const val DEFAULT_REPEAT = 3
        const val MIN_LOW_PERCENT = 10
        const val MAX_LOW_PERCENT = 50
        const val DEFAULT_LOW_PERCENT = 20
        const val MAX_TITLE_LENGTH = 40
        const val MAX_MESSAGE_LENGTH = 140
        const val DEFAULT_BANNER_TITLE = "Here I am!"
        const val DEFAULT_BANNER_MESSAGE = "நீங்க எங்க தூக்கி போட்டீங்களோ அங்கதான்யா இருக்கேன்"
        val POWER_KEYS = setOf("pause_low_battery", "low_battery_percent", "pause_charging")
        private const val KEY_ENABLED = "listening_enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_REPEAT = "repeat_count"
        private const val KEY_SOUND_NAME = "custom_sound_name"
        private const val KEY_PAUSE_LOW = "pause_low_battery"
        private const val KEY_LOW_PERCENT = "low_battery_percent"
        private const val KEY_PAUSE_CHARGING = "pause_charging"
        private const val KEY_BANNER_TITLE = "banner_title"
        private const val KEY_BANNER_MESSAGE = "banner_message"
        private const val KEY_BANNER_THEME = "banner_theme"
    }
}
