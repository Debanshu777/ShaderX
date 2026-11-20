package com.debanshu.shaderlab.shaderx

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportReason
import com.debanshu.shaderlab.shaderx.factory.ShaderFallbackPolicy
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.shaderx.factory.createRenderEffectWithFallback
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Actual Android HWUI/AGSL pixels. An emulator result is not physical-GPU evidence. */
class AndroidShaderPixelTest {
    @Test
    fun hardwareReadbackControl_rendersSolidInput() {
        PixelTarget(16, 16).use { target -> assertEquals(Color.RED, target.pixel(null, Color.RED)) }
    }

    @Test
    fun builtInsAndChains_renderExpectedPixels() {
        ShaderFactory.create().use { factory ->
            PixelTarget(16, 16).use { target ->
                val grayscale = success(factory.createRenderEffect(GrayscaleEffect(), 16f, 16f))
                val gray = target.pixel(grayscale, Color.RED)
                channelNear(76, Color.red(gray))
                channelNear(Color.red(gray), Color.green(gray))
                channelNear(Color.red(gray), Color.blue(gray))
                val chain = success(factory.createRenderEffect(CompositeEffect.of(GrayscaleEffect(), InvertEffect()), 16f, 16f))
                val inverted = target.pixel(chain, Color.RED)
                channelNear(179, Color.red(inverted))
                channelNear(Color.red(inverted), Color.green(inverted))
            }
        }
    }

