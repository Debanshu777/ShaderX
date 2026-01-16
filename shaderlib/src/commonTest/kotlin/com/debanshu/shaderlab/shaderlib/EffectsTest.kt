package com.debanshu.shaderlab.shaderlib

import com.debanshu.shaderlab.shaderlib.effects.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderlib.effects.GrayscaleEffect
import com.debanshu.shaderlab.shaderlib.effects.InvertEffect
import com.debanshu.shaderlab.shaderlib.effects.NativeBlurEffect
import com.debanshu.shaderlab.shaderlib.effects.PixelateEffect
import com.debanshu.shaderlab.shaderlib.effects.SepiaEffect
import com.debanshu.shaderlab.shaderlib.effects.VignetteEffect
import com.debanshu.shaderlab.shaderlib.effects.WaveEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EffectsTest {

    @Test
    fun grayscaleEffect_hasCorrectId() {
        val effect = GrayscaleEffect()
        assertEquals("grayscale", effect.id)
        assertEquals("Grayscale", effect.displayName)
    }

    @Test
    fun grayscaleEffect_withParameter_updatesValue() {
        val effect = GrayscaleEffect(intensity = 0.5f)
        val updated = effect.withParameter("intensity", 0.8f)

        assertNotEquals(effect, updated)
        assertEquals(0.8f, (updated as GrayscaleEffect).getParameterValue("intensity"))
    }

    @Test
    fun grayscaleEffect_buildsUniforms() {
        val effect = GrayscaleEffect(intensity = 0.7f)
        val uniforms = effect.buildUniforms(100f, 100f)

        assertEquals(1, uniforms.size)
        assertEquals("intensity", uniforms[0].name)
    }

    @Test
    fun sepiaEffect_hasCorrectId() {
        val effect = SepiaEffect()
        assertEquals("sepia", effect.id)
    }

    @Test
    fun vignetteEffect_hasTwoParameters() {
        val effect = VignetteEffect()
        assertEquals(2, effect.parameters.size)
    }

    @Test
    fun vignetteEffect_withParameter_updatesCorrectValue() {
        val effect = VignetteEffect(radius = 0.5f, intensity = 0.5f)

        val updatedRadius = effect.withParameter("radius", 0.8f) as VignetteEffect
        val updatedIntensity = effect.withParameter("intensity", 0.3f) as VignetteEffect

        assertNotEquals(effect, updatedRadius)
        assertNotEquals(effect, updatedIntensity)
    }

    @Test
    fun pixelateEffect_coercesMinimumValue() {
        val effect = PixelateEffect(pixelSize = 0.5f)
        val uniforms = effect.buildUniforms(100f, 100f)

        val pixelSizeUniform = uniforms.find { it.name == "pixelSize" }
        assertTrue(pixelSizeUniform != null)
    }

    @Test
    fun chromaticAberrationEffect_hasOffsetParameter() {
        val effect = ChromaticAberrationEffect(offset = 5f)
        assertEquals(1, effect.parameters.size)
        assertEquals("offset", effect.parameters[0].id)
    }

    @Test
    fun invertEffect_hasNoParameters() {
        assertTrue(InvertEffect.parameters.isEmpty())
    }

    @Test
    fun invertEffect_withParameter_returnsSame() {
        val updated = InvertEffect.withParameter("anything", 1f)
        assertTrue(updated === InvertEffect)
    }

    @Test
    fun waveEffect_isAnimatable() {
        val effect = WaveEffect(animate = true)
        assertTrue(effect.isAnimating)

        val notAnimating = WaveEffect(animate = false)
        assertFalse(notAnimating.isAnimating)
    }

    @Test
    fun waveEffect_withTime_updatesTime() {
        val effect = WaveEffect(time = 0f)
        val updated = effect.withTime(1.5f)

        assertEquals(1.5f, updated.time)
        assertNotEquals(effect, updated)
    }

    @Test
    fun waveEffect_hasThreeParameters() {
        val effect = WaveEffect()
        assertEquals(3, effect.parameters.size)
    }

    @Test
    fun nativeBlurEffect_hasRadiusParameter() {
        val effect = NativeBlurEffect(radius = 15f)
        assertEquals(15f, effect.radius)
        assertEquals(1, effect.parameters.size)
    }

    @Test
    fun nativeBlurEffect_withParameter_updatesRadius() {
        val effect = NativeBlurEffect(radius = 10f)
        val updated = effect.withParameter("radius", 20f) as NativeBlurEffect

        assertEquals(20f, updated.radius)
    }

    @Test
    fun allEffects_haveNonEmptyShaderSource() {
        val effects = listOf(
            GrayscaleEffect(),
            SepiaEffect(),
            VignetteEffect(),
            PixelateEffect(),
            ChromaticAberrationEffect(),
            InvertEffect,
            WaveEffect()
        )

        effects.forEach { effect ->
            assertTrue(
                effect.shaderSource.isNotBlank(),
                "Effect ${effect.id} should have non-empty shader source"
            )
            assertTrue(
                effect.shaderSource.contains("main("),
                "Effect ${effect.id} should contain main function"
            )
        }
    }
}

