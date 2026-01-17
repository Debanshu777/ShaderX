package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect
import com.debanshu.shaderlab.shaderx.effect.plus
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CompositeEffectTest {

    @Test
    fun compositeEffect_plusOperator_combinesTwoEffects() {
        val effect = GrayscaleEffect() + VignetteEffect()

        assertTrue(effect is CompositeEffect)
        assertEquals(2, effect.size)
        assertTrue(effect[0] is GrayscaleEffect)
        assertTrue(effect[1] is VignetteEffect)
    }

    @Test
    fun compositeEffect_plusOperator_chainsMultipleEffects() {
        val effect = GrayscaleEffect() + VignetteEffect() + NativeBlurEffect()

        assertEquals(3, effect.size)
        assertTrue(effect[0] is GrayscaleEffect)
        assertTrue(effect[1] is VignetteEffect)
        assertTrue(effect[2] is NativeBlurEffect)
    }

    @Test
    fun compositeEffect_of_createsFromVarargs() {
        val effect = CompositeEffect.of(
            GrayscaleEffect(),
            VignetteEffect(),
            NativeBlurEffect()
        )

        assertEquals(3, effect.size)
    }

    @Test
    fun compositeEffect_of_createsFromList() {
        val effects = listOf(GrayscaleEffect(), VignetteEffect())
        val effect = CompositeEffect.of(effects)

        assertEquals(2, effect.size)
    }

    @Test
    fun compositeEffect_id_combinesEffectIds() {
        val effect = GrayscaleEffect() + VignetteEffect()

        assertTrue(effect.id.contains("grayscale"))
        assertTrue(effect.id.contains("vignette"))
    }

    @Test
    fun compositeEffect_displayName_combinesNames() {
        val effect = GrayscaleEffect() + VignetteEffect()

        assertEquals("Grayscale + Vignette", effect.displayName)
    }

    @Test
    fun compositeEffect_parameters_prefixesWithIndex() {
        val effect = GrayscaleEffect() + VignetteEffect()

        // GrayscaleEffect has "intensity" -> "0_intensity"
        // VignetteEffect has "radius" and "intensity" -> "1_radius", "1_intensity"
        val paramIds = effect.parameters.map { it.id }

        assertTrue(paramIds.contains("0_intensity"))
        assertTrue(paramIds.contains("1_radius"))
        assertTrue(paramIds.contains("1_intensity"))
    }

    @Test
    fun compositeEffect_withParameter_updatesCorrectEffect() {
        val effect = GrayscaleEffect(intensity = 0.5f) + VignetteEffect(radius = 0.5f)

        val updated = effect.withParameter("1_radius", 0.8f)

        assertNotEquals(effect, updated)
        assertEquals(0.8f, updated.getParameterValue("1_radius"))
        assertEquals(0.5f, updated.getParameterValue("0_intensity"))
    }

    @Test
    fun compositeEffect_withTypedParameter_updatesCorrectEffect() {
        val effect = GrayscaleEffect(intensity = 0.5f) + VignetteEffect(radius = 0.5f)

        val updated = effect.withTypedParameter(
            "0_intensity",
            ParameterValue.FloatValue(0.9f)
        )

        assertNotEquals(effect, updated)

        val value = updated.getTypedParameterValue("0_intensity")
        assertTrue(value is ParameterValue.FloatValue)
        assertEquals(0.9f, (value as ParameterValue.FloatValue).value)
    }

    @Test
    fun compositeEffect_getParameterValue_returnsCorrectValue() {
        val effect = GrayscaleEffect(intensity = 0.7f) + VignetteEffect(radius = 0.3f)

        assertEquals(0.7f, effect.getParameterValue("0_intensity"))
        assertEquals(0.3f, effect.getParameterValue("1_radius"))
    }

    @Test
    fun compositeEffect_getParameterValue_returnsZeroForUnknown() {
        val effect = GrayscaleEffect() + VignetteEffect()

        assertEquals(0f, effect.getParameterValue("invalid_param"))
        assertEquals(0f, effect.getParameterValue("99_intensity"))
    }

    @Test
    fun compositeEffect_plusComposite_flattensEffects() {
        val first = GrayscaleEffect() + VignetteEffect()
        val second = NativeBlurEffect() + GrayscaleEffect()

        val combined = first + second

        assertEquals(4, combined.size)
    }

    @Test
    fun compositeEffect_indexOperator_accessesEffect() {
        val effect = GrayscaleEffect() + VignetteEffect() + NativeBlurEffect()

        assertTrue(effect[0] is GrayscaleEffect)
        assertTrue(effect[1] is VignetteEffect)
        assertTrue(effect[2] is NativeBlurEffect)
    }

    @Test
    fun compositeEffect_requiresAtLeastOneEffect() {
        assertFailsWith<IllegalArgumentException> {
            CompositeEffect(emptyList())
        }
    }

    @Test
    fun compositeEffect_singleEffect_works() {
        val effect = CompositeEffect.of(GrayscaleEffect())

        assertEquals(1, effect.size)
        assertTrue(effect[0] is GrayscaleEffect)
    }

    @Test
    fun compositeEffect_preservesEffectState() {
        val grayscale = GrayscaleEffect(intensity = 0.3f)
        val vignette = VignetteEffect(radius = 0.7f, intensity = 0.4f)

        val composite = grayscale + vignette

        // Verify the contained effects maintain their state
        val containedGrayscale = composite[0] as GrayscaleEffect
        val containedVignette = composite[1] as VignetteEffect

        assertEquals(grayscale, containedGrayscale)
        assertEquals(vignette, containedVignette)
    }
}

