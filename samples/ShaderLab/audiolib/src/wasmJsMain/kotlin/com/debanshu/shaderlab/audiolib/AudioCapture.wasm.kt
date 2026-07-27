package com.debanshu.shaderlab.audiolib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// spectrum is pull-based via currentSpectrum(); Web Audio's AnalyserNode owns the FFT.

// Web Audio's AnalyserNode already performs the FFT, so the common Fft is not used on Wasm.
// The JS glue below requests the mic, wires an AnalyserNode, and exposes:
//   audiolibStart()               — getUserMedia + analyser (browser shows the mic prompt)
//   audiolibState()               — "idle" | "running" | "denied"
//   audiolibSnapshot()            — pulls the latest byte frequency data into a JS buffer
//   audiolibBin(index, binCount)  — max magnitude (0..1) of bucket `index` from the last snapshot
private fun audiolibStart() {
    js(
        """
        {
          if (globalThis.__audiolibState === 'running') return;
          globalThis.__audiolibState = 'starting';
          navigator.mediaDevices.getUserMedia({ audio: true }).then(function(stream) {
            var Ctx = globalThis.AudioContext || globalThis.webkitAudioContext;
            var ctx = new Ctx();
            var src = ctx.createMediaStreamSource(stream);
            var analyser = ctx.createAnalyser();
            analyser.fftSize = 2048;
            src.connect(analyser);
            globalThis.__audiolibAnalyser = analyser;
            globalThis.__audiolibBuf = new Uint8Array(analyser.frequencyBinCount);
            globalThis.__audiolibState = 'running';
          }).catch(function(e) {
            globalThis.__audiolibState = 'denied';
          });
        }
        """,
    )
}

private fun audiolibState(): String = js("(globalThis.__audiolibState || 'idle')")

private fun audiolibSnapshot() {
    js(
        """
        {
          var a = globalThis.__audiolibAnalyser;
          if (a && globalThis.__audiolibBuf) { a.getByteFrequencyData(globalThis.__audiolibBuf); }
        }
        """,
    )
}

private fun audiolibBin(
    index: Int,
    binCount: Int,
): Double =
    js(
        """
        {
          var buf = globalThis.__audiolibBuf;
          if (!buf || buf.length === 0) return 0.0;
          var half = buf.length;
          var start = Math.floor(index * half / binCount);
          var end = Math.floor((index + 1) * half / binCount);
          var peak = 0;
          for (var k = start; k < end; k++) { if (buf[k] > peak) peak = buf[k]; }
          return peak / 255.0;
        }
        """,
    )

internal class WasmAudioCapture(
    override val binCount: Int,
) : AudioCapture {
    private var latest = FloatArray(binCount)

    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    override suspend fun start() {
        audiolibStart()
        _state.value = AudioCaptureState.RUNNING
    }

    // Pull-based: snapshot Web Audio's analyser on each call from the render loop.
    override fun currentSpectrum(): FloatArray {
        when (audiolibState()) {
            "running" -> {
                _state.value = AudioCaptureState.RUNNING
                audiolibSnapshot()
                latest = FloatArray(binCount) { audiolibBin(it, binCount).toFloat() }
            }

            "denied" -> {
                _state.value = AudioCaptureState.PERMISSION_DENIED
            }

            else -> { // idle / starting
            }
        }
        return latest
    }

    override fun stop() {
        if (_state.value == AudioCaptureState.RUNNING) _state.value = AudioCaptureState.IDLE
    }
}

@Composable
public actual fun rememberAudioCapture(binCount: Int): AudioCapture {
    val capture = remember(binCount) { WasmAudioCapture(binCount) }
    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }
    return capture
}
