package com.debanshu.shaderx.sampler

import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the screen's actual public definition through Skia, not a copy of its source. */
class CustomEffectRenderTest {
    @Test
    fun sliderEndpointsRenderExpectedPixelsAndKeepInstancesIndependent() {
        val input = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAADklEQVR4nGP4DwYMEAoAU7oL9ZisIGcAAAAASUVORK5CYII=")
        val defaults = warmTint.effect()
        val original = defaults.withValue(strength.key, 0f)
        val warm = defaults.withValue(strength.key, 1f)
        val cool = warm.withValue(temperature.key, 0f)
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            // Render the original again after warmth to catch cross-instance mutable uniform state.
            for ((effect, channels) in listOf(original to listOf(255, 255, 255),
                warm to listOf(255, 166, 102), cool to listOf(102, 166, 255), original to listOf(255, 255, 255))) {
                val output = processor.process(input, effect).getOrThrow()
                Image.makeFromEncoded(output).use { image ->
                    assertEquals(2, image.width)
                    assertEquals(2, image.height)
                    Bitmap.makeFromImage(image).use { bitmap ->
                        for (y in 0..1) for (x in 0..1) {
                            val pixel = bitmap.getColor(x, y)
                            assertEquals(255, Color.getA(pixel))
                            val actual = listOf(Color.getR(pixel), Color.getG(pixel), Color.getB(pixel))
                            channels.zip(actual).forEach { (expected, value) ->
                                assertTrue(abs(expected - value) <= 2, "Expected $channels, got $actual")
                            }
                        }
                    }
                }
            }
        }
        assertEquals(0.65f, defaults[strength.key])
        assertEquals(0f, original[strength.key])
        assertEquals(1f, warm[strength.key])
    }
}
