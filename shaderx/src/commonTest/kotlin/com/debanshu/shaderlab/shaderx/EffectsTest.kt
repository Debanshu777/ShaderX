package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.Color
import com.debanshu.shaderlab.shaderx.effects.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderx.effects.GradientEffect
import com.debanshu.shaderlab.shaderx.effects.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effects.InvertEffect
import com.debanshu.shaderlab.shaderx.effects.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effects.PixelateEffect
import com.debanshu.shaderlab.shaderx.effects.SepiaEffect
import com.debanshu.shaderlab.shaderx.effects.VignetteEffect
import com.debanshu.shaderlab.shaderx.effects.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
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
            WaveEffect(),
            GradientEffect()
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

    // GradientEffect tests

    @Test
    fun gradientEffect_hasCorrectId() {
        val effect = GradientEffect()
        assertEquals("gradient_overlay", effect.id)
        assertEquals("Gradient", effect.displayName)
    }

    @Test
    fun gradientEffect_hasThreeParameters() {
        val effect = GradientEffect()
        assertEquals(3, effect.parameters.size)
    }

    @Test
    fun gradientEffect_hasColorParameters() {
        val effect = GradientEffect()
        val colorParams = effect.parameters.filterIsInstance<ColorParameter>()
        assertEquals(2, colorParams.size)
        assertEquals("color1", colorParams[0].id)
        assertEquals("color2", colorParams[1].id)
    }

    @Test
    fun gradientEffect_buildsColorUniforms() {
        val effect = GradientEffect(
            color1 = 0xFFFF0000,  // Red
            color2 = 0xFF00FF00   // Green
        )
        val uniforms = effect.buildUniforms(100f, 100f)

        // Should have: resolution, color1, color2, intensity
        assertEquals(4, uniforms.size)

        val colorUniforms = uniforms.filterIsInstance<ColorUniform>()
        assertEquals(2, colorUniforms.size)

        val color1Uniform = colorUniforms.find { it.name == "color1" }
        assertTrue(color1Uniform != null)
        assertEquals(1f, color1Uniform!!.red, 0.01f)  // Red = FF
        assertEquals(0f, color1Uniform.green, 0.01f)
        assertEquals(0f, color1Uniform.blue, 0.01f)
    }

    @Test
    fun gradientEffect_withColor1_updatesColor() {
        val effect = GradientEffect()
        val updated = effect.withColor1(0xFF0000FF)  // Blue

        assertNotEquals(effect, updated)
    }

    @Test
    fun gradientEffect_withColor2_updatesColor() {
        val effect = GradientEffect()
        val updated = effect.withColor2(0xFFFFFF00)  // Yellow

        assertNotEquals(effect, updated)
    }

    @Test
    fun gradientEffect_withParameter_updatesIntensity() {
        val effect = GradientEffect(intensity = 0.5f)
        val updated = effect.withParameter("intensity", 0.8f) as GradientEffect

        assertNotEquals(effect, updated)
    }

    @Test
    fun gradientEffect_shaderContainsColorLayout() {
        val effect = GradientEffect()
        assertTrue(
            effect.shaderSource.contains("layout(color)"),
            "Shader should use layout(color) annotation for color uniforms"
        )
    }

    // GradientEffect Compose Color integration tests

    @Test
    fun gradientEffect_withColor1_acceptsComposeColor() {
        val effect = GradientEffect()
        val updated = effect.withColor1(Color.Red)

        assertNotEquals(effect, updated)
    }

    @Test
    fun gradientEffect_withColor2_acceptsComposeColor() {
        val effect = GradientEffect()
        val updated = effect.withColor2(Color.Blue)

        assertNotEquals(effect, updated)
    }

    @Test
    fun gradientEffect_withComposeColor_producesCorrectUniforms() {
        val effect = GradientEffect()
            .withColor1(Color.Red)
            .withColor2(Color.Green)

        val uniforms = effect.buildUniforms(100f, 100f)
        val colorUniforms = uniforms.filterIsInstance<ColorUniform>()

        val color1Uniform = colorUniforms.find { it.name == "color1" }
        val color2Uniform = colorUniforms.find { it.name == "color2" }

        assertTrue(color1Uniform != null)
        assertTrue(color2Uniform != null)

        // Verify color1 is red
        assertEquals(1f, color1Uniform!!.red, 0.01f)
        assertEquals(0f, color1Uniform.green, 0.01f)
        assertEquals(0f, color1Uniform.blue, 0.01f)

        // Verify color2 is green
        assertEquals(0f, color2Uniform!!.red, 0.01f)
        assertEquals(1f, color2Uniform.green, 0.01f)
        assertEquals(0f, color2Uniform.blue, 0.01f)
    }

    @Test
    fun gradientEffect_withComposeColor_preservesAlpha() {
        val semiTransparentRed = Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f)
        val effect = GradientEffect().withColor1(semiTransparentRed)

        val uniforms = effect.buildUniforms(100f, 100f)
        val color1Uniform = uniforms.filterIsInstance<ColorUniform>().find { it.name == "color1" }

        assertTrue(color1Uniform != null)
        assertEquals(0.5f, color1Uniform!!.alpha, 0.01f)
    }
}

