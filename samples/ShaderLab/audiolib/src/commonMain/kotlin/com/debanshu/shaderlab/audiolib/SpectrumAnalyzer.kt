package com.debanshu.shaderlab.audiolib

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.sin

/**
 * Per-capture FFT workspace for repeated PCM buffers. Uses the same Hann window, radix-2
 * arithmetic, fixed reference, and max-pooling as [Fft.magnitudes]. Scratch storage and transform
 * coefficients are reused while the power-of-two window size is unchanged; only the returned
 * snapshot is allocated per frame. Confine each analyzer to one capture worker.
 */
public class SpectrumAnalyzer {
    private var plan: TransformPlan? = null

    /**
     * Analyzes the first [sampleCount] values without copying a short read into a smaller array.
     * The result belongs to the caller and is never modified by subsequent calls.
     */
    public fun magnitudes(
        samples: FloatArray,
        bins: Int,
        sampleCount: Int = samples.size,
    ): FloatArray {
        require(sampleCount in 0..samples.size) { "sampleCount must be within the input buffer" }
        if (sampleCount < 2 || bins <= 0) return FloatArray(bins.coerceAtLeast(0))
        val size = sampleCount.takeHighestOneBit()
        val transform = plan?.takeIf { it.size == size } ?: TransformPlan(size).also { plan = it }
        return transform.magnitudes(samples, bins)
    }

    private class TransformPlan(val size: Int) {
        private val real = FloatArray(size)
        private val imaginary = FloatArray(size)
        private val window = FloatArray(size) { i ->
            0.5f - 0.5f * cos((2.0 * PI * i / (size - 1)).toFloat())
        }
        private val reversed = IntArray(size)
        private val twiddleReal = FloatArray(size - 1)
        private val twiddleImaginary = FloatArray(size - 1)
        private val reference = log2(1f + size * 0.05f)

        init {
            var j = 0
            for (i in 1 until size) {
                var bit = size shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j or bit
                reversed[i] = j
            }
            var length = 2
            var offset = 0
            while (length <= size) {
                val angle = -2.0 * PI / length
                val realStep = cos(angle).toFloat()
                val imaginaryStep = sin(angle).toFloat()
                var wReal = 1f
                var wImaginary = 0f
                repeat(length / 2) { k ->
                    twiddleReal[offset + k] = wReal
                    twiddleImaginary[offset + k] = wImaginary
                    // Preserve Fft.transform's recurrence and rounding, rather than replacing
                    // each twiddle with an independently evaluated sin/cos.
                    val nextReal = wReal * realStep - wImaginary * imaginaryStep
                    wImaginary = wReal * imaginaryStep + wImaginary * realStep
                    wReal = nextReal
                }
                offset += length / 2
                length = length shl 1
            }
        }

        fun magnitudes(samples: FloatArray, bins: Int): FloatArray {
            for (i in 0 until size) real[i] = samples[i] * window[i]
            imaginary.fill(0f)

            for (i in 1 until size) {
                val j = reversed[i]
                if (i < j) {
                    val value = real[i]
                    real[i] = real[j]
                    real[j] = value
                    // Imaginary input is all zero, so its permutation is unnecessary.
                }
            }

            var length = 2
            var offset = 0
            while (length <= size) {
                val halfLength = length / 2
                var i = 0
                while (i < size) {
                    for (k in 0 until halfLength) {
                        val a = i + k
                        val b = a + halfLength
                        val wReal = twiddleReal[offset + k]
                        val wImaginary = twiddleImaginary[offset + k]
                        val tReal = wReal * real[b] - wImaginary * imaginary[b]
                        val tImaginary = wReal * imaginary[b] + wImaginary * real[b]
                        real[b] = real[a] - tReal
                        imaginary[b] = imaginary[a] - tImaginary
                        real[a] += tReal
                        imaginary[a] += tImaginary
                    }
                    i += length
                }
                offset += halfLength
                length = length shl 1
            }

            val half = size / 2
            return FloatArray(bins) { bin ->
                val start = (bin.toLong() * half / bins).toInt()
                val end = ((bin + 1).toLong() * half / bins).toInt()
                var peak = 0f
                for (k in start until end) {
                    val value = log2(1f + hypot(real[k], imaginary[k])) / reference
                    if (value > peak) peak = value
                }
                peak.coerceIn(0f, 1f)
            }
        }
    }
}
