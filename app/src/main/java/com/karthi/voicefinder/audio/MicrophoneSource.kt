package com.karthi.voicefinder.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.karthi.voicefinder.voice.VoiceAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** 16 kHz mono PCM from the microphone, emitted in 10 ms frames. The recorder lives only while collected. */
object MicrophoneSource {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(): Flow<ShortArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(VoiceAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "Microphone does not support 16 kHz mono PCM (code $minBuffer)" }
        val recorder = AudioRecord(
            // VOICE_RECOGNITION disables AGC/noise suppression that would distort the features.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            VoiceAudio.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, VoiceAudio.FRAME_SAMPLES * 2 * 50),
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be opened" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone is in use by another app" }
            while (true) {
                currentCoroutineContext().ensureActive()
                val frame = ShortArray(VoiceAudio.FRAME_SAMPLES)
                var filled = 0
                while (filled < frame.size) {
                    val read = recorder.read(frame, filled, frame.size - filled)
                    check(read >= 0) { "Microphone read failed (code $read)" }
                    filled += read
                }
                emit(frame)
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
