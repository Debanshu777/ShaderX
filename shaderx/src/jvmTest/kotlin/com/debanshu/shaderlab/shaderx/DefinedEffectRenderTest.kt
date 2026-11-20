package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.authoring.DefinitionShaderSession
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real Skia raster pixels through public declarations and factory/session ownership. */
class DefinedEffectRenderTest {
    private val color = ColorParameterDefinition("defined_tint", "color", 0x804080C0L)
    private val tint = ShaderDefinition("defined_tint", "Tint", ShaderProgram("""
        uniform shader content;
        layout(color) uniform half4 tint;
        half4 main(float2 p) { return half4(tint.rgb * tint.a, tint.a) * content.eval(p).a; }
    """.trimIndent(), listOf(ShaderBinding.color("tint", color.key))), ParameterSchema("defined_tint", listOf(color)))

    @Test
    fun imageExportKeepsProviderFailuresDistinctFromActualSourceDiagnostics() {
        val bytes = Surface.makeRasterN32Premul(4, 4).use { surface ->
            surface.canvas.clear(Color.WHITE)
            surface.makeImageSnapshot().use { image -> image.encodeToData()!!.use { it.bytes } }
        }
        val provider = ShaderDefinition("export_provider", program = ShaderProgram("""
            uniform shader content;
            uniform float level;
            half4 main(float2 p) { return content.eval(p) * level; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("level"))))
            .animatedEffect(frameProvider = ShaderFrameProvider { error("export sensor unavailable") })
        val broken = ShaderDefinition("export_broken", program = ShaderProgram("invalid source")).effect()
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            val failure = assertIs<ShaderError.Unknown>(assertIs<ShaderResult.Failure<*>>(processor.process(bytes, provider)).error)
            assertTrue(failure.message.contains("export_provider"))
            assertTrue(failure.message.contains("provider execution"))
            assertTrue(failure.cause != null)
            val compilation = assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(processor.process(bytes, broken)).error)
            assertTrue(compilation.message.contains("export_broken"))
            assertEquals("invalid source", compilation.shaderSource)
        }
    }

    @Test
    fun twoStaticInstancesRetainIndependentColorsAndPremultipliedAlpha() {
        ShaderFactory.create().use { factory ->
            val first = factory.createRenderEffect(tint.effect(), 16f, 16f).getOrThrow()
            val second = factory.createRenderEffect(tint.effect().withValue(color.key, 0xC0C02040L), 16f, 16f).getOrThrow()
            val firstPixel = pixel(first)
            near(128, Color.getA(firstPixel)); near(64, Color.getR(firstPixel))
            near(128, Color.getG(firstPixel)); near(192, Color.getB(firstPixel))
            near(192, Color.getA(pixel(second))); near(192, Color.getR(pixel(second)))
            repeat(20) { index ->
                factory.createRenderEffect(tint.effect().withValue(color.key, if (index % 2 == 0) 0xFF00FF00L else 0xFF0000FFL), 16f, 16f).getOrThrow()
            }
            assertEquals(firstPixel, pixel(first))
        }
    }

    @Test
    fun sessionChangesReplaceStructuralColorsAndKeepTwoInstancesIndependent() {
        ShaderFactory.create().use { factory ->
            val initial = tint.animatedEffect(isAnimating = false)
            factory.createAnimatedSession(initial, 16f, 16f).getOrThrow().use { first ->
                factory.createAnimatedSession(initial.withValue(color.key, 0xFF0000FFL), 16f, 16f).getOrThrow().use { second ->
                    repeat(20) { index ->
                        val argb = if (index % 2 == 0) 0xFFFF0000L else 0xFF00FF00L
                        val rendered = first.refresh(initial.withValue(color.key, argb).withTime(index.toFloat()), 16f, 16f).getOrThrow()
                        assertEquals(argb.toInt(), pixel(rendered))
                        assertEquals(Color.BLUE, pixel(second.currentRenderEffect))
                    }
                }
            }
        }
    }

    @Test
    fun sourceAndSizeReplacementUseNewProgramAndPixels() {
        val red = solid("same_id", "half4(1.0, 0.0, 0.0, 1.0)").animatedEffect()
        val blue = solid("same_id", "half4(0.0, 0.0, 1.0, 1.0)").animatedEffect()
        val size = ShaderDefinition("size", "Size", ShaderProgram("""
            uniform shader content;
            uniform float2 target;
            half4 main(float2 p) { return half4(target.x / 32.0, 0.0, 0.0, 1.0) * content.eval(p).a; }
        """.trimIndent(), listOf(ShaderBinding.resolution("target")))).animatedEffect()
        ShaderFactory.create(2).use { factory ->
            factory.createAnimatedSession(red, 16f, 16f).getOrThrow().use { session ->
                assertEquals(Color.RED, pixel(session.currentRenderEffect))
                assertEquals(Color.BLUE, pixel(session.refresh(blue, 16f, 16f).getOrThrow()))
                near(128, Color.getR(pixel(session.refresh(size, 16f, 16f).getOrThrow())))
                near(255, Color.getR(pixel(session.refresh(size, 32f, 32f).getOrThrow())))
            }
        }
    }

    @Test
    fun failedReplacementRetainsWorkingSessionAndDisposalIsIdempotent() {
        val good = solid("replacement", "half4(1.0, 0.0, 0.0, 1.0)").animatedEffect()
        val broken = ShaderDefinition("replacement", "Broken", ShaderProgram("uniform shader content; invalid source", emptyList())).animatedEffect()
        ShaderFactory.create(1).use { factory ->
            repeat(12) {
                val session = factory.createAnimatedSession(good, 16f, 16f).getOrThrow()
                val failure = assertIs<ShaderResult.Failure<*>>(session.refresh(broken, 16f, 16f))
                val diagnostic = assertIs<ShaderError.CompilationError>(failure.error)
                assertTrue(diagnostic.message.contains("replacement"))
                assertTrue(diagnostic.message.contains("error", ignoreCase = true), diagnostic.message)
                assertEquals("uniform shader content; invalid source", diagnostic.shaderSource)
                assertEquals(Color.RED, pixel(session.currentRenderEffect))
                assertEquals(Color.RED, pixel(session.refresh(good.withTime(2f), 16f, 16f).getOrThrow()))
                session.close(); session.close()
                assertIs<ShaderResult.Failure<*>>(session.refresh(good, 16f, 16f))
                factory.clearCache()
            }
        }
    }

    @Test
    fun separateFactoriesDoNotShareOwnedNativeState() {
        ShaderFactory.create().use { first ->
            ShaderFactory.create().use { second ->
                val effect = tint.animatedEffect()
                first.createAnimatedSession(effect, 16f, 16f).getOrThrow().use { a ->
                    second.createAnimatedSession(effect.withValue(color.key, 0xFF00FF00L), 16f, 16f).getOrThrow().use { b ->
                        a.close()
                        first.clearCache()
                        assertEquals(Color.GREEN, pixel(b.refresh(effect.withValue(color.key, 0xFF00FF00L).withTime(3f), 16f, 16f).getOrThrow()))
                    }
                }
            }
        }
    }

    @Test
    fun providersIdentifyBindingStageAndFailureBeforeAnyPartialFrameAssignment() {
        var mode = 0
        val provider = ShaderFrameProvider {
            when (mode) {
                1 -> error("sensor disconnected")
                2 -> listOf(ColorUniform("live", 0xFFFF0000L))
                3 -> listOf(FloatUniform("live", .75f), FloatUniform("rogue", 1f))
                else -> listOf(FloatUniform("live", if (mode == 4) .75f else .25f))
            }
        }
        val definition = ShaderDefinition("live_diagnostic", "Live diagnostic", ShaderProgram("""
            uniform shader content;
            uniform float live;
            half4 main(float2 p) { return half4(live, 0.0, 0.0, 1.0) * content.eval(p).a; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live"))))
        val effect = definition.animatedEffect(frameProvider = provider)
        ShaderFactory.create().use { factory ->
            factory.createAnimatedSession(effect, 16f, 16f).getOrThrow().use { session ->
                val original = pixel(session.currentRenderEffect)
                near(64, Color.getR(original))
                for ((scenario, binding, stage) in listOf(
                    Triple(1, "<frame provider>", "provider execution"),
                    Triple(2, "live", "frame validation"),
                    Triple(3, "rogue", "frame validation"),
                )) {
                    mode = scenario
                    val failure = assertIs<ShaderResult.Failure<*>>(session.refresh(effect.withTime(1f), 16f, 16f))
                    val error = assertIs<ShaderError.Unknown>(failure.error)
                    assertTrue(error.message.contains("live_diagnostic"), error.message)
                    assertTrue(error.message.contains(binding), error.message)
                    assertTrue(error.message.contains(stage), error.message)
                    assertTrue(error.cause != null)
                    assertEquals(original, pixel(session.currentRenderEffect), "Invalid batch must not partly apply live=.75")
                }
                mode = 4
                near(191, Color.getR(pixel(session.refresh(effect.withTime(2f), 16f, 16f).getOrThrow())))
            }
        }
    }

    @Test
    fun missingDeclaredImageFailsWhileUnknownSourceUniformRecordsSkikoValidationLimit() {
        val missingImage = ShaderDefinition("missing_image", "Missing image", ShaderProgram("""
            uniform shader content;
            uniform shader mask;
            half4 main(float2 p) { return mask.eval(p) * content.eval(p).a; }
        """.trimIndent(), listOf(ShaderBinding.frameImage("mask"))))
            .animatedEffect(frameProvider = ShaderFrameProvider { emptyList() })
        val unknown = ShaderDefinition("unknown_uniform", "Unknown uniform", ShaderProgram("""
            uniform shader content;
            half4 main(float2 p) { return content.eval(p); }
        """.trimIndent(), listOf(ShaderBinding.float("absent", .5f)))).effect()
        ShaderFactory.create().use { factory ->
            val missing = assertIs<ShaderError.Unknown>(assertIs<ShaderResult.Failure<*>>(
                factory.createAnimatedSession(missingImage, 16f, 16f)).error)
            assertTrue(missing.message.contains("missing_image"))
            assertTrue(missing.message.contains("mask"))
            assertTrue(missing.message.contains("missing frame binding"))
            // Resolved Skiko 0.9.37.4 exposes no compiled uniform metadata and silently ignores
            // this unknown setter name. Do not invent reflection or a diagnostic it cannot give.
            val accepted = factory.createRenderEffect(unknown, 16f, 16f).getOrThrow()
            assertEquals(Color.WHITE, pixel(accepted))
        }
    }

    @Test
    fun replacementOwnsExactlyOneDelegateAndClosesOnlyAfterSuccessfulCreation() {
        ShaderFactory.create().use { factory ->
            val effect = tint.animatedEffect()
            val filter = factory.createRenderEffect(tint.effect(), 16f, 16f).getOrThrow()
            class CountedSession : ShaderSession {
                var closeCount = 0
                override val currentRenderEffect: RenderEffect = filter
                override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> = ShaderResult.success(filter)
                override fun close() { closeCount++ }
            }
            val original = CountedSession()
            val replacement = CountedSession()
            var canReplace = false
            val session = DefinitionShaderSession(effect, 16f, 16f, original) { _, _, _ ->
                if (canReplace) ShaderResult.success(replacement)
                else ShaderResult.failure(ShaderError.Unknown("Replacement unavailable"))
            }
            val updated = effect.withValue(color.key, 0xFF00FF00L)
            assertIs<ShaderResult.Failure<*>>(session.refresh(updated, 16f, 16f))
            assertEquals(0, original.closeCount)
            canReplace = true
            session.refresh(updated, 16f, 16f).getOrThrow()
            assertEquals(1, original.closeCount)
            assertEquals(0, replacement.closeCount)
            session.close(); session.close()
            assertEquals(1, replacement.closeCount)
            assertIs<ShaderResult.Failure<*>>(session.refresh(updated, 16f, 16f))
            assertEquals(1, original.closeCount)
        }
    }

    private fun solid(id: String, expression: String): ShaderDefinition = ShaderDefinition(id, id, ShaderProgram("""
        uniform shader content;
        half4 main(float2 p) { return $expression * content.eval(p).a; }
    """.trimIndent(), emptyList()))

    private fun near(expected: Int, actual: Int) = assertTrue(abs(expected - actual) <= 3, "Expected $expected, got $actual")

    private fun pixel(filter: RenderEffect): Int = Surface.makeRasterN32Premul(16, 16).use { surface ->
        Paint().use { layer ->
            layer.imageFilter = filter.asSkiaImageFilter()
            surface.canvas.saveLayer(Rect.makeWH(16f, 16f), layer)
            surface.canvas.clear(Color.WHITE)
            surface.canvas.restore()
        }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { it.getColor(8, 8) } }
    }
}
