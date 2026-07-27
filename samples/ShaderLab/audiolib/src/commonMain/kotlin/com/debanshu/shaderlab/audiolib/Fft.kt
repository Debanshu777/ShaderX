package com.debanshu.shaderlab.audiolib

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Minimal in-place radix-2 Cooley–Tukey FFT and spectrum helpers.
 *
 * Pure common Kotlin — no platform dependency — so every target computes the spectrum from raw PCM
 * the same way. Not optimised for large transforms; sized for real-time audio visualization
 * (typically 1024–2048 samples per frame).
 */
public object Fft {
    /**
     * In-place complex FFT. [re] and [im] must be the same length and a power of two.
     *
     * @param re Real components (input samples); overwritten with the real part of the transform.
     * @param im Imaginary components; pass zeros for real input; overwritten with the imaginary part.
     */
    public fun transform(
        re: FloatArray,
        im: FloatArray,
    ) {
        val n = re.size
        require(n == im.size) { "re and im must have the same length" }
        if (n <= 1) return
        require(n and (n - 1) == 0) { "FFT length must be a power of two, got $n" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        // Danielson–Lanczos section.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRealStep = cos(ang).toFloat()
            val wImagStep = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wReal = 1f
                var wImag = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val tReal = wReal * re[b] - wImag * im[b]
                    val tImag = wReal * im[b] + wImag * re[b]
                    re[b] = re[a] - tReal
                    im[b] = im[a] - tImag
                    re[a] += tReal
                    im[a] += tImag
                    val nextWReal = wReal * wRealStep - wImag * wImagStep
                    wImag = wReal * wImagStep + wImag * wRealStep
                    wReal = nextWReal
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes a normalised magnitude spectrum from real PCM [samples].
     *
     * Applies a Hann window, runs an FFT over the largest power-of-two prefix of [samples], takes
     * the magnitude of the first half (real spectrum), compresses it logarithmically for a more
     * musical response, and resamples down to [bins] values in `0f..1f`.
     *
     * @param samples Mono PCM in roughly `-1f..1f`.
     * @param bins Number of output frequency bins (e.g. 64).
     * @return Magnitudes in `0f..1f`, length [bins].
     */
    public fun magnitudes(
        samples: FloatArray,
        bins: Int,
    ): FloatArray {
        if (samples.isEmpty() || bins <= 0) return FloatArray(bins.coerceAtLeast(0))

        // Largest power-of-two window that fits.
        val n = 1 shl log2(samples.size.toDouble()).toInt()
        if (n < 2) return FloatArray(bins)

        val re = FloatArray(n)
        val im = FloatArray(n)
        for (i in 0 until n) {
            // Hann window reduces spectral leakage.
            val w = 0.5f - 0.5f * cos((2.0 * PI * i / (n - 1)).toFloat())
            re[i] = samples[i] * w
        }

        transform(re, im)

        val half = n / 2
        val spectrum = FloatArray(half)
        // Fixed full-scale reference instead of per-frame max. A per-frame max would pin the
        // loudest bin to 1.0 every frame, so bars would show spectral *shape* but never respond
        // to *volume*. Reference is intentionally softer than theoretical full-scale (n/4) so
        // typical microphone / speech levels map into a visible 0..1 range instead of sitting
        // near zero.
        val reference = log2(1f + n * 0.05f)
        for (i in 0 until half) {
            // Log compression: quiet detail stays visible next to loud peaks.
            spectrum[i] = log2(1f + hypot(re[i], im[i]))
        }

        // Resample the half-spectrum down to `bins` buckets (max within each bucket) and
        // normalise by the fixed reference so volume drives height.
        val out = FloatArray(bins)
        for (b in 0 until bins) {
            val start = (b.toLong() * half / bins).toInt()
            val end = min((((b + 1).toLong() * half) / bins).toInt(), half)
            var peak = 0f
            var k = start
            while (k < end) {
                val v = spectrum[k] / reference
                if (v > peak) peak = v
                k++
            }
            out[b] = peak.coerceIn(0f, 1f)
        }
        return out
    }

    /**
     * Resamples an already-binned spectrum to a different bin count using max-pooling
     * (consistent with [magnitudes]). Use this when the capture emits a fixed bin count but
     * the consuming shader/texture needs a different width — e.g. a live visualizer whose
     * "frequency bins" slider must not recreate the microphone capture.
     */
    public fun resample(
        spectrum: FloatArray,
        bins: Int,
    ): FloatArray {
        if (bins <= 0) return FloatArray(0)
        if (spectrum.isEmpty()) return FloatArray(bins)
        val out = FloatArray(bins)
        for (b in 0 until bins) {
            val start = (b.toLong() * spectrum.size / bins).toInt()
            val end =
                (((b + 1).toLong() * spectrum.size) / bins)
                    .toInt()
                    .coerceAtLeast(start + 1)
                    .coerceAtMost(spectrum.size)
            var peak = 0f
            for (k in start until end) {
                if (spectrum[k] > peak) peak = spectrum[k]
            }
            out[b] = peak.coerceIn(0f, 1f)
        }
        return out
    }

    /** Packs normalised magnitudes (`0f..1f`) into a byte-per-bin array (`0..255`). */
    public fun toBytes(magnitudes: FloatArray): ByteArray =
        ByteArray(magnitudes.size) {
            (magnitudes[it].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }

    private const val PI = kotlin.math.PI
}
