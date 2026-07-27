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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.thread
import kotlin.coroutines.resume

private const val SAMPLE_RATE = 44100
private const val FRAME_SAMPLES = 2048

internal class AndroidAudioCapture(
    override val binCount: Int,
    private val requestPermission: suspend () -> Boolean,
) : AudioCapture {
    @Volatile private var latest = FloatArray(binCount)

    override fun currentSpectrum(): FloatArray = latest

    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    @Volatile private var running = false
    private var worker: Thread? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        if (running) return
        if (!requestPermission()) {
            _state.value = AudioCaptureState.PERMISSION_DENIED
            return
        }
        try {
            val minBuf =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            val bufSize = maxOf(minBuf, FRAME_SAMPLES * 2)
            val rec =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize,
                )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                _state.value = AudioCaptureState.ERROR
                return
            }
            record = rec
            running = true
            _state.value = AudioCaptureState.RUNNING
            rec.startRecording()
            worker =
                thread(name = "audiolib-capture", isDaemon = true) {
                    val pcm = ShortArray(FRAME_SAMPLES)
                    val samples = FloatArray(FRAME_SAMPLES)
                    while (running) {
                        val read = rec.read(pcm, 0, FRAME_SAMPLES)
                        if (read <= 0) continue
                        for (i in 0 until read) {
                            samples[i] = pcm[i] / 32768f
                        }
                        val frame = if (read == FRAME_SAMPLES) samples else samples.copyOf(read)
                        latest = Fft.magnitudes(frame, binCount)
                    }
                }
        } catch (e: Exception) {
            _state.value = AudioCaptureState.ERROR
            stop()
        }
    }

    override fun stop() {
        running = false
        worker?.join(200)
        worker = null
        record?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        record = null
        if (_state.value == AudioCaptureState.RUNNING) _state.value = AudioCaptureState.IDLE
    }
}

@Composable
public actual fun rememberAudioCapture(binCount: Int): AudioCapture {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending.value?.invoke(granted)
            pending.value = null
        }

    val capture =
        remember(binCount) {
            AndroidAudioCapture(binCount) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    true
                } else {
                    suspendCancellableCoroutine { cont ->
                        pending.value = { granted -> cont.resume(granted) }
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }

    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }
    return capture
}
