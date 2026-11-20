package com.debanshu.shaderlab.shaderx

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Paint
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.os.Build
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.compose.ControlledSessionOwner
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** HardwareBufferRenderer readback of real AGSL in explicit destination spaces (API 34+). */
class DefinedEffectDeviceTest {
    private val color = ColorParameterDefinition("defined_device_tint", "color", 0x804080C0L)
    private val tint = ShaderDefinition("defined_device_tint", "Tint", ShaderProgram("""
        uniform shader content;
        layout(color) uniform half4 tint;
        half4 main(float2 p) { return half4(tint.rgb * tint.a, tint.a) * content.eval(p).a; }
    """.trimIndent(), listOf(ShaderBinding.color("tint", color.key))), ParameterSchema("defined_device_tint", listOf(color)))

    @Test
    fun generatedColorsAndSessionsRemainIndependentAcrossReplacementAndDisposal() {
        target(ColorSpace.Named.SRGB).use { target ->
            val control = target.color(null)
            near(1f, control.red()); near(1f, control.alpha())
            ShaderFactory.create(2).use { factory ->
                val first = factory.createRenderEffect(tint.effect().withValue(color.key, 0xFFFF0000L), 16f, 16f).getOrThrow()
                val second = factory.createRenderEffect(tint.effect().withValue(color.key, 0xFF0000FFL), 16f, 16f).getOrThrow()
                near(1f, target.color(first).red()); near(1f, target.color(second).blue())
                near(1f, target.color(first).red())
                val initial = tint.animatedEffect(isAnimating = false)
                repeat(12) {
                    factory.createAnimatedSession(initial, 16f, 16f).getOrThrow().use { a ->
                        factory.createAnimatedSession(initial.withValue(color.key, 0xFF0000FFL), 16f, 16f).getOrThrow().use { b ->
                            repeat(8) { index ->
                                val argb = if (index % 2 == 0) 0xFFFF0000L else 0xFF00FF00L
                                val updated = a.refresh(initial.withValue(color.key, argb).withTime(index.toFloat()), 16f, 16f).getOrThrow()
                                val actual = target.color(updated)
                                near(if (index % 2 == 0) 1f else 0f, actual.red())
                                near(if (index % 2 == 0) 0f else 1f, actual.green())
                                near(1f, target.color(b.currentRenderEffect).blue())
                            }
                            val replacement = ShaderDefinition("defined_device_tint", "Other source", ShaderProgram("""
                                uniform shader content;
                                uniform float2 extent;
                                half4 main(float2 p) { return half4(extent.x / 32.0, 0.0, 0.0, 1.0) * content.eval(p).a; }
                            """.trimIndent(), listOf(ShaderBinding.resolution("extent")))).animatedEffect()
                            near(.5f, target.color(a.refresh(replacement, 16f, 16f).getOrThrow()).red())
                            near(1f, target.color(a.refresh(replacement, 32f, 32f).getOrThrow()).red())
                            val broken = ShaderDefinition("broken_device", "Broken", ShaderProgram("uniform shader content; invalid source", emptyList())).animatedEffect()
                            val failure = assertIs<ShaderResult.Failure<*>>(a.refresh(broken, 16f, 16f))
                            assertIs<ShaderError.CompilationError>(failure.error)
                            assertTrue(failure.error.message.contains("broken_device"))
                            assertTrue(failure.error.message.contains("compile/bind failed"), "Keep the native compiler failure case distinct from reserved-child preflight")
                            assertEquals(broken.shaderSource, (failure.error as ShaderError.CompilationError).shaderSource)
                            near(1f, target.color(a.currentRenderEffect).red())
                            a.close(); a.close()
                            assertIs<ShaderResult.Failure<*>>(a.refresh(initial, 16f, 16f))
                        }
                    }
                    factory.clearCache()
                }
            }
        }
    }

