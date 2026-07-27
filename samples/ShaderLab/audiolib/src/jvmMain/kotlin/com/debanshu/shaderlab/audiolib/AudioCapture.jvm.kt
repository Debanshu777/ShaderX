package com.debanshu.shaderlab.audiolib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

private const val SAMPLE_RATE = 44100f
private const val FRAME_SAMPLES = 2048

internal class JvmAudioCapture(
    override val binCount: Int,
) : AudioCapture {
    @Volatile private var latest = FloatArray(binCount)

    override fun currentSpectrum(): FloatArray = latest

    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    @Volatile private var running = false
    private var worker: Thread? = null
    private var line: TargetDataLine? = null

    override suspend fun start() {
        if (running) return
        try {
            // 16-bit mono PCM, little-endian.
            val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) {
                _state.value = AudioCaptureState.UNSUPPORTED
                return
            }
            val dataLine = AudioSystem.getLine(info) as TargetDataLine
            dataLine.open(format, FRAME_SAMPLES * 2)
            dataLine.start()
            line = dataLine
            running = true
            _state.value = AudioCaptureState.RUNNING
            worker =
                thread(name = "audiolib-capture", isDaemon = true) {
                    val raw = ByteArray(FRAME_SAMPLES * 2)
                    val samples = FloatArray(FRAME_SAMPLES)
                    while (running) {
                        val read = dataLine.read(raw, 0, raw.size)
                        val n = read / 2
                        if (n <= 0) continue
                        for (i in 0 until n) {
                            // Little-endian 16-bit → float.
                            val lo = raw[i * 2].toInt() and 0xFF
                            val hi = raw[i * 2 + 1].toInt()
                            samples[i] = ((hi shl 8) or lo) / 32768f
                        }
                        val frame = if (n == FRAME_SAMPLES) samples else samples.copyOf(n)
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
        line?.let {
            try {
                it.stop()
                it.flush()
            } catch (_: Exception) {
            }
            it.close()
        }
        line = null
        if (_state.value == AudioCaptureState.RUNNING) _state.value = AudioCaptureState.IDLE
    }
}

@Composable
public actual fun rememberAudioCapture(binCount: Int): AudioCapture {
    val capture = remember(binCount) { JvmAudioCapture(binCount) }
    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }
    return capture
}
