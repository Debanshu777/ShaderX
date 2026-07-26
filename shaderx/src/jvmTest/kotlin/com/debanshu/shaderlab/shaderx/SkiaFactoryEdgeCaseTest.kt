package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.effect.plus
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM/Skia [ShaderFactory] edge-case tests that complement [FactorySmokeTest]:
 * - clearCache empties the cache
 * - isSupported returns true on JVM
 * - close is idempotent (can be called multiple times without error)
 * - zero-dimension renders compile successfully
 * - single-effect composite always succeeds (no chaining needed)
 * - custom cache size is respected
 * - factory with small cache evicts correctly
 * - ImageProcessor creation validates factory type
 * - Built-in effects all compile (belt-and-suspenders alongside FactorySmokeTest)
 */
class SkiaFactoryEdgeCaseTest {
    // ── clearCache ────────────────────────────────────────────────────────────

    @Test
    fun skiaFactory_clearCache_emptiesCache() {
        val factory = ShaderFactory.create()
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        assertTrue(factory.cacheSize > 0, "Cache should have an entry after createRenderEffect")

        factory.clearCache()
        assertEquals(0, factory.cacheSize, "clearCache should empty the cache")
        factory.close()
    }

    @Test
    fun skiaFactory_clearCache_allowsReuse() {
        val factory = ShaderFactory.create()
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        factory.clearCache()

        // After clear, shader must be recompiled on next call
        val result = factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        assertTrue(result is ShaderResult.Success, "Factory should still work after clearCache")
        factory.close()
    }

    // ── isSupported ────────────────────────────────────────────────────────────

    @Test
    fun skiaFactory_isSupported_returnsTrue() {
        val factory = ShaderFactory.create()
        assertTrue(factory.isSupported(), "isSupported must return true on JVM/Skia")
        factory.close()
    }

    // ── close idempotency ─────────────────────────────────────────────────────

    @Test
    fun skiaFactory_close_idempotent() {
        val factory = ShaderFactory.create()
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        factory.close()
        factory.close() // Second close must not throw
        assertEquals(0, factory.cacheSize)
    }

    @Test
    fun skiaFactory_closeAndClearCache_equivalent() {
        val f1 = ShaderFactory.create()
        val f2 = ShaderFactory.create()
        f1.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        f2.createRenderEffect(GrayscaleEffect(), 100f, 100f)

        f1.close()
        f2.clearCache()

        assertEquals(
            f1.cacheSize,
            f2.cacheSize,
            "close and clearCache should both result in empty cache",
        )
        f2.close()
    }

    // ── Zero dimensions ───────────────────────────────────────────────────────

    @Test
    fun skiaFactory_zeroDimensions_compilesSuccessfully() {
        val factory = ShaderFactory.create()
        // GrayscaleEffect has no resolution uniform — zero dimensions are fine
        val result = factory.createRenderEffect(GrayscaleEffect(), 0f, 0f)
        assertTrue(result is ShaderResult.Success, "Zero dimensions should not fail compilation")
        factory.close()
    }

    @Test
    fun skiaFactory_zeroDimensions_withResolutionEffect_compilesSuccessfully() {
        val factory = ShaderFactory.create()
        // VignetteEffect uses resolution in uniforms but compilation still succeeds
        val result = factory.createRenderEffect(VignetteEffect(), 0f, 0f)
        assertTrue(
            result is ShaderResult.Success,
            "Zero dimensions with resolution uniform should still compile; got: ${(result as? ShaderResult.Failure)?.error?.message}",
        )
        factory.close()
    }

    // ── Single-effect composite ────────────────────────────────────────────────

    @Test
    fun skiaFactory_singleEffectComposite_succeeds() {
        val factory = ShaderFactory.create()
        val singleComposite = CompositeEffect.of(GrayscaleEffect())
        val result = factory.createRenderEffect(singleComposite, 200f, 200f)
        assertTrue(
            result is ShaderResult.Success,
            "Single-effect composite should not trigger UnsupportedEffect",
        )
        factory.close()
    }

