package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderFallbackPolicy
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportReason
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createRenderEffectWithFallback
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real Skia compilation/export/raster checks; Android agreement is separately device-tested. */
class ShaderCapabilitiesRenderTest {
    @Test
    fun builtInQueriesMatchStaticRoutesWithoutPopulatingCache() {
        ShaderFactory.create().use { factory ->
            for (effect in ShaderX.builtInEffects()) {
                assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, effect).status)
                assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect).status)
            }
            assertEquals(0, factory.cacheSize, "Queries must never compile shaders")
            for (effect in ShaderX.builtInEffects()) {
                assertIs<ShaderResult.Success<RenderEffect>>(factory.createRenderEffect(effect, SIZE.toFloat(), SIZE.toFloat()), effect.id)
            }
        }
    }

    @Test
    fun supportedCustomSourceStillFailsCompilationAndDoesNotPoisonCache() {
        ShaderFactory.create().use { factory ->
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, Broken).status)
            assertEquals(0, factory.cacheSize)
            val error = assertIs<ShaderResult.Failure<RenderEffect>>(factory.createRenderEffect(Broken, 16f, 16f)).error
            assertIs<ShaderError.CompilationError>(error)
            assertEquals(Broken.shaderSource, error.shaderSource)
            assertEquals(0, factory.cacheSize)
            assertIs<ShaderResult.Success<RenderEffect>>(factory.createRenderEffect(GrayscaleEffect(), 16f, 16f))
        }
    }

    @Test
    fun supportedSkiaExportRoutesProducePixelsWhileInvalidSourceStillFails() {
        val input = Surface.makeRasterN32Premul(SIZE, SIZE).use { surface ->
            surface.canvas.clear(Color.RED)
            surface.makeImageSnapshot().use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
        }
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            for (effect in listOf(GrayscaleEffect(), NativeBlurEffect())) {
                assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect).status)
                val output = processor.process(input, effect).getOrThrow()
                Image.makeFromEncoded(output).use { image ->
                    assertEquals(SIZE, image.width)
                    assertEquals(SIZE, image.height)
                    Bitmap.makeFromImage(image).use { bitmap ->
                        val pixel = bitmap.getColor(SIZE / 2, SIZE / 2)
                        assertEquals(255, Color.getA(pixel))
                        if (effect is BlurEffect) {
                            assertEquals(Color.RED, pixel)
                        } else {
                            assertEquals(Color.getR(pixel), Color.getG(pixel))
                            assertEquals(Color.getG(pixel), Color.getB(pixel))
                            assertFalse(pixel == Color.RED)
                        }
                    }
                }
            }
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.IMAGE_EXPORT, Broken).status)
            val failure = assertIs<ShaderResult.Failure<ByteArray>>(processor.process(input, Broken))
            assertIs<ShaderError.CompilationError>(failure.error)
        }
    }

    @Test
    fun oneChildCompositeRendersButEveryCompositeExportRejectsBeforeDecode() {
        ShaderFactory.create().use { factory ->
            val leaf = GrayscaleEffect()
            val single = CompositeEffect.of(leaf)
            val multi = CompositeEffect.of(leaf, WaveEffect())
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, single).status)
            val singleFilter = factory.createRenderEffect(single, SIZE.toFloat(), SIZE.toFloat()).getOrThrow()
            val leafFilter = factory.createRenderEffect(leaf, SIZE.toFloat(), SIZE.toFloat()).getOrThrow()
            assertContentEquals(raster(leafFilter), raster(singleFilter))
            for (composite in listOf(single, multi, CompositeEffect.of(single))) {
                val support = factory.querySupport(ShaderOperation.IMAGE_EXPORT, composite)
                assertEquals(ShaderSupportReason.COMPOSITE_EXPORT_UNAVAILABLE, support.reason)
                val result = ImageProcessor.create(factory).process(byteArrayOf(1, 2, 3), composite)
                val error = assertIs<ShaderResult.Failure<ByteArray>>(result).error
                assertIs<ShaderError.UnsupportedEffect>(error, "Composite rejection must precede decoding invalid bytes")
                assertEquals(support.message, error.message)
            }
            val support = factory.querySupport(ShaderOperation.CONTENT_RENDER, multi)
            assertEquals(ShaderSupportStatus.SUPPORTED, support.status)
            assertNotNull(factory.createRenderEffect(multi, 16f, 16f).getOrNull())
        }
    }

    @Test
    fun animatedSessionIsIndependentOfStaticCompositeSnapshot() {
        ShaderFactory.create().use { factory ->
            val effect = WaveEffect(time = .5f)
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.ANIMATED_SESSION, effect).status)
            factory.createAnimatedSession(effect, SIZE.toFloat(), SIZE.toFloat()).getOrThrow().use { session ->
                assertNotNull(session.refresh(effect.withTime(1f), SIZE.toFloat(), SIZE.toFloat()).getOrNull())
            }
            val composite = CompositeEffect.of(effect)
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, composite).status)
            assertEquals(ShaderSupportStatus.SUPPORTED,
                factory.querySupport(ShaderOperation.ANIMATED_SESSION, composite).status)
            assertContentEquals(
                raster(factory.createRenderEffect(effect, SIZE.toFloat(), SIZE.toFloat()).getOrThrow()),
                raster(factory.createRenderEffect(composite, SIZE.toFloat(), SIZE.toFloat()).getOrThrow()),
            )
        }
    }

    @Test
    fun preservingWholeFailedChainLeavesOriginalPixelsAndRetainsReason() {
        ShaderFactory.create().use { factory ->
            val chain = CompositeEffect.of(GrayscaleEffect(), Broken)
            val outcome = factory.createRenderEffectWithFallback(chain, SIZE.toFloat(), SIZE.toFloat())
            assertTrue(outcome.fallbackUsed)
            assertIs<ShaderError.CompilationError>(outcome.cause)
            val filter = assertIs<ShaderResult.Success<RenderEffect?>>(outcome.result).value
            assertNull(filter)
            val original = raster(null)
            assertContentEquals(original, raster(filter))
            val partial = factory.createRenderEffect(GrayscaleEffect(), SIZE.toFloat(), SIZE.toFloat()).getOrThrow()
            assertFalse(original.contentEquals(raster(partial)), "Preserving the chain must not secretly retain only its first child")
        }
    }

    @Test
    fun failAndExplicitReplacementAreObservableAndNeverRetryRecursively() {
        ShaderFactory.create().use { backend ->
            val calls = mutableListOf<ShaderEffect>()
            // An existing third-party wrapper remains UNKNOWN; recovery still attempts its operation.
            val factory = object : ShaderFactory by backend {
                override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
                    calls += effect
                    return backend.createRenderEffect(effect, width, height)
                }
            }
            assertEquals(ShaderSupportStatus.UNKNOWN, factory.querySupport(ShaderOperation.CONTENT_RENDER, Broken).status)
            val failed = factory.createRenderEffectWithFallback(Broken, 16f, 16f, ShaderFallbackPolicy.Fail)
            assertFalse(failed.fallbackUsed)
            assertEquals(1, calls.size)
            assertTrue(assertIs<ShaderResult.Failure<RenderEffect?>>(failed.result).error === failed.cause)

            calls.clear()
            val replacement = GrayscaleEffect()
            val replaced = factory.createRenderEffectWithFallback(Broken, SIZE.toFloat(), SIZE.toFloat(), ShaderFallbackPolicy.Substitute(replacement))
            assertEquals(listOf<ShaderEffect>(Broken, replacement), calls)
            assertTrue(replaced.fallbackUsed)
            assertIs<ShaderError.CompilationError>(replaced.cause)
            val replacementFilter = assertIs<ShaderResult.Success<RenderEffect?>>(replaced.result).value
            assertNotNull(replacementFilter)
            assertContentEquals(raster(backend.createRenderEffect(replacement, SIZE.toFloat(), SIZE.toFloat()).getOrThrow()), raster(replacementFilter))

            calls.clear()
            val badReplacement = CompositeEffect.of(replacement, Broken)
            val replacementFailed = factory.createRenderEffectWithFallback(Broken, 16f, 16f, ShaderFallbackPolicy.Substitute(badReplacement))
            assertEquals(listOf<ShaderEffect>(Broken, badReplacement), calls)
            assertTrue(replacementFailed.fallbackUsed)
            assertIs<ShaderError.CompilationError>(replacementFailed.cause)
            assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<RenderEffect?>>(replacementFailed.result).error)

            calls.clear()
            val successful = factory.createRenderEffectWithFallback(replacement, 16f, 16f, ShaderFallbackPolicy.Substitute(Broken))
            assertEquals(listOf<ShaderEffect>(replacement), calls)
            assertFalse(successful.fallbackUsed)
            assertNull(successful.cause)
            assertNotNull(successful.result.getOrNull())
        }
    }

    private fun raster(filter: RenderEffect?): IntArray = Surface.makeRasterN32Premul(SIZE, SIZE).use { surface ->
        Paint().use { layer ->
            layer.imageFilter = filter?.asSkiaImageFilter()
            surface.canvas.saveLayer(Rect.makeWH(SIZE.toFloat(), SIZE.toFloat()), layer)
            Paint().use { content ->
                for (y in 0 until SIZE step 4) for (x in 0 until SIZE step 4) {
                    content.color = Color.makeRGB(x * 7, y * 7, 200)
                    surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 4f, 4f), content)
                }
            }
            surface.canvas.restore()
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { bitmap -> IntArray(SIZE * SIZE) { bitmap.getColor(it % SIZE, it / SIZE) } }
        }
    }

    private object Broken : RuntimeShaderEffect {
        override val id = "sx04_broken"
        override val displayName = "Broken"
        override val parameters: List<ParameterSpec> = emptyList()
        override val shaderSource = "INVALID SOURCE SX04"
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = error("Invalid source must fail before uniform evaluation")
        override fun withTypedParameter(parameterId: String, value: ParameterValue): RuntimeShaderEffect = this
    }

    private companion object { const val SIZE = 16 }
}
