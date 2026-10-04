# Voice Finder 1.2.0

**Fixed: only the full phrase triggers now**
- Saying just "ஏய்", "எங்க இருக்க", "இருக்க" or "ஏய் எங்க" no longer plays the sound. All three words, "ஏய் எங்க இருக்க", are required.
- How: silence is trimmed before comparing, and the phrase must be about as long as your recordings. Its **start** must match "ஏய்" and its **end** must match "இருக்க", as well as the whole phrase matching.
- A short pause between words (up to 0.6 s) no longer splits the phrase in two.
- Setup now rejects a recording that's too short or missing a word.

**Action needed:** after updating, open the app and **record your phrase again** (3–6 times, the whole phrase each time). Older voice profiles don't have the new start/end check.

**Install:** download `VoiceFinder-1.2.0.apk` below and install it over the old version.