    @Test
    fun cachedSourceAndAnimatedInstances_keepUniformsIndependent() {
        ShaderFactory.create(2).use { factory ->
            PixelTarget(16, 16).use { target ->
                val low = success(factory.createRenderEffect(LevelEffect(0.25f), 16f, 16f))
                val high = success(factory.createRenderEffect(LevelEffect(0.75f), 16f, 16f))
                channelNear(64, Color.red(target.pixel(low)))
                channelNear(191, Color.red(target.pixel(high)))
                success(factory.createAnimatedSession(LevelEffect(0.25f), 16f, 16f)).use { first ->
                    success(factory.createAnimatedSession(LevelEffect(0.75f), 16f, 16f)).use { second ->
                        repeat(20) { index ->
                            val value = (index % 4) / 4f
                            val updated = success(first.refresh(LevelEffect(value), 16f, 16f))
                            channelNear((value * 255).toInt(), Color.red(target.pixel(updated)))
                            channelNear(191, Color.red(target.pixel(second.currentRenderEffect)))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun layoutColor_translucentValuesAndRepeatedUpdates_renderUnpremultipliedInput() {
        ShaderFactory.create().use { factory ->
            PixelTarget(16, 16).use { target ->
                repeat(20) { index ->
                    val red = if (index % 2 == 0) 0.8f else 0.2f
                    val effect = ColorEffect(ColorUniform("tint", red, 0.4f, 0.2f, 0.5f))
                    val pixel = target.pixel(success(factory.createRenderEffect(effect, 16f, 16f)))
                    channelNear(128, Color.alpha(pixel))
                    channelNear((red * 255).toInt(), Color.red(pixel), 3)
                    channelNear(102, Color.green(pixel), 3)
                    val translucent = target.pixel(success(factory.createRenderEffect(effect, 16f, 16f)), 0x80FFFFFF.toInt())
                    channelNear(64, Color.alpha(translucent))
                    channelNear((red * 255).toInt(), Color.red(translucent), 5)
                }
            }
        }
    }

    @Test
    fun strictPilotEditsMatchLegacyPixelsWithoutChangingAlphaOrTime() {
        ShaderFactory.create().use { factory ->
            PixelTarget(16, 16).use { target ->
                val original = GradientEffect()
                val typed = GradientEffect.SCHEMA.valuesFrom(original)
                    .withValue(GradientEffect.COLOR_1.key, 0x804080C0L)
                    .withValue(GradientEffect.COLOR_2.key, 0x804080C0L)
                    .withValue(GradientEffect.INTENSITY.key, 1f)
                    .applyTo(original) as GradientEffect
                val legacy = original.copy(color1 = 0x804080C0L, color2 = 0x804080C0L, intensity = 1f)
                val pixel = target.pixel(success(factory.createRenderEffect(typed, 16f, 16f)))
                assertEquals(target.pixel(success(factory.createRenderEffect(legacy, 16f, 16f))), pixel)
                channelNear(64, Color.red(pixel))
                channelNear(128, Color.green(pixel))
                channelNear(192, Color.blue(pixel))
                // Legacy Gradient preserves content alpha, not gradient-color alpha.
                channelNear(255, Color.alpha(pixel))
                assertEquals(0x804080C0L, GradientEffect.SCHEMA.valuesFrom(typed)[GradientEffect.COLOR_1.key])

                val wave = WaveEffect(amplitude = 0f, animate = false, time = .75f)
                val edited = WaveEffect.SCHEMA.valuesFrom(wave)
                    .withValue(WaveEffect.AMPLITUDE.key, 13f).applyTo(wave) as WaveEffect
                assertEquals(.75f, edited.time)
                assertEquals(false, edited.animate)
                val before = target.pixel(success(factory.createRenderEffect(wave, 16f, 16f)), patterned = true)
                val after = target.pixel(success(factory.createRenderEffect(edited, 16f, 16f)), patterned = true)
                assertTrue(before != after, "Typed wave update must move the patterned input")
                assertEquals(target.pixel(success(factory.createRenderEffect(wave.withParameter("amplitude", 13f), 16f, 16f)), patterned = true), after)
            }
        }
    }

    @Test
    fun texturesResizeErrorsAndRepeatedDisposal_keepRenderingValid() {
        val texture = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            ShaderFactory.create(2).use { factory ->
                repeat(12) { iteration ->
                    val size = if (iteration % 2 == 0) 8 else 32
                    PixelTarget(size, size).use { target ->
                        val effect = SimpleEffect("""
                            uniform shader content;
                            uniform shader extra;
                            uniform float2 resolution;
                            half4 main(float2 p) { return extra.eval(p / resolution * 2.0) * content.eval(p).a; }
                            // Distinct sources force eviction: $iteration
                        """.trimIndent(), listOf(TextureUniform("extra", texture.asImageBitmap()), FloatUniform("resolution", size.toFloat(), size.toFloat())))
                        channelNear(255, Color.blue(target.pixel(success(factory.createRenderEffect(effect, size.toFloat(), size.toFloat())))))
                        assertTrue(factory.cacheSize <= 2)
                        val broken = factory.createRenderEffect(SimpleEffect("invalid shader", emptyList()), size.toFloat(), size.toFloat())
                        assertTrue(broken is ShaderResult.Failure)
                        if (iteration % 3 == 0) factory.clearCache()
                        channelNear(64, Color.red(target.pixel(success(factory.createRenderEffect(LevelEffect(0.25f), size.toFloat(), size.toFloat())))))
                    }
                }
            }
        } finally {
            texture.recycle()
        }
    }

    @Test
    fun capabilitiesAndExplicitFallback_agreeWithHardwareRendering() {
        ShaderFactory.create().use { factory ->
            ShaderX.builtInEffects().forEach { effect ->
                assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, effect).status)
                assertEquals(ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE, factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect).reason)
            }
            val chain = CompositeEffect.of(GrayscaleEffect(), InvertEffect())
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, chain).status)
            assertEquals(ShaderSupportStatus.SUPPORTED,
                factory.querySupport(ShaderOperation.ANIMATED_SESSION, CompositeEffect.of(WaveEffect())).status)
            val invalid = SimpleEffect("invalid shader", emptyList())
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, invalid).status)
            assertEquals(0, factory.cacheSize) // Queries neither compile nor populate cache.
            PixelTarget(16, 16).use { target ->
                val preserved = factory.createRenderEffectWithFallback(invalid, 16f, 16f)
                assertTrue(preserved.fallbackUsed)
                assertTrue(preserved.cause is ShaderError.CompilationError)
                assertEquals(Color.RED, target.pixel(preserved.result.getOrThrow(), Color.RED))
                val failed = factory.createRenderEffectWithFallback(invalid, 16f, 16f, ShaderFallbackPolicy.Fail)
                assertTrue(failed.result is ShaderResult.Failure)
                assertTrue(!failed.fallbackUsed)
                val substitute = factory.createRenderEffectWithFallback(invalid, 16f, 16f,
                    ShaderFallbackPolicy.Substitute(InvertEffect()))
                assertTrue(substitute.fallbackUsed)
                assertTrue(substitute.cause is ShaderError.CompilationError)
                val pixel = target.pixel(success(substitute.result), Color.RED)
                channelNear(0, Color.red(pixel))
                channelNear(255, Color.green(pixel))
                channelNear(255, Color.blue(pixel))
                val rendered = target.pixel(success(factory.createRenderEffect(chain, 16f, 16f)), Color.RED)
                channelNear(179, Color.red(rendered))
            }
        }
    }

    @Test
    fun portablePipeline_noncommutativeOrderAndSharedClock_renderHardwarePixels() {
        ShaderFactory.create(1).use { factory ->
            val multiply = SimpleEffect("""
                uniform shader content;
                half4 main(float2 p) { half4 c = content.eval(p); return half4(c.rgb * 0.5, c.a); }
            """.trimIndent(), emptyList())
            val add = SimpleEffect("""
                uniform shader content;
                half4 main(float2 p) { half4 c = content.eval(p); return half4(c.rgb + 0.25 * c.a, c.a); }
            """.trimIndent(), emptyList())
            PixelTarget(16, 16).use { target ->
                val ab = success(factory.createRenderEffect(CompositeEffect.of(multiply, add), 16f, 16f))
                val ba = success(factory.createRenderEffect(CompositeEffect.of(add, multiply), 16f, 16f))
                val grayInput = Color.rgb(128, 128, 128)
                channelNear(128, Color.red(target.pixel(ab, grayInput)))
                channelNear(96, Color.red(target.pixel(ba, grayInput)))
                assertTrue(target.pixel(ab, grayInput) != target.pixel(ba, grayInput), "Pipeline order must affect pixels")
                fun clock(channel: String) = ShaderDefinition("android_pipeline_$channel", program = ShaderProgram("""
                    uniform shader content; uniform float time;
                    half4 main(float2 p) {
                        half4 c = content.eval(p);
                        return ${if (channel == "r") "half4(time * c.a, c.g, c.b, c.a)" else "half4(c.r, time * c.a, c.b, c.a)"};
                    }
                """.trimIndent(), listOf(ShaderBinding.time()))).animatedEffect()
                val chain = CompositeEffect.of(clock("r"), clock("g"))
                success(factory.createPipelineSession(chain, 16f, 16f, .25f)).use { shared ->
                    success(factory.createPipelineSession(chain, 16f, 16f, .75f)).use { independent ->
                        val quarter = target.pixel(shared.currentRenderEffect)
                        channelNear(64, Color.red(quarter)); channelNear(64, Color.green(quarter))
                        val updated = target.pixel(success(shared.refresh(chain, 16f, 16f, .5f)))
                        channelNear(128, Color.red(updated)); channelNear(128, Color.green(updated))
                        val other = target.pixel(independent.currentRenderEffect)
                        channelNear(191, Color.red(other)); channelNear(191, Color.green(other))
                        factory.clearCache()
                        channelNear(191, Color.red(target.pixel(independent.currentRenderEffect)))
                    }
                }
            }
        }
    }

    @Test
    fun pipelineStructuralColorsResizeAndFailedReplacement_preserveSuccessfulWholeSnapshot() {
        ShaderFactory.create(1).use { factory ->
            fun tint(red: Float) = ShaderDefinition("pipeline_tint", program = ShaderProgram("""
                uniform shader content; uniform float time; layout(color) uniform half4 tint;
                half4 main(float2 p) { return half4(tint.rgb * time * tint.a, tint.a) * content.eval(p).a; }
            """.trimIndent(), listOf(ShaderBinding.time(), ShaderBinding.uniform(ColorUniform("tint", red, .4f, .2f, .5f))))).animatedEffect()
            val spatial = ShaderDefinition("pipeline_resize", program = ShaderProgram("""
                uniform shader content; uniform float2 resolution;
                half4 main(float2 p) { half4 c = content.eval(p); return half4(c.r, p.x / resolution.x * c.a, c.b, c.a); }
            """.trimIndent(), listOf(ShaderBinding.resolution()))).effect()
            val original = CompositeEffect.of(tint(.8f), spatial)
            PixelTarget(16, 16).use { target ->
                success(factory.createPipelineSession(original, 16f, 16f, .25f)).use { session ->
                    val retained = session.currentRenderEffect
                    val first = target.pixel(retained)
                    channelNear(51, Color.red(first), 4); channelNear(128, Color.alpha(first))
                    repeat(8) { index ->
                        val red = if (index % 2 == 0) .2f else .8f
                        val chain = CompositeEffect.of(tint(red), spatial)
                        val resized = success(session.refresh(chain, 32f, 32f, .5f))
                        val pixel = target.pixel(resized)
                        channelNear((red * .5f * 255).toInt(), Color.red(pixel), 4)
                        channelNear(68, Color.green(pixel), 4)
                        channelNear(128, Color.alpha(pixel))
                        assertEquals(first, target.pixel(retained), "Fresh color snapshots cannot mutate older filters")
                    }
                    val last = session.currentRenderEffect
                    val bad = CompositeEffect.of(tint(.1f), SimpleEffect("invalid late shader", emptyList()))
                    assertTrue(session.refresh(bad, 16f, 16f, .75f) is ShaderResult.Failure)
                    assertTrue(last === session.currentRenderEffect)
                    assertEquals(target.pixel(last), target.pixel(session.currentRenderEffect))
                    val restored = target.pixel(success(session.refresh(original, 16f, 16f, .25f)))
                    assertEquals(first, restored)
                }
            }
        }
    }

    @Test
    fun staticColorChildrenSameSourceRemainIndependentWhenLaterLiveProviderFails() {
        ShaderFactory.create(1).use { factory ->
            fun tint(red: Float) = ShaderDefinition("pipeline_static_tint", program = ShaderProgram("""
                uniform shader content; layout(color) uniform half4 tint;
                half4 main(float2 p) { return half4(tint.rgb * tint.a, tint.a) * content.eval(p).a; }
            """.trimIndent(), listOf(ShaderBinding.uniform(ColorUniform("tint", red, .4f, .2f, .5f))))).effect()
            var fail = false
            val live = ShaderDefinition("pipeline_static_later", program = ShaderProgram("""
                uniform shader content; uniform float live;
                half4 main(float2 p) { return content.eval(p) * live; }
            """.trimIndent(), listOf(ShaderBinding.frameFloat("live")))).animatedEffect(frameProvider = ShaderFrameProvider {
                check(!fail) { "Later provider failed" }; listOf(FloatUniform("live", .5f))
            })
            val firstChain = CompositeEffect.of(tint(.8f), live)
            val secondChain = CompositeEffect.of(tint(.2f), live)
            PixelTarget(16, 16).use { target ->
                success(factory.createPipelineSession(firstChain, 16f, 16f)).use { first ->
                    success(factory.createPipelineSession(secondChain, 16f, 16f)).use { second ->
                        val retained = first.currentRenderEffect
                        repeat(8) {
                            channelNear(204, Color.red(target.pixel(retained)), 5)
                            channelNear(51, Color.red(target.pixel(second.currentRenderEffect)), 5)
                            channelNear(64, Color.alpha(target.pixel(retained)), 3)
                            success(second.refresh(secondChain, 16f, 16f, it / 10f))
                        }
                        fail = true
                        assertTrue(first.refresh(secondChain, 32f, 32f, .75f) is ShaderResult.Failure)
                        assertTrue(retained === first.currentRenderEffect)
                        channelNear(204, Color.red(target.pixel(first.currentRenderEffect)), 5)
                        fail = false
                        val replaced = success(first.refresh(secondChain, 16f, 16f, .75f))
                        channelNear(51, Color.red(target.pixel(replaced)), 5)
                        channelNear(204, Color.red(target.pixel(retained)), 5)
                    }
                }
            }
        }
    }

    private fun <T> success(result: ShaderResult<T>): T = result.getOrNull() ?: error("Rendering failed: $result")

    private fun channelNear(expected: Int, actual: Int, tolerance: Int = 2) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected channel $expected +/- $tolerance, got $actual")
    }

    private open class SimpleEffect(override val shaderSource: String, private val uniforms: List<Uniform>) : RuntimeShaderEffect {
        override val id = "android_pixel_test"
        override val displayName = "Android pixel test"
        override val parameters: List<ParameterSpec> = emptyList()
        override fun withTypedParameter(parameterId: String, value: ParameterValue): RuntimeShaderEffect = this
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = uniforms
    }

    private class ColorEffect(color: ColorUniform) : SimpleEffect("""
        uniform shader content;
        layout(color) uniform half4 tint;
        half4 main(float2 p) { return half4(tint.rgb * tint.a, tint.a) * content.eval(p).a; }
    """.trimIndent(), listOf(color))

    private class LevelEffect(private val level: Float, override val time: Float = 0f) : AnimatedShaderEffect {
        override val id = "android_level"
        override val displayName = "Android level"
        override val parameters: List<ParameterSpec> = emptyList()
        override val isAnimating = true
        override val shaderSource = """
            uniform shader content;
            uniform float level;
            half4 main(float2 p) { return half4(level, 0.0, 0.0, 1.0) * content.eval(p).a; }
        """.trimIndent()
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(FloatUniform("level", level))
        override fun buildFrameUniforms(): List<Uniform> = listOf(FloatUniform("level", level))
        override fun withTime(newTime: Float): AnimatedShaderEffect = LevelEffect(level, newTime)
        override fun withTypedParameter(parameterId: String, value: ParameterValue): AnimatedShaderEffect = this
        override fun equals(other: Any?): Boolean = other is LevelEffect && level == other.level
        override fun hashCode(): Int = level.hashCode()
    }

    /** Based on Android's HardwareRenderer/ImageReader migration recipe; owns every native wrapper. */
    internal class PixelTarget(private val width: Int, private val height: Int) : AutoCloseable {
        private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        private val node = RenderNode("ShaderX pixels").apply { setPosition(0, 0, this@PixelTarget.width, this@PixelTarget.height) }
        private val root = RenderNode("ShaderX root").apply { setPosition(0, 0, this@PixelTarget.width, this@PixelTarget.height) }
        private val renderer = HardwareRenderer().apply {
            setName("ShaderX validation")
            setOpaque(false)
            setSurface(reader.surface)
            setContentRoot(root)
        }

        init {
            Log.i("ShaderXValidation", "HWUI ${Build.MANUFACTURER}/${Build.MODEL}, API ${Build.VERSION.SDK_INT}, ${Build.FINGERPRINT}; viewport=${width}x$height; RGBA8888 sRGB; drawingEnabled=${HardwareRenderer.isDrawingEnabled()}; debug instrumentation")
        }

        fun pixel(effect: RenderEffect?, input: Int = Color.WHITE, patterned: Boolean = false): Int {
            node.setRenderEffect(effect?.asAndroidRenderEffect())
            node.beginRecording().apply {
                drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = input })
                if (patterned) {
                    drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
                    drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
                }
            }
            node.endRecording()
            root.beginRecording().drawRenderNode(node)
            root.endRecording()
            var status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            // HWUI may decline a second request in the same vsync. Wait for a new frame slot;
            // pixel assertions are never retried and surface/context failures still fail below.
            var droppedFrames = 0
            while (status and HardwareRenderer.SYNC_FRAME_DROPPED != 0 && droppedFrames++ < 3) {
                Thread.sleep(20)
                status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            }
            if (droppedFrames > 0) Log.i("ShaderXValidation", "HWUI frame scheduling resubmissions=$droppedFrames, status=$status")
            assertEquals(0, status and (HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or HardwareRenderer.SYNC_FRAME_DROPPED), "HardwareRenderer failed: $status")
            val image = reader.acquireNextImage() ?: error("HardwareRenderer produced no image")
            return image.use {
                if (Build.VERSION.SDK_INT >= 33) image.fence.use { fence ->
                    if (fence.isValid) check(fence.await(java.time.Duration.ofSeconds(5))) { "GPU fence timeout" }
                }
                val buffer = image.hardwareBuffer ?: error("Missing hardware buffer")
                buffer.use {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, null) ?: error("Cannot wrap hardware buffer")
                    try {
                        val readable = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: error("Cannot read hardware pixels")
                        try { readable.getPixel(width / 2, height / 2) } finally { readable.recycle() }
                    } finally { hardware.recycle() }
                }
            }
        }

        override fun close() {
            renderer.destroy()
            node.discardDisplayList()
            root.discardDisplayList()
            reader.close()
        }
    }
}
