package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual Skia CPU raster checks, not Android or physical GPU color-space evidence. */
class ParameterSchemaRenderTest {
    @Test
    fun typedStaticColorMatchesLegacyPixelsAndRetainsExistingAlphaBehavior() {
        ShaderFactory.create().use { factory ->
            val original = GradientEffect()
            val typed = GradientEffect.SCHEMA.valuesFrom(original)
                .withValue(GradientEffect.COLOR_1.key, 0x204080C0L)
                .withValue(GradientEffect.COLOR_2.key, 0x90C04080L)
                .withValue(GradientEffect.INTENSITY.key, .75f)
                .applyTo(original) as GradientEffect
            val legacy = original.withTypedParameter("color1", ParameterValue.ColorValue(0x204080C0L))
                .withTypedParameter("color2", ParameterValue.ColorValue(0x90C04080L))
                .withParameter("intensity", .75f)
            val pixels = render(factory, typed)
            assertContentEquals(render(factory, legacy), pixels)
            assertFalse(render(factory, original).contentEquals(pixels), "Updated values must affect real pixels")
            assertContentEquals(render(factory, typed.copy(color1 = 0xFF4080C0L, color2 = 0xFFC04080L)), pixels,
                "Gradient historically ignores gradient-color alpha; this API must preserve that rendering")
            assertTrue(pixels.all { Color.getA(it) == 255 }, "Gradient preserves opaque content alpha")
        }
    }

    @Test
    fun typedAnimatedEditAndSessionRefreshMatchLegacyAtPreservedTime() {
        ShaderFactory.create().use { factory ->
            val original = WaveEffect(amplitude = 0f, animate = false, time = .75f)
            val typed = WaveEffect.SCHEMA.valuesFrom(original)
                .withValue(WaveEffect.AMPLITUDE.key, 13.125f)
                .withValue(WaveEffect.FREQUENCY.key, 7.25f)
                .applyTo(original) as WaveEffect
            val legacy = original.withParameter("amplitude", 13.125f).withParameter("frequency", 7.25f)
            assertEquals(.75f, typed.time)
            assertFalse(typed.isAnimating)
            assertEquals(typed, typed.withTime(99f))
            val pixels = render(factory, typed)
            assertContentEquals(render(factory, legacy), pixels)
            assertFalse(render(factory, original).contentEquals(pixels), "Wave edits must distort the patterned input")
            // Configuration edits create a new session; refresh only updates frame uniforms.
            val session = factory.createAnimatedSession(typed, SIZE.toFloat(), SIZE.toFloat()).getOrNull()
                ?: error("Wave session creation failed")
            session.use {
                val nextFrame = typed.withTime(1.25f)
                val filter = session.refresh(nextFrame, SIZE.toFloat(), SIZE.toFloat()).getOrNull()
                    ?: error("Wave session refresh failed")
                assertContentEquals(render(factory, nextFrame), raster(filter))
            }
        }
    }

    private fun render(factory: ShaderFactory, effect: ShaderEffect): IntArray {
        val filter = factory.createRenderEffect(effect, SIZE.toFloat(), SIZE.toFloat()).getOrNull()
            ?: error("${effect.id} compilation failed")
        return raster(filter)
    }

    private fun raster(filter: RenderEffect): IntArray = Surface.makeRasterN32Premul(SIZE, SIZE).use { surface ->
        Paint().use { layer ->
            layer.imageFilter = filter.asSkiaImageFilter()
            surface.canvas.saveLayer(Rect.makeWH(SIZE.toFloat(), SIZE.toFloat()), layer)
            Paint().use { content ->
                for (y in 0 until SIZE step 4) {
                    for (x in 0 until SIZE step 4) {
                        content.color = Color.makeRGB(x * 7, y * 7, if ((x + y) % 8 == 0) 240 else 16)
                        surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 4f, 4f), content)
                    }
                }
            }
            surface.canvas.restore()
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { bitmap -> IntArray(SIZE * SIZE) { bitmap.getColor(it % SIZE, it / SIZE) } }
        }
    }

    private companion object { const val SIZE = 32 }
}
