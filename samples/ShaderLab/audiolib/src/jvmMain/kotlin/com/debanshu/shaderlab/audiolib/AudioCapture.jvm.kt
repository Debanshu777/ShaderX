package com.debanshu.shaderlab.audiolib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

private const val SAMPLE_RATE = 44100f
private const val FRAME_SAMPLES = 2048

internal class JvmAudioCapture(
    override val binCount: Int,
) : AudioCapture by BlockingAudioCapture(
    binCount = binCount,
    dispatcher = Dispatchers.IO,
    frameSamples = FRAME_SAMPLES,
    createInput = {
        val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (AudioSystem.isLineSupported(info)) JvmAudioInput(format, info) else null
    },
)

private class JvmAudioInput(
    private val format: AudioFormat,
    private val info: DataLine.Info,
) : BlockingAudioInput {
    @Volatile private var line: TargetDataLine? = null
    private val raw = ByteArray(FRAME_SAMPLES * 2)

    override fun start() {
        val input = AudioSystem.getLine(info) as TargetDataLine
        line = input
        input.open(format, raw.size)
        input.start()
    }

    override fun read(samples: FloatArray): Int {
        val read = checkNotNull(line).read(raw, 0, raw.size)
        val count = read / 2
        for (i in 0 until count) {
            val lo = raw[i * 2].toInt() and 0xFF
            val hi = raw[i * 2 + 1].toInt()
            samples[i] = ((hi shl 8) or lo) / 32768f
        }
        return if (read < 0) read else count
    }

    override fun stop() {
        line?.let {
            it.stop()
            it.flush()
        }
    }

    override fun close() {
        val input = line
        line = null
        input?.close()
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
