package com.debanshu.shaderlab
import com.debanshu.shaderlab.playground.decodeWorkbenchImage
import com.debanshu.shaderlab.shaderx.factory.create
import kotlin.io.encoding.Base64
import kotlin.test.*
class WorkbenchDecodeTest {
    @Test fun nativeComposeDecodeRetainsPixels() {
        val image = decodeWorkbenchImage(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP4z8AARAwQCgAf7gP9i18U1AAAAABJRU5ErkJggg=="))
        val pixels = IntArray(4)
        image.readPixels(pixels)
        val legacy = com.debanshu.shaderlab.imagelib.decodeImageBytes(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP4z8AARAwQCgAf7gP9i18U1AAAAABJRU5ErkJggg=="), 256)
        val legacyPixels = IntArray(4)
        assertNotNull(legacy, "A valid fixture must decode through the existing imagelib path")
        legacy.readPixels(legacyPixels)
        assertTrue(legacyPixels.all { it == 0xFFFF0000.toInt() }, "Valid RGB PNG must retain red pixels through imagelib")
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertTrue(pixels.all { it == 0xFFFF0000.toInt() }, pixels.joinToString { it.toUInt().toString(16) })
    }
}

class WorkbenchBrowserPipelineTest {
    @Test fun actualShaderXNativeRenderDecodesUsablePixels() {
        val input = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAADklEQVR4nGP4DwYMEAoAU7oL9ZisIGcAAAAASUVORK5CYII=")
        // Valid 2x2 white RGB PNG: the warm tint must actually change green/blue output.
        val factory = com.debanshu.shaderlab.shaderx.factory.ShaderFactory.create(1)
        try {
            val project = com.debanshu.shaderlab.playground.WorkbenchStarters.tint.build()
            val output = com.debanshu.shaderlab.shaderx.factory.ImageProcessor.create(factory).process(input, project.effect)
            val image = decodeWorkbenchImage(output.getOrThrow())
            val pixels = IntArray(4)
            image.readPixels(pixels)
            for (pixel in pixels) {
                assertEquals(255, pixel ushr 24)
                assertTrue(kotlin.math.abs(((pixel ushr 16) and 255) - 255) <= 2)
                assertTrue(kotlin.math.abs(((pixel ushr 8) and 255) - 219) <= 2,
                    "Warm tint must change white's green channel: ${pixel.toUInt().toString(16)}")
                assertTrue(kotlin.math.abs((pixel and 255) - 185) <= 2,
                    "Warm tint must change white's blue channel: ${pixel.toUInt().toString(16)}")
            }
        } finally { factory.close() }
    }
}
