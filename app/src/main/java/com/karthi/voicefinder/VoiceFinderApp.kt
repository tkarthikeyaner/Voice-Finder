package com.karthi.voicefinder

import android.app.Application
import com.karthi.voicefinder.service.Notifications

class VoiceFinderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
