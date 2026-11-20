package com.debanshu.shaderlab.shaderx

import android.graphics.Color
import android.os.Build
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.uniform.*
import com.debanshu.shaderlab.shaderx.effect.impl.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.result.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Run explicitly on API31/32 as well as current Android; no AGSL test class is initialized. */
class AndroidAdoptionDeviceTest {
    @Test fun nativeBlurAndChainsHaveVisiblePixelsAndIndependentLifetime() {
        ShaderFactory.create().use { factory ->
            assertTrue(factory.isSupported())
            assertEquals(ShaderSupportStatus.SUPPORTED, factory.querySupport(ShaderOperation.CONTENT_RENDER, NativeBlurEffect()).status)
            AndroidShaderPixelTest.PixelTarget(32, 32).use { target ->
                assertEquals(Color.RED, target.pixel(null, Color.RED))
                val original = target.pixel(null, patterned = true)
                val blur = factory.createRenderEffect(NativeBlurEffect(8f), 32f, 32f).getOrThrow()
                val blurred = target.pixel(blur, patterned = true)
                assertNotEquals(original, blurred)
                assertTrue(Color.red(blurred) > 40 && Color.blue(blurred) > 40)
                val order = mutableListOf<Float>()
                fun tracked(amount: Float): BlurEffect = object : BlurEffect by NativeBlurEffect(amount) {
                    override val radius: Float get() { order += amount; return amount }
                }
                val chain = factory.createRenderEffect(CompositeEffect.of(tracked(4f), tracked(8f)), 32f, 32f).getOrThrow()
                assertEquals(listOf(4f, 8f), order)
                factory.clearCache()
                assertTrue(Color.red(target.pixel(chain, patterned = true)) > 40)
                assertEquals(Color.RED, target.pixel(blur, Color.RED))
            }
        }
    }

    @Test fun api31PreflightDoesNotReadRuntimeSourcesProvidersOrPartialNativeValues() {
        assumeTrue(Build.VERSION.SDK_INT in 31..32)
        var sourceCalls = 0
        var uniformCalls = 0
        var frameCalls = 0
        var nativeCalls = 0
        val runtime = object : AnimatedShaderEffect by WaveEffect() {
            override val shaderSource: String get() { sourceCalls++; error("must not compile") }
            override fun buildUniforms(width: Float, height: Float): List<Uniform> { uniformCalls++; error("must not bind") }
            override fun buildFrameUniforms(): List<Uniform> { frameCalls++; error("must not refresh") }
        }
        val native = object : BlurEffect by NativeBlurEffect() {
            override val radius: Float get() { nativeCalls++; return 4f }
        }
        ShaderFactory.create().use { factory ->
            val mixed = CompositeEffect.of(native, runtime)
            assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(mixed, 32f, 32f))
            assertIs<ShaderResult.Failure<*>>(factory.createAnimatedSession(runtime, 32f, 32f))
            assertIs<ShaderResult.Failure<*>>(factory.createPipelineSession(mixed, 32f, 32f))
            val data = Rgba8Data(1, 1, byteArrayOf(1, 2, 3, 0))
            val background = ShaderDefinition("raw-child", program = ShaderProgram(
                "uniform shader bytes; half4 main(float2 p) { return bytes.eval(p); }",
                listOf(ShaderBinding.data("bytes", data)))).proceduralBackground()
            val child = ShaderNode.snapshot(background)
            val parent = ShaderDefinition("parent", program = ShaderProgram(
                "uniform shader child; half4 main(float2 p) { return child.eval(p); }",
                listOf(ShaderBinding.nested("child", child)))).proceduralBackground()
            assertEquals(ShaderSupportReason.ANDROID_API_33_REQUIRED, factory.queryBackgroundSupport(parent).reason)
            assertEquals(ShaderSupportReason.ANDROID_API_33_REQUIRED, factory.querySupport(ShaderOperation.CONTENT_RENDER, parent.definition.effect()).reason)
            val live = ShaderDefinition("live", program = ShaderProgram("not compiled", listOf(ShaderBinding.frameData("bytes"))))
                .animatedEffect(frameProvider = ShaderFrameProvider { frameCalls++; error("must not execute") })
            assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(live, 32f, 32f))
            assertIs<ShaderResult.Failure<*>>(createBackgroundSnapshot(factory, parent, parent.definition.schema.defaults(), 32f, 32f, 0f, null))
            assertEquals(listOf(0, 0, 0, 0), listOf(sourceCalls, uniformCalls, frameCalls, nativeCalls))
        }
    }

    @Test fun api31RejectsWholeRuntimeGraphAndPreservesOrSubstitutesExplicitly() {
        assumeTrue(Build.VERSION.SDK_INT in 31..32)
        ShaderFactory.create().use { factory ->
            assertEquals(ShaderSupportReason.ANDROID_API_33_REQUIRED, factory.querySupport(ShaderOperation.CONTENT_RENDER, GrayscaleEffect()).reason)
            val mixed = CompositeEffect.of(NativeBlurEffect(), GrayscaleEffect())
            assertIs<ShaderError.PlatformNotSupported>((factory.createRenderEffect(mixed, 32f, 32f) as ShaderResult.Failure).error)
            val preserved = factory.createRenderEffectWithFallback(mixed, 32f, 32f)
            assertTrue(preserved.fallbackUsed)
            assertNull(preserved.result.getOrThrow())
            assertIs<ShaderError.PlatformNotSupported>(preserved.cause)
            val replacement = factory.createRenderEffectWithFallback(mixed, 32f, 32f, ShaderFallbackPolicy.Substitute(NativeBlurEffect()))
            assertNotNull(replacement.result.getOrThrow())
            assertIs<ShaderResult.Failure<*>>(factory.createAnimatedSession(WaveEffect(), 32f, 32f))
            assertIs<ShaderError.PlatformNotSupported>(backgroundRouteError(factory))
            assertIs<ShaderResult.Failure<*>>(createNativeBackgroundSnapshot(factory, "not compiled", emptyList()))
            assertEquals(0, factory.cacheSize)
            factory.close()
            factory.close()
        }
    }
}
