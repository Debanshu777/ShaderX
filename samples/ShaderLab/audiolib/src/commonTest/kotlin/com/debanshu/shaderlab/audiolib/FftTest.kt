package com.debanshu.shaderlab.audiolib

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests for the shared FFT + spectrum helpers used by every platform's mic capture. */
class FftTest {
    @Test
    fun magnitudes_returnsRequestedBinCount() {
        val samples = FloatArray(2048) { 0f }
        val mags = Fft.magnitudes(samples, 64)
        assertEquals(64, mags.size)
    }

    @Test
    fun magnitudes_silenceIsAllZero() {
        val mags = Fft.magnitudes(FloatArray(2048) { 0f }, 32)
        assertTrue(mags.all { it == 0f }, "Silence must produce a zero spectrum")
    }

    @Test
    fun magnitudes_areNormalisedToUnitRange() {
        // Loud full-scale sine — output must still be clamped to 0..1.
        val n = 2048
        val samples = FloatArray(n) { sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() }
        val mags = Fft.magnitudes(samples, 64)
        assertTrue(mags.all { it in 0f..1f }, "All magnitudes must be within 0..1")
        assertTrue(mags.any { it > 0f }, "A tone must produce some energy")
    }

    @Test
    fun magnitudes_toneConcentratesEnergyInOneBand() {
        // A single low tone should put its peak bucket in the lower half of the spectrum.
        val n = 2048
        val freq = 440.0
        val samples = FloatArray(n) { sin(2.0 * PI * freq * it / 44100.0).toFloat() }
        val mags = Fft.magnitudes(samples, 64)
        val peakBin = mags.indices.maxBy { mags[it] }
        assertTrue(
            peakBin < 32,
            "Low tone peak should land in the lower spectrum, was bin $peakBin",
        )
    }

    @Test
    fun magnitudes_respondToVolume() {
        // Fixed-reference normalization: louder input must produce taller bars than quieter input.
        // (Per-frame max normalization would pin both peaks to 1.0 and hide volume changes.)
        val n = 2048
        val freq = 440.0
        val quiet = FloatArray(n) { 0.1f * sin(2.0 * PI * freq * it / 44100.0).toFloat() }
        val loud = FloatArray(n) { sin(2.0 * PI * freq * it / 44100.0).toFloat() }
        val quietPeak = Fft.magnitudes(quiet, 64).max()
        val loudPeak = Fft.magnitudes(loud, 64).max()
        assertTrue(loudPeak > quietPeak, "Loud tone ($loudPeak) must beat quiet tone ($quietPeak)")
        assertTrue(
            loudPeak <= 1f && quietPeak < 1f,
            "Both must stay in 0..1; quiet must not be pinned to 1",
        )
    }

    @Test
    fun transform_impulseHasFlatMagnitudeSpectrum() {
        // FFT of a unit impulse is flat: every real bin magnitude ~1.
        val n = 8
        val re = FloatArray(n).also { it[0] = 1f }
        val im = FloatArray(n)
        Fft.transform(re, im)
        for (i in 0 until n) {
            val mag = kotlin.math.hypot(re[i], im[i])
            assertTrue(kotlin.math.abs(mag - 1f) < 1e-4f, "bin $i magnitude was $mag")
        }
    }

    @Test
    fun toBytes_mapsRangeToZeroTo255() {
        val bytes = Fft.toBytes(floatArrayOf(0f, 0.5f, 1f, 2f, -1f))
        assertEquals(0, bytes[0].toInt() and 0xFF)
        assertEquals(128, bytes[1].toInt() and 0xFF)
        assertEquals(255, bytes[2].toInt() and 0xFF)
        assertEquals(255, bytes[3].toInt() and 0xFF, "over-range clamps to 255")
        assertEquals(0, bytes[4].toInt() and 0xFF, "under-range clamps to 0")
    }

    @Test
    fun resample_returnsRequestedBinCount() {
        assertEquals(64, Fft.resample(FloatArray(256), 64).size)
        assertEquals(8, Fft.resample(FloatArray(256), 8).size)
        assertEquals(512, Fft.resample(FloatArray(256), 512).size)
    }

    @Test
    fun resample_downscalePreservesPeak() {
        val src = FloatArray(256) { it / 256f }
        val out = Fft.resample(src, 64)
        // Each output bin is the max over its source range, so the last bin must be the global max.
        assertEquals(src.max(), out.max(), 1e-5f)
        assertTrue(out.all { it in 0f..1f })
    }

    @Test
    fun resample_emptyAndZeroSpectraAreSafe() {
        // Empty spectrum yields a zero-filled texture of the requested width (not size 0),
        // so the shader's fftSize uniform always matches the texture width.
        assertEquals(64, Fft.resample(FloatArray(0), 64).size)
        assertTrue(Fft.resample(FloatArray(0), 64).all { it == 0f })
        assertTrue(Fft.resample(FloatArray(128), 64).all { it == 0f })
    }
}
