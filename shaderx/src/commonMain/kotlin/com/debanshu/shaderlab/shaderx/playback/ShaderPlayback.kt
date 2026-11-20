package com.debanshu.shaderlab.shaderx.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Externally owned animation position, independent of shader configuration and native resources.
 *
 * Use the Compose playback driver to advance this state. Share one instance between effects to
 * synchronize them; independent instances have independent positions. Access it on the Compose
 * UI thread. This state contains no native handles and does not schedule work itself.
 *
 * Positions are finite signed seconds. Speed must be finite and nonnegative; zero retains the
 * current position without advancing. Pausing, changing speed, seeking, or suspending the driver
 * retains the last sampled position and discards the frame anchor. The next frame establishes a
 * fresh anchor, so suspension never accumulates hidden elapsed time. At numerical overflow the
 * position saturates at [Double.MAX_VALUE]. Rendering adapters may impose narrower time limits.
 *
 * @param initialPositionSeconds Initial phase in finite signed seconds.
 * @param initiallyPlaying Whether the driver should initially advance this timeline.
 * @param initialSpeed Finite nonnegative playback multiplier.
 */
@Stable
public class ShaderPlayback(
    initialPositionSeconds: Double = 0.0,
    initiallyPlaying: Boolean = true,
    initialSpeed: Double = 1.0,
) {
    init {
        require(initialPositionSeconds.isFinite()) { "Playback position must be finite" }
        require(initialSpeed.isFinite() && initialSpeed >= 0.0) {
            "Playback speed must be finite and nonnegative"
        }
    }

    private var position by mutableStateOf(initialPositionSeconds)
    private var playingState by mutableStateOf(initiallyPlaying)
    private var multiplier by mutableStateOf(initialSpeed)
    private var lastFrameTimeNanos: Long? = null

    /** Last sampled phase, in seconds. Use [seekTo] to change it. */
    public val positionSeconds: Double get() = position

    /** Pause/resume without resetting phase; resuming starts at the next driver's frame. */
    public var isPlaying: Boolean
        get() = playingState
        set(value) {
            if (playingState != value) {
                playingState = value
                stopClock()
            }
        }

    /** Finite nonnegative multiplier. Zero settles while preserving [isPlaying]. */
    public var speed: Double
        get() = multiplier
        set(value) {
            require(value.isFinite() && value >= 0.0) {
                "Playback speed must be finite and nonnegative"
            }
            if (multiplier != value) {
                multiplier = value
                stopClock()
            }
        }

    /** Whether playback requests time advancement, before driver visibility/motion policy. */
    public val isAdvancing: Boolean get() = playingState && multiplier > 0.0

    /** Set an exact finite signed phase without changing [isPlaying] or [speed]. */
    public fun seekTo(seconds: Double) {
        require(seconds.isFinite()) { "Playback position must be finite" }
        position = seconds
        stopClock()
    }

    internal fun onFrame(frameTimeNanos: Long) {
        if (!isAdvancing) {
            stopClock()
            return
        }
        val previous = lastFrameTimeNanos
        if (previous == null) {
            lastFrameTimeNanos = frameTimeNanos
            return
        }
        // Duplicate/shared or out-of-order frames cannot advance the timeline twice or rewind it.
        if (frameTimeNanos <= previous) return
        lastFrameTimeNanos = frameTimeNanos
        val elapsedSeconds = (frameTimeNanos.toDouble() - previous.toDouble()) / 1_000_000_000.0
        position = (position + elapsedSeconds * multiplier).coerceAtMost(Double.MAX_VALUE)
    }

    internal fun stopClock() {
        lastFrameTimeNanos = null
    }
}