    /** Real AGSL owner/session pixels; this RenderNode harness does not prove Compose invalidation. */
    @Test
    fun controlledTimelineAndLiveSnapshotsSurviveStructuralAndFactoryReplacement() {
        val color = ColorParameterDefinition("controlled_device", "color", 0xFFFF0000L)
        val schema = ParameterSchema("controlled_device", listOf(color))
        val definition = ShaderDefinition("controlled_device", program = ShaderProgram("""
            uniform shader content;
            uniform float time;
            uniform float live;
            uniform float2 extent;
            layout(color) uniform half4 tint;
            half4 main(float2 p) {
                return half4(time + live, tint.g, extent.x / 64.0, 1.0) * content.eval(p).a;
            }
        """.trimIndent(), listOf(ShaderBinding.time(), ShaderBinding.frameFloat("live"),
            ShaderBinding.resolution("extent"), ShaderBinding.color("tint", color.key))), schema = schema)
        var live = .1f
        val provider = ShaderFrameProvider { listOf(FloatUniform("live", live)) }
        var effect = definition.animatedEffect(frameProvider = provider)
        val playback = ShaderPlayback(initialPositionSeconds = .2, initiallyPlaying = false)
        val otherPlayback = ShaderPlayback(initialPositionSeconds = .6, initiallyPlaying = false)
        target(ColorSpace.Named.SRGB).use { target ->
            ShaderFactory.create().use { first ->
                ShaderFactory.create().use { second ->
                    ControlledSessionOwner().use { owner ->
                        ControlledSessionOwner().use { independent ->
                            var filter = owner.refresh(first, effect.withTime(playback.positionSeconds.toFloat()), IntSize(16, 16)).getOrThrow()
                            near(.3f, target.color(filter).red())
                            near(.25f, target.color(filter).blue())
                            val other = independent.refresh(first, effect.withTime(otherPlayback.positionSeconds.toFloat()), IntSize(16, 16)).getOrThrow()
                            near(.7f, target.color(other).red())
                            // No owner refresh occurs until the host publishes its next revision.
                            live = .3f
                            near(.3f, target.color(filter).red())
                            filter = owner.refresh(first, effect.withTime(playback.positionSeconds.toFloat()), IntSize(16, 16)).getOrThrow()
                            near(.5f, target.color(filter).red())
                            near(.7f, target.color(other).red())
                            repeat(8) { index ->
                                effect = effect.withValue(color.key, if (index % 2 == 0) 0xFF00FF00L else 0xFFFF0000L)
                                filter = owner.refresh(first, effect.withTime(playback.positionSeconds.toFloat()), IntSize(32, 32)).getOrThrow()
                                val rendered = target.color(filter)
                                near(.5f, rendered.red())
                                near(if (index % 2 == 0) 1f else 0f, rendered.green())
                                near(.5f, rendered.blue())
                                assertEquals(.2, playback.positionSeconds)
                            }
                            val broken = ShaderDefinition("broken_controlled", program = ShaderProgram("uniform shader content; invalid source")).animatedEffect()
                            val brokenResult = assertIs<ShaderResult.Failure<*>>(owner.refresh(first, broken.withTime(playback.positionSeconds.toFloat()), IntSize(32, 32)))
                            val brokenError = assertIs<ShaderError.CompilationError>(brokenResult.error)
                            assertTrue(brokenError.message.contains("compile/bind failed"), "Explicit content plus an invalid body still reaches native compilation")
                            assertEquals(broken.shaderSource, brokenError.shaderSource)
                            near(.5f, target.color(filter).red())
                            val unavailable = object : ShaderFactory by second {
                                override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
                                    ShaderResult.failure(ShaderError.Unknown("Temporary replacement factory failure"))
                            }
                            assertIs<ShaderResult.Failure<*>>(owner.refresh(unavailable, effect.withTime(playback.positionSeconds.toFloat()), IntSize(32, 32)))
                            near(.5f, target.color(filter).red())
                            filter = owner.refresh(second, effect.withTime(playback.positionSeconds.toFloat()), IntSize(32, 32)).getOrThrow()
                            near(.5f, target.color(filter).red())
                            playback.seekTo(.4)
                            filter = owner.refresh(second, effect.withTime(playback.positionSeconds.toFloat()), IntSize(32, 32)).getOrThrow()
                            near(.7f, target.color(filter).red())
                            assertEquals(.4, playback.positionSeconds)
                            assertEquals(.6, otherPlayback.positionSeconds)
                            // Both old native ownership and the failed candidate must be harmless.
                            owner.close(); owner.close()
                        }
                    }
                }
            }
        }
    }

