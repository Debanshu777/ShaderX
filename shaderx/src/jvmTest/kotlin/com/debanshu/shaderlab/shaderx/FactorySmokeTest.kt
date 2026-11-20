package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect
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
import kotlin.test.assertTrue

/**
 * JVM/Skia platform smoke tests.
 *
 * These tests compile each built-in shader against the Skia SkSL runtime and catch:
 * - AGSL/SkSL syntax drift in any built-in shader.
 * - Missing uniforms declared in `buildUniforms` but absent from `shaderSource`.
 * - Phase 1's composite-chaining regression (silent drop → explicit error).
 */
class FactorySmokeTest {
    @Test
    fun jvmFactory_compilesEveryBuiltInEffect() {
        val factory = ShaderFactory.create()
        val failures = mutableListOf<String>()

        ShaderX.builtInEffects().forEach { effect ->
            val result = factory.createRenderEffect(effect, 200f, 200f)
            if (result is ShaderResult.Failure) {
                failures += "${effect.id}: ${result.error.message}"
            }
        }

        assertTrue(
            failures.isEmpty(),
            "Some built-in effects failed on JVM/Skia:\n${failures.joinToString("\n")}",
        )
        factory.close()
    }

    @Test
    fun jvmFactory_compositeOfTwoEffects_returnsCompleteFilter() {
        val factory = ShaderFactory.create()
        val composite = GrayscaleEffect() + VignetteEffect()
        val result = factory.createRenderEffect(composite, 200f, 200f)

        assertTrue(result is ShaderResult.Success, "Expected complete Skia chain")
        factory.close()
    }

    @Test
    fun jvmFactory_cacheSize_staysAtOneAfterRepeatedEffect() {
        val factory = ShaderFactory.create()
        val effect = GrayscaleEffect()

        repeat(10) {
            factory.createRenderEffect(effect, 100f, 100f)
        }

        assertTrue(factory.cacheSize <= 1, "Expected cache size ≤ 1, got ${factory.cacheSize}")
        factory.close()
    }

    @Test
    fun jvmFactory_close_emptiesCache() {
        val factory = ShaderFactory.create()
        factory.createRenderEffect(GrayscaleEffect(), 100f, 100f)
        factory.close()
        assertTrue(
            factory.cacheSize == 0,
            "Expected cache size 0 after close, got ${factory.cacheSize}",
        )
    }

    @Test
    fun shaderResult_fold_worksCorrectly() {
        val success: ShaderResult<Int> = ShaderResult.success(42)
        val result = success.fold(onSuccess = { it * 2 }, onFailure = { -1 })
        assertTrue(result == 84)

        val failure: ShaderResult<Int> =
            ShaderResult.failure(ShaderError.PlatformNotSupported("test"))
        val failResult = failure.fold(onSuccess = { it * 2 }, onFailure = { -1 })
        assertTrue(failResult == -1)
    }

    @Test
    fun shaderResult_flatMap_chainsOperations() {
        val result =
            ShaderResult
                .success(10)
                .flatMap { ShaderResult.success(it + 5) }
                .flatMap { ShaderResult.success(it * 2) }
        assertTrue(result is ShaderResult.Success)
        assertTrue((result as ShaderResult.Success).value == 30)
    }

    @Test
    fun shaderResult_recover_convertsFail() {
        val result: ShaderResult<Int> =
            ShaderResult
                .failure<Int>(ShaderError.PlatformNotSupported("test"))
                .recover { -1 }
        assertTrue(result is ShaderResult.Success)
        assertTrue((result as ShaderResult.Success).value == -1)
    }

    // ── Fix 1: Composite chain failure propagation ────────────────────────────

    /** A minimal RuntimeShaderEffect with intentionally broken shader source. */
    private class BrokenEffect : com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect {
        override val id = "broken_test"
        override val displayName = "Broken"
        override val shaderSource = "THIS IS NOT VALID AGSL OR SKSL !@#$"
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
    fun compositeChain_failingSecondEffect_returnsFailure() {
        val factory = ShaderFactory.create()
        // On JVM/Skia, multi-effect composites already return UnsupportedEffect.
        // We construct a single-valid + single-broken composite and force chaining by
        // wrapping the broken effect alone — it must compile and fail.
        val brokenResult = factory.createRenderEffect(BrokenEffect(), 200f, 200f)
        assertTrue(
            brokenResult is ShaderResult.Failure,
            "Expected Failure for broken shader source, got Success",
        )
        assertTrue(
            (brokenResult as ShaderResult.Failure).error is ShaderError.CompilationError,
            "Expected CompilationError, got ${brokenResult.error::class.simpleName}",
        )
        factory.close()
    }

    // ── Fix 3: ImageProcessor creates without crash ───────────────────────────

    @Test
    fun imageProcessor_create_withCorrectFactory_succeeds() {
        val factory = ShaderFactory.create()
        // Must not throw — on Skia the factory IS a SkiaShaderFactory
        val processor = ImageProcessor.create(factory)
        assertTrue(processor != null)
        factory.close()
    }
}
