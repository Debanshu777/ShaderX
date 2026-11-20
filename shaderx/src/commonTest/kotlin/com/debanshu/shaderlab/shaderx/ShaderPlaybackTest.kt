package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShaderPlaybackTest {
    @Test
    fun firstFrameRetainsOffsetAndPauseResumeRetainsPhase() {
        val clock = ShaderPlayback(initialPositionSeconds = -2.0)
        clock.onFrame(10_000_000_000)
        assertEquals(-2.0, clock.positionSeconds)
        clock.onFrame(11_000_000_000)
        assertEquals(-1.0, clock.positionSeconds)
        clock.isPlaying = false
        clock.onFrame(30_000_000_000)
        assertEquals(-1.0, clock.positionSeconds)
        clock.isPlaying = true
        clock.onFrame(50_000_000_000)
        assertEquals(-1.0, clock.positionSeconds)
        clock.onFrame(51_000_000_000)
        assertEquals(0.0, clock.positionSeconds)
    }

    @Test
    fun seekIsExactAndReanchorsWithoutChangingPlaybackPolicy() {
        val clock = ShaderPlayback(initialSpeed = 2.0)
        clock.onFrame(0)
        clock.onFrame(1_000_000_000)
        clock.seekTo(42.5)
        assertEquals(42.5, clock.positionSeconds)
        assertTrue(clock.isPlaying)
        assertEquals(2.0, clock.speed)
        clock.onFrame(10_000_000_000)
        assertEquals(42.5, clock.positionSeconds)
        clock.onFrame(10_250_000_000)
        assertEquals(43.0, clock.positionSeconds)
        clock.isPlaying = false
        clock.seekTo(-3.0)
        assertFalse(clock.isPlaying)
        assertEquals(-3.0, clock.positionSeconds)
    }

    @Test
    fun speedChangeAndZeroSpeedDoNotAccumulateOldElapsedTime() {
        val clock = ShaderPlayback()
        clock.onFrame(0)
        clock.onFrame(1_000_000_000)
        clock.speed = 0.0
        assertFalse(clock.isAdvancing)
        assertTrue(clock.isPlaying)
        clock.onFrame(10_000_000_000)
        assertEquals(1.0, clock.positionSeconds)
        clock.speed = 0.5
        assertTrue(clock.isAdvancing)
        clock.onFrame(20_000_000_000)
        clock.onFrame(22_000_000_000)
        assertEquals(2.0, clock.positionSeconds)
        // Assigning the same speed must not continually discard the frame anchor.
        clock.speed = 0.5
        clock.onFrame(24_000_000_000)
        assertEquals(3.0, clock.positionSeconds)
    }

    @Test
    fun suspensionDropsElapsedTimeButKeepsPositionAndPlayingFlag() {
        val clock = ShaderPlayback(initialPositionSeconds = 4.0)
        clock.onFrame(0)
        clock.onFrame(1_000_000_000)
        clock.stopClock()
        assertTrue(clock.isPlaying)
        assertEquals(5.0, clock.positionSeconds)
        clock.onFrame(90_000_000_000)
        assertEquals(5.0, clock.positionSeconds)
        clock.onFrame(91_000_000_000)
        assertEquals(6.0, clock.positionSeconds)
    }

    @Test
    fun sharedConsumersObserveOnePositionAndIndependentClocksStayIndependent() {
        val shared = ShaderPlayback()
        val firstConsumer = shared
        val secondConsumer = shared
        firstConsumer.onFrame(0)
        secondConsumer.onFrame(0)
        firstConsumer.onFrame(1_000_000_000)
        secondConsumer.onFrame(1_000_000_000)
        assertEquals(1.0, firstConsumer.positionSeconds)
        assertEquals(firstConsumer.positionSeconds, secondConsumer.positionSeconds)
        val independent = ShaderPlayback(initialPositionSeconds = 8.0)
        independent.onFrame(1_000_000_000)
        shared.seekTo(7.0)
        assertEquals(8.0, independent.positionSeconds)
        assertEquals(7.0, firstConsumer.positionSeconds)
    }

    @Test
    fun staleFramesCannotRewindOrCountElapsedTimeTwice() {
        val clock = ShaderPlayback()
        clock.onFrame(5_000_000_000)
        clock.onFrame(6_000_000_000)
        clock.onFrame(5_500_000_000)
        clock.onFrame(6_000_000_000)
        clock.onFrame(7_000_000_000)
        assertEquals(2.0, clock.positionSeconds)
    }

    @Test
    fun invalidInputsAreRejectedBeforeMutatingState() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ShaderPlayback(initialPositionSeconds = invalid) }
            assertFailsWith<IllegalArgumentException> { ShaderPlayback(initialSpeed = invalid) }
            val clock = ShaderPlayback(initialPositionSeconds = 2.0)
            assertFailsWith<IllegalArgumentException> { clock.seekTo(invalid) }
            assertFailsWith<IllegalArgumentException> { clock.speed = invalid }
            assertEquals(2.0, clock.positionSeconds)
            assertEquals(1.0, clock.speed)
        }
        assertFailsWith<IllegalArgumentException> { ShaderPlayback(initialSpeed = -1.0) }
        val clock = ShaderPlayback()
        assertFailsWith<IllegalArgumentException> { clock.speed = -0.1 }
        assertEquals(1.0, clock.speed)
    }

    @Test
    fun finiteExtremeInputsCannotProduceInfinitePositionOrOverflowTimestampSubtraction() {
        val clock = ShaderPlayback(initialSpeed = Double.MAX_VALUE)
        clock.onFrame(Long.MIN_VALUE)
        clock.onFrame(Long.MAX_VALUE)
        assertEquals(Double.MAX_VALUE, clock.positionSeconds)
        assertTrue(clock.positionSeconds.isFinite())
    }
}
