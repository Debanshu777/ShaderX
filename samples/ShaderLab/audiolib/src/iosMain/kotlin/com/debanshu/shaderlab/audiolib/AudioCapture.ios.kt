@file:OptIn(ExperimentalForeignApi::class)

package com.debanshu.shaderlab.audiolib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

private const val FRAME_SAMPLES = 2048u

internal class IosAudioCapture(
    override val binCount: Int,
    private val requestPermission: suspend () -> Boolean,
) : AudioCapture {
    @Volatile private var latest = FloatArray(binCount)

    override fun currentSpectrum(): FloatArray = latest

    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    private var engine: AVAudioEngine? = null
    private var running = false

    override suspend fun start() {
        if (running) return
        if (!requestPermission()) {
            _state.value = AudioCaptureState.PERMISSION_DENIED
            return
        }
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryRecord, null)
            session.setActive(true, null)

            val avEngine = AVAudioEngine()
            val input = avEngine.inputNode
            val format = input.inputFormatForBus(0u)
            val analyzer = SpectrumAnalyzer()
            var samples = FloatArray(FRAME_SAMPLES.toInt())

            input.installTapOnBus(0u, FRAME_SAMPLES, format) { buffer, _ ->
                val pcm = buffer as? AVAudioPCMBuffer ?: return@installTapOnBus
                val channelData = pcm.floatChannelData ?: return@installTapOnBus
                val count = pcm.frameLength.toInt()
                if (count <= 0) return@installTapOnBus
                val channel = channelData[0] ?: return@installTapOnBus
                if (samples.size < count) samples = FloatArray(count)
                for (i in 0 until count) samples[i] = channel[i]
                latest = analyzer.magnitudes(samples, binCount, count)
            }

            avEngine.prepare()
            val started =
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    avEngine.startAndReturnError(error.ptr)
                }
            if (!started) {
                avEngine.inputNode.removeTapOnBus(0u)
                try {
                    AVAudioSession.sharedInstance().setActive(false, null)
                } catch (_: Exception) {
                }
                _state.value = AudioCaptureState.ERROR
                return
            }
            engine = avEngine
            running = true
            _state.value = AudioCaptureState.RUNNING
        } catch (e: Exception) {
            _state.value = AudioCaptureState.ERROR
            stop()
        }
    }

    override fun stop() {
        running = false
        engine?.let {
            it.inputNode.removeTapOnBus(0u)
            it.stop()
        }
        engine = null
        try {
            AVAudioSession.sharedInstance().setActive(false, null)
        } catch (_: Exception) {
        }
        if (_state.value == AudioCaptureState.RUNNING) _state.value = AudioCaptureState.IDLE
    }
}

@Composable
public actual fun rememberAudioCapture(binCount: Int): AudioCapture {
    val capture =
        remember(binCount) {
            IosAudioCapture(binCount) {
                suspendCancellableCoroutine { cont ->
                    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                        cont.resume(granted)
                    }
                }
            }
        }
    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }
    return capture
}
