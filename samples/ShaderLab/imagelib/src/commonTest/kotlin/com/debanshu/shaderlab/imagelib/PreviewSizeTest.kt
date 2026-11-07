package com.debanshu.shaderlab.imagelib

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreviewSizeTest {
    @Test
    fun boundsPortraitLandscapeAndPanoramasWithoutUpscaling() {
        assertEquals(IntSize(2048, 1536), previewSize(8000, 6000, 2048))
        assertEquals(IntSize(144, 192), previewSize(6000, 8000, 192))
        assertEquals(IntSize(20, 10), previewSize(20, 10, 192))
        assertEquals(IntSize(192, 1), previewSize(Int.MAX_VALUE, 1, 192))
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertFailsWith<IllegalArgumentException> { previewSize(0, 10, 192) }
        assertFailsWith<IllegalArgumentException> { previewSize(10, 0, 192) }
        assertFailsWith<IllegalArgumentException> { previewSize(10, 10, 0) }
    }
}
