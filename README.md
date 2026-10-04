# Voice Finder (Android)

Lost your phone somewhere nearby? Say **"ஏய் எங்க இருக்க?"** in your own voice and the phone answers at full volume — even on silent, vibrate or Do Not Disturb — with *"நீங்க எங்க தூக்கி போட்டீங்களோ அங்கதான்யா இருக்கேன்"* (`res/raw/respond.mp3`).

Native Kotlin · Jetpack Compose · Material 3 · coroutines. Min Android 8.0 (API 26), target API 35. Fully offline: no cloud, no third-party SDK, no API keys.

## How it works

```
Mic (16 kHz PCM, 10 ms frames)
  └─► SpeechSegmenter      energy VAD with adaptive noise floor → 0.4–3.5 s speech segments only
        └─► FeatureExtractor  MFCC (25 ms / 10 ms, 26 mel, 12 coeffs) + cepstral mean normalisation
              └─► WakePhraseMatcher
                    ├─ phrase check: DTW vs. your enrolled recordings  (what was said)
                    └─ voice check:  MFCC mean/spread voiceprint distance (who said it)
                          └─► both ≤ calibrated threshold → AlertPlayer
                                 override silent/DND → max STREAM_MUSIC + STREAM_ALARM → play MP3 ×3 → restore everything
```

**Why template matching instead of Porcupine/Vosk/TFLite:** none of them ships a Tamil keyword model, and Porcupine needs an access key plus a paid custom-word build. Speaker-dependent DTW template matching is the classic technique for a personal wake phrase in any language: you enroll by saying the phrase 3–6 times, and the thresholds are calibrated from how much *your own* recordings differ. Because the templates are your voice, other speakers score poorly on the phrase check too, and the voiceprint gate adds a second, independent check.

Battery: the always-on part is just an RMS level per 10 ms frame. MFCC + DTW only run on short segments that look like speech (a few ms of CPU each).

## Folder structure

```
Voice-Finder/
├── app/src/main/java/com/karthi/voicefinder/
│   ├── VoiceFinderApp.kt              # Creates notification channels
│   ├── MainActivity.kt                # Permissions, settings intents, Compose host
│   ├── audio/
│   │   ├── MicrophoneSource.kt        # AudioRecord → Flow<ShortArray> (VOICE_RECOGNITION source)
│   │   └── AlertPlayer.kt             # Silent/DND override, max volume, MediaPlayer, state restore
│   ├── voice/                         # Pure Kotlin (JVM unit-tested)
│   │   ├── Mfcc.kt  Dtw.kt  FeatureExtractor.kt  SpeechSegmenter.kt
│   │   ├── VoiceProfile.kt            # Enrollment + threshold calibration
│   │   ├── WakePhraseMatcher.kt       # Phrase + voice decision
│   │   └── VoiceProfileStore.kt       # JSON in app-private storage
│   ├── service/
│   │   ├── FinderService.kt           # Microphone foreground service + wake lock + retry loop
│   │   ├── BootReceiver.kt            # Resume after reboot / update
│   │   ├── FinderSettings.kt          # Enabled flag, sensitivity
│   │   └── Notifications.kt
│   ├── power/Reliability.kt           # Battery-optimisation, DND access, OEM settings intents
│   └── ui/FinderViewModel.kt  FinderScreen.kt
├── app/src/main/res/raw/respond.mp3
└── app/src/test/…                      # DTW, segmenter and matcher tests with synthetic speech
```

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Listening (runtime prompt) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Mic access while the screen is off (Android 14 requires the typed permission) |
| `POST_NOTIFICATIONS` | The mandatory ongoing notification (runtime prompt on Android 13+) |
| `WAKE_LOCK` | Keep the CPU processing audio in Doze |
| `ACCESS_NOTIFICATION_POLICY` | Leave silent/DND and restore it afterwards (user grants "Do Not Disturb access") |
| `MODIFY_AUDIO_SETTINGS` | Raise stream volumes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | One-tap "unrestricted battery" prompt |
| `RECEIVE_BOOT_COMPLETED` | Resume after reboot |

## Setup & first run

1. Build: `./gradlew assembleDebug` (JDK 17, Android SDK 35), or download the APK from the **Voice Finder APK** GitHub Actions run (every push builds it; tags `v*` publish a GitHub Release).
2. Open the app → **Grant permissions**.
3. **Enroll**: in a quiet room, tap **Record sample** and say *ஏய் எங்க இருக்க?* naturally. Repeat 4–5 times (min 3), then **Save profile**.
4. Turn on **Listen in background**.
5. Section 4: tap **Fix** on *Unrestricted battery* and *Do Not Disturb override*, then open app settings and enable Autostart (Xiaomi/Oppo/Vivo) and lock the app in Recents.
6. Test from across the room. The *Last heard* line shows the phrase/voice scores (≤ 1.00 triggers): if it misses you, raise sensitivity a little; if it fires on other speech, lower it — or re-enroll in the place you usually lose the phone.

## Keeping the service alive (best practices used here)

- **Typed foreground service** (`microphone`) started from the visible activity — the only way Android 11+ grants background mic access. `START_STICKY` lets the system recreate it after memory pressure.
- **Partial wake lock with a timeout**, refreshed every 10 min while running — never leaks if the process dies.
- **Unrestricted battery** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) keeps Doze/App Standby from throttling it. Justified under Play policy only because always-on listening is the core function; for a Play Store release, describe it in the listing.
- **OEM killers**: Xiaomi (Autostart + "No restrictions"), Samsung (Never sleeping apps), Oppo/Realme/OnePlus (Allow background activity), Vivo/Huawei (Auto-launch). See dontkillmyapp.com.
- **Mic contention**: if a call or recorder takes the mic, the service retries with exponential backoff (2 s → 60 s) instead of dying.
- **Reboot**: on Android 8–10 listening restarts automatically. On Android 11+ a background service can't get mic access, so a "Tap to resume" notification appears — one tap restores it.
- **No self-trigger**: the mic is ignored while the response plays and for 2 s after.

## Limitations (be honest with yourself)

- The voice check is a lightweight voiceprint, not bank-grade biometrics: a recording of you saying the phrase would trigger it. That's fine for finding a phone; don't reuse it for authentication.
- Range depends on the phone's mic; typically a quiet room or two. Loud TV/music raises the noise floor and reduces range.
- While another app is recording (calls, voice notes) Android gives the mic to that app, so the finder can't hear you during that time.
- Upgrade path: swap `WakePhraseMatcher`'s voice check for a TFLite speaker-embedding model (e.g. ECAPA-TDNN) — `Utterance`/`VoiceProfile` are the only types that change.
