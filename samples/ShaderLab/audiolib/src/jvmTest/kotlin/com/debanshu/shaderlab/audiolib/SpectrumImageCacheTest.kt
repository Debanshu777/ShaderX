package com.debanshu.shaderlab.audiolib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SpectrumImageCacheTest {
    @Test
    fun unchangedSnapshotsReuseTextureAcrossDisplayFrames() {
        val cache = SpectrumImageCache()
        val spectrum = floatArrayOf(0f, 0.5f, 1f, 0.25f)
        val first = cache.imageFor(spectrum, 4)

        repeat(120) { assertSame(first, cache.imageFor(spectrum, 4)) }
        assertSame(first, cache.imageFor(spectrum.copyOf(), 4))
    }

    @Test
    fun newAudioAndBinChangesRefreshTexture() {
        val cache = SpectrumImageCache()
        val spectrum = floatArrayOf(0f, 0.5f, 1f, 0.25f)
        val first = cache.imageFor(spectrum, 4)
        val changed = cache.imageFor(floatArrayOf(1f, 0f, 0f, 0f), 4)
        assertNotSame(first, changed)

        val resized = cache.imageFor(floatArrayOf(1f, 0f, 0f, 0f), 8)
        assertNotSame(changed, resized)
        assertEquals(8, resized.width)
        assertSame(resized, cache.imageFor(floatArrayOf(1f, 0f, 0f, 0f), 8))
    }
}
