package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.ImageExportOptions
import com.debanshu.shaderlab.shaderx.factory.exportImage
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.*

/** Real Default dispatch, factory initialization/ownership and delivery on JVM/Wasm/iOS. */
class ImageExportSuspendTest {
    @Test fun ownedSuspendRouteEvaluatesProvidersAndReturnsFilteredPng() = runTest {
        val source = sourcePng()
        val original = source.copyOf()
        var calls = 0
        val base = GrayscaleEffect()
        val effect = object : RuntimeShaderEffect by base {
            override fun buildUniforms(width: Float, height: Float): List<Uniform> {
                calls++
                assertEquals(18f, width)
                assertEquals(12f, height)
                return base.buildUniforms(width, height)
            }
        }
        repeat(2) {
            val result = exportImage(source, effect, ImageExportOptions(9, 6, 18f, 12f)).getOrThrow()
            assertGrayPng(result)
        }
        assertEquals(2, calls)
        assertContentEquals(original, source)
    }

    @Test fun cancellationDuringRealProviderSuppressesDeliveryThenNextOwnedExportSucceeds() = runTest {
        val source = sourcePng()
        var providerRan = false
        var delivered = false
        val pending = launch {
            val owner = currentCoroutineContext().job
            val base = GrayscaleEffect()
            val effect = object : RuntimeShaderEffect by base {
                override fun buildUniforms(width: Float, height: Float): List<Uniform> {
                    providerRan = true
                    // Cancellation is requested during native/export work; no dispatcher or
                    // thread timing assumptions are needed, including on single-thread Wasm.
                    owner.cancel(CancellationException("Injected during export provider"))
                    return base.buildUniforms(width, height)
                }
            }
            exportImage(source, effect, ImageExportOptions(9, 6))
            delivered = true
        }
        pending.join()
        assertTrue(providerRan)
        assertTrue(pending.isCancelled)
        assertFalse(delivered)
        assertGrayPng(exportImage(source, GrayscaleEffect(), ImageExportOptions(9, 6)).getOrThrow())
    }

    private fun sourcePng(): ByteArray =
        Surface.makeRaster(ImageInfo(3, 2, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB)).use { surface ->
            surface.canvas.clear(Color.makeARGB(128, 255, 0, 0))
            surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
        }

    private fun assertGrayPng(bytes: ByteArray) = Image.makeFromEncoded(bytes).use { image ->
        assertEquals(9, image.width)
        assertEquals(6, image.height)
        Bitmap.makeFromImage(image).use { bitmap ->
            val color = bitmap.getColor(4, 3)
            assertTrue(abs(Color.getA(color) - 128) <= 2)
            assertTrue(abs(Color.getR(color) - 76) <= 3)
            assertTrue(abs(Color.getR(color) - Color.getG(color)) <= 2)
            assertTrue(abs(Color.getR(color) - Color.getB(color)) <= 2)
        }
    }
}
