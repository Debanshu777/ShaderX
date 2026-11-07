package com.debanshu.shaderlab.audiolib

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class SpectrumAnalyzerTest {
    @Test
    fun matchesOneShotNumericsAcrossWindowAndBinSizes() {
        val analyzer = SpectrumAnalyzer()
        val random = Random(17)
        // Includes short reads, non-power-of-two windows, and more bins than FFT frequencies.
        for (count in listOf(0, 1, 2, 3, 8, 31, 64, 127, 256, 1023, 2048, 4097)) {
            val samples = FloatArray(count) { random.nextFloat() * 2f - 1f }
            for (bins in listOf(-1, 0, 1, 8, 64, 256, 512)) {
                assertContentEquals(
                    Fft.magnitudes(samples, bins),
                    analyzer.magnitudes(samples, bins),
                    "sampleCount=$count bins=$bins",
                )
            }
        }
    }

    @Test
    fun repeatedFramesResetScratchAndPreservePublishedSnapshots() {
        val analyzer = SpectrumAnalyzer()
        val tone = FloatArray(2048) { sin(2.0 * PI * 440 * it / 44100).toFloat() }
        val originalInput = tone.copyOf()
        val first = analyzer.magnitudes(tone, 256)
        val saved = first.copyOf()
        repeat(10) {
            assertContentEquals(FloatArray(256), analyzer.magnitudes(FloatArray(2048), 256))
            val next = analyzer.magnitudes(tone, 256)
            assertNotSame(first, next)
            assertContentEquals(saved, next)
        }
        assertContentEquals(saved, first)
        assertContentEquals(originalInput, tone)
    }

    @Test
    fun shortReadsIgnoreStaleTailAndWindowResizingResetsPlan() {
        val analyzer = SpectrumAnalyzer()
        val buffer = FloatArray(2048) { (it % 13) / 13f }
        for (count in listOf(2048, 1031, 127, 0, 1, 2048, 511)) {
            assertContentEquals(
                Fft.magnitudes(buffer.copyOf(count), 64),
                analyzer.magnitudes(buffer, 64, count),
            )
        }
        assertFailsWith<IllegalArgumentException> { analyzer.magnitudes(buffer, 64, -1) }
        assertFailsWith<IllegalArgumentException> { analyzer.magnitudes(buffer, 64, 2049) }
    }
}
