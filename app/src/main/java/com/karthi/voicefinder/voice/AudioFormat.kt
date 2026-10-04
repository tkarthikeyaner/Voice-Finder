package com.karthi.voicefinder.voice

/** Capture format shared by the microphone, the segmenter and the feature extractor. */
object VoiceAudio {
    const val SAMPLE_RATE = 16_000

    /** 10 ms of audio; the unit the microphone delivers and the segmenter consumes. */
    const val FRAME_SAMPLES = SAMPLE_RATE / 100
}
