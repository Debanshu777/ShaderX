package com.debanshu.shaderlab.audiolib

import androidx.compose.ui.graphics.ImageBitmap

/** Keeps a spectrum texture until its immutable source snapshot or display width changes. */
public class SpectrumImageCache {
    private var previousSpectrum: FloatArray? = null
    private var previousBins: Int = 0
    private var image: ImageBitmap? = null

    /**
     * Returns a texture for [bins] columns. [spectrum] must be a read-only snapshot, as returned by
     * [AudioCapture.currentSpectrum]; retaining it avoids copying PCM-derived data every display frame.
     */
    public fun imageFor(
        spectrum: FloatArray,
        bins: Int,
    ): ImageBitmap {
        val cached = image
        if (cached != null && previousBins == bins &&
            (previousSpectrum === spectrum || previousSpectrum.contentEquals(spectrum))
        ) {
            previousSpectrum = spectrum
            return cached
        }
        val resampled = Fft.resample(spectrum, bins)
        return packMagnitudesToImage(resampled).also {
            previousSpectrum = spectrum
            previousBins = bins
            image = it
        }
    }
}
