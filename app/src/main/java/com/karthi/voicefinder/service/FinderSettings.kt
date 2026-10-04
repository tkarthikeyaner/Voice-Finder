package com.karthi.voicefinder.service

import android.content.Context

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

    companion object {
        const val MIN_SENSITIVITY = 0.7f
        const val MAX_SENSITIVITY = 1.5f
        const val DEFAULT_SENSITIVITY = 1.0f
        private const val KEY_ENABLED = "listening_enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
    }
}
