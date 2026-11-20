package com.debanshu.shaderx.sampler

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.*

class EffectOrderTest {
    @Test fun swappingOrderChangesActualRenderedColor() {
        ShaderFactory.create().use { factory ->
            val tinted = render(factory, true)
            val gray = render(factory, false)
            assertEquals(255, Color.getA(gray))
            assertTrue(abs(Color.getR(gray) - Color.getG(gray)) <= 2)
            assertTrue(abs(Color.getG(gray) - Color.getB(gray)) <= 2)
            assertTrue(abs(Color.getR(tinted) - Color.getG(tinted)) > 10 ||
                abs(Color.getB(tinted) - Color.getG(tinted)) > 10,
                "Grayscale before the gradient must retain a visible tint")
            assertEquals(tinted, render(factory, true), "Reorder must not mutate previous uniform values")
        }
    }

    @Test fun intermediateStagesAreRealAndNeutralControlsRemoveOnlyTheirOwnStage() {
        ShaderFactory.create().use { factory ->
            val grayFirst = orderedEffects(true)
            val tintFirst = orderedEffects(false)
            assertEquals(Color.WHITE, render(factory, grayFirst.effects[0]))
            val tintOnly = render(factory, tintFirst.effects[0])
            assertTrue(abs(Color.getR(tintOnly) - Color.getG(tintOnly)) > 10 ||
                abs(Color.getB(tintOnly) - Color.getG(tintOnly)) > 10)
            assertEquals(tintOnly, render(factory, orderedEffects(true, gray = 0f)))
            assertEquals(tintOnly, render(factory, orderedEffects(false, gray = 0f)))
            assertEquals(Color.WHITE, render(factory, orderedEffects(true, tint = 0f)))
            assertEquals(Color.WHITE, render(factory, orderedEffects(false, tint = 0f)))
        }
    }

    private fun render(factory: ShaderFactory, grayscaleFirst: Boolean): Int =
        render(factory, orderedEffects(grayscaleFirst))

    private fun render(factory: ShaderFactory, stage: ShaderEffect): Int {
        val effect = factory.createRenderEffect(stage, 64f, 64f).getOrThrow()
        return effect.asSkiaImageFilter().use { filter ->
            Surface.makeRasterN32Premul(64, 64).use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                Paint().use { paint ->
                    paint.color = Color.WHITE
                    paint.imageFilter = filter
                    surface.canvas.drawRect(Rect.makeWH(64f, 64f), paint)
                }
                surface.makeImageSnapshot().use { image ->
                    Bitmap.makeFromImage(image).use { it.getColor(32, 32) }
                }
            }
        }
    }
}
