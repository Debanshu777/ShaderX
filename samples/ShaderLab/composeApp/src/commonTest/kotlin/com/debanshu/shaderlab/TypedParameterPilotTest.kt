package com.debanshu.shaderlab

import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.ui.primarySpeedParameterId
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TypedParameterPilotTest {
    @Test
    fun gradientControlsPreserveArgbAndFloatPrecisionAndExplicitlyClampThenReset() {
        val model = ShaderLabViewModel()
        model.setActiveEffect(GradientEffect())
        model.updateEffectColorParameter(GradientEffect.PARAM_COLOR_1, 0x80123456L)
        val precise = 0.12345679f
        model.updateEffectParameter(GradientEffect.PARAM_INTENSITY, precise)
        val edited = model.uiState.value.activeEffect as GradientEffect
        assertEquals(0x80123456L, edited.color1)
        assertEquals(precise.toBits(), edited.intensity.toBits())

        model.updateEffectParameter(GradientEffect.PARAM_INTENSITY, 2f)
        assertEquals(1f, (model.uiState.value.activeEffect as GradientEffect).intensity)
        model.resetEffectParameters()
        assertEquals(GradientEffect(), model.uiState.value.activeEffect)
    }

    @Test
    fun waveControlsKeepSpatialFrequencySeparateFromPlaybackAndResetDeclarations() {
        val model = ShaderLabViewModel()
        model.setActiveEffect(WaveEffect(amplitude = 17f, frequency = 12f, animate = false))
        assertNull((model.uiState.value.activeEffect as WaveEffect).primarySpeedParameterId())
        model.toggleAnimation()
        model.updateEffectParameter(WaveEffect.PARAM_FREQUENCY, 7.123456f)
        val edited = model.uiState.value.activeEffect as WaveEffect
        assertEquals(true, edited.animate)
        assertEquals(17f, edited.amplitude)
        assertEquals(7.123456f, edited.frequency)
        assertEquals(0f, edited.time)
        model.resetEffectParameters()
        assertEquals(WaveEffect(), model.uiState.value.activeEffect)
    }

    @Test
    fun invalidPilotEditsLeaveCurrentStateUnchanged() {
        val model = ShaderLabViewModel()
        model.setActiveEffect(WaveEffect())
        val original = model.uiState.value.activeEffect
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                model.updateEffectParameter(WaveEffect.PARAM_AMPLITUDE, value)
            }
            assertEquals(original, model.uiState.value.activeEffect)
        }
        assertFailsWith<IllegalArgumentException> { model.updateEffectParameter("missing", 1f) }
        assertFailsWith<IllegalArgumentException> {
            model.updateEffectColorParameter(WaveEffect.PARAM_AMPLITUDE, 0xFF123456L)
        }
        assertEquals(original, model.uiState.value.activeEffect)
    }
}
