package com.debanshu.shaderlab.audiolib

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

// StateFlow retained for `state`.

/** Runtime state of a microphone [AudioCapture] session. */
public enum class AudioCaptureState {
    /** Not yet started, or stopped. */
    IDLE,

    /** Microphone permission was denied by the user or platform. */
    PERMISSION_DENIED,

    /** Actively capturing and emitting spectra. */
    RUNNING,

    /** Capture failed to start (no device, platform error). */
    ERROR,

    /** This platform has no microphone capture implementation. */
    UNSUPPORTED,
}

/**
 * Live microphone capture that emits a normalised frequency spectrum for shader visualization.
 *
 * The spectrum is a [FloatArray] of [binCount] magnitudes in `0f..1f`, updated continuously while
 * the session is [AudioCaptureState.RUNNING]. Collect [spectrum] and feed it to a shader — e.g. via
 * [packMagnitudesToImage] and
 * [com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect.withFftData].
 */
public interface AudioCapture {
    /** Number of frequency bins in each emitted spectrum. */
    public val binCount: Int

    /**
     * Returns the latest normalised magnitude spectrum (`0f..1f`), length [binCount].
     *
     * Pull-based: call once per frame from the render loop. Push platforms (Android/JVM/iOS) return
     * the most recent buffer captured off-thread; pull platforms (Wasm/Web Audio) snapshot on call.
     */
    public fun currentSpectrum(): FloatArray

    /** Current capture state. */
    public val state: StateFlow<AudioCaptureState>

    /**
     * Requests microphone permission if needed and begins capture. Idempotent while running.
     * Updates [state] to reflect the outcome.
     */
    public suspend fun start()

    /** Stops capture and releases the microphone. Safe to call when not running. */
    public fun stop()
}

/**
 * Packs a normalised magnitude spectrum (`0f..1f`) into a 1-pixel-tall [ImageBitmap] — one column
 * per bin, magnitude written to the red channel — ready to bind as an
 * [com.debanshu.shaderlab.shaderx.uniform.TextureUniform].
 */
public expect fun packMagnitudesToImage(magnitudes: FloatArray): ImageBitmap

/**
 * Remembers an [AudioCapture] bound to the current platform's microphone.
 *
 * On platforms without a microphone implementation this returns a capture whose [AudioCapture.state]
 * is [AudioCaptureState.UNSUPPORTED] and whose spectrum stays silent.
 *
 * @param binCount Number of frequency bins to emit (default 64).
 */
@Composable
public expect fun rememberAudioCapture(binCount: Int = 64): AudioCapture
