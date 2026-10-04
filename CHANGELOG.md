# Voice Finder 1.0.1

Fix: the phrase didn't trigger after the app was closed.

- **What happened:** after you close the app, Android (Xiaomi/MIUI especially) can keep the mic "on" but feed the app pure silence, so it never heard you even though the mic icon stayed lit.
- **Now:** the app detects that silence within 3 seconds and shows **"Voice Finder can't hear you"**. One tap restores listening; opening the app does too.
- **To stop it happening at all:** lock Voice Finder in Recents (open Recents, then long-press the card or pull it down to show the lock), set Battery saver to **No restrictions**, and turn on **Autostart**.

**Install:** download `VoiceFinder-1.0.1.apk` below and install it over 1.0.0. Your recorded voice profile is kept.
