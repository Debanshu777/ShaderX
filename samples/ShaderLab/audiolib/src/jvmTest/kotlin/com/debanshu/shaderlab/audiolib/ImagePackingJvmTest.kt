package com.debanshu.shaderlab.audiolib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** JVM/Skia test for packing an FFT spectrum into an ImageBitmap. */
class ImagePackingJvmTest {
    @Test
    fun packMagnitudesToImage_hasOneColumnPerBin() {
        val mags = FloatArray(64) { it / 64f }
        val image = packMagnitudesToImage(mags)
        assertEquals(64, image.width)
        assertEquals(1, image.height)
    }

    @Test
    fun packMagnitudesToImage_emptySpectrumStillProducesValidImage() {
        val image = packMagnitudesToImage(FloatArray(0))
        assertTrue(image.width >= 1)
        assertEquals(1, image.height)
    }
}