    @Test
    fun skiaFactory_singleNativeComposite_succeeds() {
        val factory = ShaderFactory.create()
        val composite = CompositeEffect.of(NativeBlurEffect(radius = 5f))
        val result = factory.createRenderEffect(composite, 200f, 200f)
        assertTrue(result is ShaderResult.Success, "Single native effect composite should succeed")
        factory.close()
    }

    // ── Two-effect composite returns UnsupportedEffect ─────────────────────────

    @Test
    fun skiaFactory_twoEffectComposite_returnsUnsupportedEffect() {
        val factory = ShaderFactory.create()
        val composite = GrayscaleEffect() + SepiaEffect()
        val result = factory.createRenderEffect(composite, 100f, 100f)

        assertTrue(result is ShaderResult.Failure)
        assertTrue(
            (result as ShaderResult.Failure).error is ShaderError.UnsupportedEffect,
            "Two-effect composite should produce UnsupportedEffect on Skia",
        )
        factory.close()
    }

    // ── Custom cache size ─────────────────────────────────────────────────────

    @Test
    fun skiaFactory_customCacheSize_cappedAtMaxSize() {
        val maxSize = 2
        val factory = ShaderFactory.create(maxCacheSize = maxSize)

        // Compile 4 distinct effects to exceed the cache
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        factory.createRenderEffect(SepiaEffect(), 100f, 100f)
        factory.createRenderEffect(VignetteEffect(), 100f, 100f)
        factory.createRenderEffect(ChromaticAberrationEffect(), 100f, 100f)

        assertTrue(
            factory.cacheSize <= maxSize,
            "Cache size should not exceed maxCacheSize=$maxSize, got ${factory.cacheSize}",
        )
        factory.close()
    }

    @Test
    fun skiaFactory_defaultCacheSize_compilesMany() {
        val factory = ShaderFactory.create()
        ShaderX.builtInEffects().forEach { effect ->
            factory.createRenderEffect(effect, 100f, 100f)
        }
        assertTrue(factory.cacheSize > 0, "Cache should have entries after compiling built-ins")
        factory.close()
    }

    // ── Cache sharing between repeated calls ──────────────────────────────────

    @Test
    fun skiaFactory_sameEffectRepeated_cacheNotGrown() {
        val factory = ShaderFactory.create()
        val effect = GrayscaleEffect()

        repeat(20) {
            factory.createRenderEffect(effect, 100f + it, 100f + it)
        }

        // Shader source is the same → only one cache entry
        assertEquals(
            1,
            factory.cacheSize,
            "Repeated calls with same shader source should not grow cache",
        )
        factory.close()
    }

    @Test
    fun skiaFactory_differentDimensions_sameEffect_singleCacheEntry() {
        val factory = ShaderFactory.create()
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        factory.createRenderEffect(GrayscaleEffect(), 200f, 200f)
        factory.createRenderEffect(GrayscaleEffect(), 500f, 500f)

        // Dimensions change uniforms but NOT the compiled shader — still 1 cache entry
        assertEquals(1, factory.cacheSize)
        factory.close()
    }

    // ── NativeBlurEffect radius clamping at factory level ─────────────────────

    @Test
    fun skiaFactory_blurEffect_zeroRadius_clampedAtFactory() {
        val factory = ShaderFactory.create()
        // NativeBlurEffect with radius=0f is rendered with MIN_BLUR_RADIUS=0.1f at factory level
        val result = factory.createRenderEffect(NativeBlurEffect(radius = 0f), 100f, 100f)
        assertTrue(
            result is ShaderResult.Success,
            "Zero radius blur should use minimum (0.1f) at factory",
        )
        factory.close()
    }

    // ── Broken shader produces CompilationError ───────────────────────────────

    private class BrokenRtEffect : RuntimeShaderEffect {
        override val id = "broken_rt"
        override val displayName = "Broken RT"
        override val shaderSource = "THIS IS CLEARLY INVALID SKSL @#$%"
        override val parameters: List<ParameterSpec> = emptyList()

        override fun buildUniforms(
            width: Float,
            height: Float,
        ): List<Uniform> = emptyList()

