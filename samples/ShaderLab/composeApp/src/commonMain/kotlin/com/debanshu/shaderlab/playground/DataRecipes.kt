package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.uniform.Rgba8Data
import kotlin.math.roundToInt

/** Sample-only producer: capture/FFT belongs to the application, outside ShaderX core. */
object DataRecipes {
    /** Quantizes already normalized finite FFT bins into R; G/B=0 and A=255 are explicit data. */
    fun fftBins(normalizedMagnitudes: FloatArray): Rgba8Data {
        require(normalizedMagnitudes.size in 1..4096) { "Provide 1..4096 FFT magnitudes" }
        require(normalizedMagnitudes.all { it.isFinite() && it in 0f..1f }) { "FFT magnitudes must be finite and normalized to 0..1" }
        val bytes = ByteArray(normalizedMagnitudes.size * 4)
        for (i in normalizedMagnitudes.indices) {
            bytes[i * 4] = (normalizedMagnitudes[i] * 255f).roundToInt().toByte()
            bytes[i * 4 + 3] = 255.toByte()
        }
        return Rgba8Data(normalizedMagnitudes.size, 1, bytes)
    }
}
