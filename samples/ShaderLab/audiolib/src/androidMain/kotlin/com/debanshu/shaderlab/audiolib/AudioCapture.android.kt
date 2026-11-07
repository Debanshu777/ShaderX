package com.debanshu.shaderlab.audiolib

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_RATE = 44100
private const val FRAME_SAMPLES = 2048

internal class AndroidAudioCapture(
    override val binCount: Int,
    requestPermission: suspend () -> Boolean,
) : AudioCapture by BlockingAudioCapture(
    binCount = binCount,
    dispatcher = Dispatchers.IO,
    frameSamples = FRAME_SAMPLES,
    createInput = { AndroidAudioInput() },
    requestPermission = requestPermission,
)

private class AndroidAudioInput : BlockingAudioInput {
    @Volatile private var record: AudioRecord? = null
    private val pcm = ShortArray(FRAME_SAMPLES)

    @SuppressLint("MissingPermission")
    override fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "Microphone configuration is not supported" }
        val input = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, FRAME_SAMPLES * 2),
        )
        record = input
        check(input.state == AudioRecord.STATE_INITIALIZED) { "Microphone failed to initialize" }
        input.startRecording()
    }

    override fun read(samples: FloatArray): Int {
        val count = checkNotNull(record).read(pcm, 0, FRAME_SAMPLES)
        for (i in 0 until count) samples[i] = pcm[i] / 32768f
        return count
    }

    override fun stop() {
        record?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
    }

    override fun close() {
        val input = record
        record = null
        try {
            if (input?.recordingState == AudioRecord.RECORDSTATE_RECORDING) input.stop()
        } finally {
            input?.release()
        }
    }
}

@Composable
public actual fun rememberAudioCapture(binCount: Int): AudioCapture {
    val context = LocalContext.current
    val permission = remember { SharedAudioPermission() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permission.complete(granted)
        }

    val capture =
        remember(binCount, context) {
            AndroidAudioCapture(binCount) {
                withContext(Dispatchers.Main.immediate) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        true
                    } else {
                        permission.await { launcher.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                }
            }
        }

    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }
    DisposableEffect(permission) {
        onDispose { permission.complete(false) }
    }
    return capture
}
