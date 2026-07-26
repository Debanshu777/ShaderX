package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.AnimatedGradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.PlasmaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.RippleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VortexEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract tests for built-in animated effects: shader source, uniforms, and equality rules.
 */
class AnimatedEffectContractTest {
    @Test
    fun animatedGradient_buildFrameUniforms_containsOnlyTime() {
        val effect = AnimatedGradientEffect(time = 1.25f)
        val frameUniforms = effect.buildFrameUniforms()
        assertEquals(1, frameUniforms.size)
        assertEquals("time", frameUniforms[0].name)
        assertEquals(1.25f, (frameUniforms[0] as FloatUniform).values[0])
    }

    @Test
    fun animatedGradient_shaderDeclaresTimeAndContent() {
        val effect = AnimatedGradientEffect()
        assertContains(effect.shaderSource, "uniform float time")
        assertContains(effect.shaderSource, "uniform shader content")
        assertTrue(effect.buildUniforms(100f, 100f).any { it.name == "time" })
    }

    @Test
    fun animatedGradient_equalityExcludesTime() {
        val a = AnimatedGradientEffect(speed = 1f, time = 0f)
        val b = AnimatedGradientEffect(speed = 1f, time = 2f)
        assertEquals(a, b)
        assertNotEquals(AnimatedGradientEffect(speed = 1f), AnimatedGradientEffect(speed = 2f))
    }

    @Test
    fun plasma_shaderDeclaresTimeAndContent() {
        val effect = PlasmaEffect()
        assertContains(effect.shaderSource, "uniform float time")
        assertContains(effect.shaderSource, "uniform shader content")
    }

    @Test
    fun plasma_equalityExcludesTime() {
        val a = PlasmaEffect(scale = 4f, time = 0f)
        val b = PlasmaEffect(scale = 4f, time = 3f)
        assertEquals(a, b)
    }

    @Test
    fun plasma_withTime_updatesTimePreservesEquality() {
        val original = PlasmaEffect()
        val updated = original.withTime(5f) as PlasmaEffect
        assertEquals(original, updated)
        assertEquals(5f, updated.time)
    }

    @Test
    fun ripple_shaderDeclaresTimeAndContent() {
        val effect = RippleEffect()
        assertContains(effect.shaderSource, "uniform float time")
        assertContains(effect.shaderSource, "uniform shader content")
    }

    @Test
    fun ripple_equalityExcludesTime() {
        val a = RippleEffect(frequency = 10f, time = 0f)
        val b = RippleEffect(frequency = 10f, time = 1f)
        assertEquals(a, b)
    }

    @Test
    fun vortex_shaderDeclaresTimeAndContent() {
        val effect = VortexEffect()
        assertContains(effect.shaderSource, "uniform float time")
        assertContains(effect.shaderSource, "uniform shader content")
    }

    @Test
    fun vortex_equalityExcludesTime() {
        val a = VortexEffect(strength = 1f, time = 0f)
        val b = VortexEffect(strength = 1f, time = 0.5f)
        assertEquals(a, b)
    }

    @Test
    fun waveEffect_stillExcludesTimeAfterBaseClassMigration() {
        val a = WaveEffect(amplitude = 10f, time = 0f)
        val b = WaveEffect(amplitude = 10f, time = 99f)
        assertEquals(a, b)
    }
}