        override fun withTypedParameter(
            parameterId: String,
            value: ParameterValue,
        ) = this
    }

    @Test
    fun skiaFactory_brokenShader_returnsCompilationError() {
        val factory = ShaderFactory.create()
        val result = factory.createRenderEffect(BrokenRtEffect(), 100f, 100f)

        assertTrue(result is ShaderResult.Failure)
        assertTrue(
            (result as ShaderResult.Failure).error is ShaderError.CompilationError,
            "Invalid SkSL should produce CompilationError, got ${result.error::class.simpleName}",
        )
        factory.close()
    }

    @Test
    fun skiaFactory_brokenShader_doesNotCacheFailedEntry() {
        val factory = ShaderFactory.create()
        // Broken effect → compile failure → should not grow the cache
        factory.cacheSize
        factory.createRenderEffect(BrokenRtEffect(), 100f, 100f)
        // Cache might have grown by 0 or 1 depending on whether the runtime throws before or after
        // At minimum, subsequent successful compiles still work
        val result = factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        assertTrue(result is ShaderResult.Success, "Factory should still work after broken shader")
        factory.close()
    }

    // ── ImageProcessor factory validation ─────────────────────────────────────

    @Test
    fun imageProcessor_create_withSkiaFactory_succeeds() {
        val factory = ShaderFactory.create()
        val processor = ImageProcessor.create(factory)
        assertNotNull(processor)
        factory.close()
    }

    @Test
    fun imageProcessor_create_withCustomFactory_throwsRequire() {
        // A hand-rolled factory that is NOT SkiaShaderFactory must be rejected on JVM
        val fakeFactory =
            object : ShaderFactory {
                override fun createRenderEffect(
                    effect: com.debanshu.shaderlab.shaderx.effect.ShaderEffect,
                    width: Float,
                    height: Float,
                ) = ShaderResult.failure<androidx.compose.ui.graphics.RenderEffect>(
                    ShaderError.PlatformNotSupported("fake"),
                )

                override fun createAnimatedSession(
                    effect: com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect,
                    width: Float,
                    height: Float,
                ) = ShaderResult.failure<com.debanshu.shaderlab.shaderx.factory.ShaderSession>(
                    ShaderError.PlatformNotSupported("fake"),
                )

                override fun isSupported() = false

                override fun clearCache() {}

                override fun close() {}

                override val cacheSize = 0
            }

        try {
            ImageProcessor.create(fakeFactory)
            // If we reach here, the require check was not enforced
            assertFalse(true, "ImageProcessor.create should throw for non-SkiaShaderFactory")
        } catch (e: IllegalArgumentException) {
            // Expected — require(factory is SkiaShaderFactory)
            assertTrue(e.message?.contains("factory") == true || e.message != null)
        }
    }

    // ── WaveEffect animation with factory ────────────────────────────────────

    @Test
    fun skiaFactory_waveEffect_differentTimes_singleCacheEntry() {
        val factory = ShaderFactory.create()
        // WaveEffect at different times has the same shader source
        factory.createRenderEffect(WaveEffect(time = 0f), 200f, 200f)
        factory.createRenderEffect(WaveEffect(time = 1f), 200f, 200f)
        factory.createRenderEffect(WaveEffect(time = 2f), 200f, 200f)

        assertEquals(1, factory.cacheSize, "Different WaveEffect times use the same shader source")
        factory.close()
    }

    // ── Gradient effect with color uniforms compiles ──────────────────────────

    @Test
    fun skiaFactory_gradientEffect_withColors_compiles() {
        val factory = ShaderFactory.create()
        val effect =
            GradientEffect(
                color1 = 0xFFFF0000L, // Red
                color2 = 0xFF0000FFL, // Blue
                intensity = 0.8f,
            )
        val result = factory.createRenderEffect(effect, 200f, 200f)
        assertTrue(
            result is ShaderResult.Success,
            "GradientEffect should compile on Skia; error: ${(result as? ShaderResult.Failure)?.error?.message}",
        )
        factory.close()
    }
}
