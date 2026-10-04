# Voice Finder 1.4.0

**Fixed: other sentences in the same tone no longer trigger it**

Before, a different sentence starting with "ஏய்" and said in the same tone (like "ஏய் என்ன பண்ற") could set off the alert. Now:

- **Sharper word matching:** the app compares the actual word sounds and how they change, not mostly the overall tone of your voice.
- **Every part must match:** the beginning, middle and end of the phrase are each checked separately, so one matching word isn't enough.
- **Stricter limits:** the tolerance is set from your own recordings *and* from "same voice, different words" versions made from them, and kept well clear of the second. The sensitivity slider's loosest setting is also tighter.
- **Teach it what not to trigger on:**
  - When a wrong sentence sets it off, tap **"Wrong phrase"** on the alert screen or in the notification. That sentence won't trigger again.
  - Or record such sentences yourself in **Your voice → Phrases that should NOT trigger**.

**Action needed:** after updating, **record your phrase again** (3–6 times). The matching changed, so older recordings can't be used.

**Install:** download `VoiceFinder-1.4.0.apk` below and install it over the old version.