    /** Valid source-free programs must never reach Android's reserved-child native lookup. */
    @Test
    fun sourceFreeContentRoutesRejectWholeGraphBeforeProvidersOrCacheWork() {
        assumeTrue("AGSL route needs API33", Build.VERSION.SDK_INT >= 33)
        val source = "half4 main(float2 p) { return half4(1,0,0,1); }"
        val static = ShaderDefinition("source_free_static_device", program = ShaderProgram(source))
        val liveSource = "uniform float live; half4 main(float2 p) { return half4(live,0,0,1); }"
        val live = ShaderDefinition("source_free_live_device", program = ShaderProgram(liveSource,
            listOf(ShaderBinding.frameFloat("live"))))
        var providerCalls = 0
        val sentinel = ShaderFrameProvider {
            providerCalls++
            error("Content preflight must run before any provider")
        }
        val animated = live.animatedEffect(frameProvider = sentinel)
        // A valid first child's provider/cache must also remain untouched when a later child fails.
        val first = ShaderDefinition("valid_first_device", program = ShaderProgram("""
            uniform shader content;
            uniform float live;
            half4 main(float2 p) { return content.eval(p) * live; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live")))).animatedEffect(frameProvider = sentinel)
        ShaderFactory.create().use { factory ->
            fun rejected(result: ShaderResult<*>, definition: ShaderDefinition) {
                val error = assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
                assertEquals(definition.program.source, error.shaderSource)
                assertTrue(error.message.contains(definition.id))
                assertTrue(error.message.contains("uniform shader content;"))
                assertTrue(error.message.contains("proceduralBackground"))
                assertEquals(0, providerCalls, "Reject the whole graph before resolving a valid first child")
                assertEquals(0, factory.cacheSize, "Reject before adding any native program to the cache")
            }
            rejected(factory.createRenderEffect(static.effect(), 16f, 16f), static)
            rejected(factory.createRenderEffect(animated, 16f, 16f), live)
            rejected(factory.createAnimatedSession(animated, 16f, 16f), live)
            rejected(factory.createRenderEffect(CompositeEffect.of(first, static.effect()), 16f, 16f), static)
            rejected(factory.createPipelineSession(CompositeEffect.of(first, animated), 16f, 16f), live)
            rejected(factory.createPipelineSession(CompositeEffect.of(first, static.effect()), 16f, 16f), static)
            println("SX12 content preflight rejected6 routes; providerCalls=$providerCalls cacheSize=${factory.cacheSize}")
        }
    }

    @Test
    fun layoutColorTransformsSrgbIntoLinearSrgbAndDisplayP3WithTranslucentAlpha() {
        ShaderFactory.create().use { factory ->
            val filter = factory.createRenderEffect(tint.effect(), 16f, 16f).getOrThrow()
            for (named in listOf(ColorSpace.Named.SRGB, ColorSpace.Named.LINEAR_SRGB, ColorSpace.Named.DISPLAY_P3)) {
                target(named).use { target ->
                    val actual = target.color(filter)
                    val expected = ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB), ColorSpace.get(named))
                        .transform(64f / 255, 128f / 255, 192f / 255)
                    near(expected[0], actual.red()); near(expected[1], actual.green()); near(expected[2], actual.blue())
                    near(128f / 255, actual.alpha())
                    // Legacy fromColor copies components. The declarative path accepts packed sRGB;
                    // explicitly convert Compose wide-gamut input before packing it for this binding.
                    val compose = androidx.compose.ui.graphics.Color(.8f, .3f, .2f, .6f, ColorSpaces.DisplayP3)
                        .convert(ColorSpaces.Srgb)
                    val packed = compose.toArgb().toLong() and 0xFFFFFFFFL
                    val convertedFilter = factory.createRenderEffect(tint.effect().withValue(color.key, packed), 16f, 16f).getOrThrow()
                    val converted = target.color(convertedFilter)
                    val expectedConverted = ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB), ColorSpace.get(named))
                        .transform(Color.red(packed.toInt()) / 255f, Color.green(packed.toInt()) / 255f, Color.blue(packed.toInt()) / 255f)
                    near(expectedConverted[0], converted.red()); near(expectedConverted[1], converted.green()); near(expectedConverted[2], converted.blue())
                    near(Color.alpha(packed.toInt()) / 255f, converted.alpha())
                }
            }
        }
    }

    private fun target(space: ColorSpace.Named): Target {
        assumeTrue("HardwareBufferRenderer needs API 34", Build.VERSION.SDK_INT >= 34)
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        assumeTrue("RGBA_FP16 hardware target unavailable", HardwareBuffer.isSupported(16, 16, HardwareBuffer.RGBA_FP16, 1, usage))
        return Target(ColorSpace.get(space))
    }

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) <= .015f, "Expected $expected, got $actual")

    private class Target(private val space: ColorSpace) : AutoCloseable {
        private val buffer = HardwareBuffer.create(16, 16, HardwareBuffer.RGBA_FP16, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        private val node = RenderNode("Defined effect pixels").apply { setPosition(0, 0, 16, 16) }
        private val root = RenderNode("Defined effect root").apply { setPosition(0, 0, 16, 16) }
        private val renderer = HardwareBufferRenderer(buffer).apply { setContentRoot(root) }

        fun color(effect: RenderEffect?): Color {
            node.setRenderEffect(effect?.asAndroidRenderEffect())
            node.beginRecording().apply { drawRect(0f, 0f, 16f, 16f, Paint().apply { color = Color.WHITE }) }
            node.endRecording()
            root.beginRecording().apply {
                drawColor(Color.TRANSPARENT, android.graphics.BlendMode.CLEAR)
                drawRenderNode(node)
            }
            root.endRecording()
            val done = CountDownLatch(1)
            var result: HardwareBufferRenderer.RenderResult? = null
            renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { rendered -> result = rendered; done.countDown() }
            check(done.await(5, TimeUnit.SECONDS)) { "HardwareBufferRenderer callback timeout" }
            val rendered = checkNotNull(result)
            assertEquals(HardwareBufferRenderer.RenderResult.SUCCESS, rendered.status)
            rendered.fence.use { if (it.isValid) check(it.await(Duration.ofSeconds(5))) { "GPU fence timeout" } }
            val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, space))
            try {
                val readable = checkNotNull(hardware.copy(Bitmap.Config.RGBA_F16, false))
                try {
                    // Android may promote F16 readback to extended sRGB/scRGB. Preserve the
                    // readback's declared space and convert numerically; never relabel pixels.
                    return readable.getColor(8, 8).convert(space)
                } finally { readable.recycle() }
            } finally { hardware.recycle() }
        }

        override fun close() {
            renderer.close()
            node.discardDisplayList(); root.discardDisplayList()
            buffer.close()
        }
    }
}
