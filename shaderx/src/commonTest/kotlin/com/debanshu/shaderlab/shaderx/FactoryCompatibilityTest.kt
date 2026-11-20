package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.BaseShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FactoryCompatibilityTest {
    @Test
    fun oldStyleFactoriesReportUnsupportedSessionWithoutRendering() {
        val direct = object : ShaderFactory {
            override val cacheSize = 0
            override fun isSupported() = error("Default session must not query support")
            override fun clearCache() {}
            override fun close() {}
            override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
                error("Default session must not render")
        }
        val base = object : BaseShaderFactory() {
            override val cacheSize = 0
            override fun isSupported() = error("Default session must not query support")
            override fun clearCache() {}
            override fun close() {}
            override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> = error("Must not render")
            override fun createRuntimeShaderEffect(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
                error("Must not render")
        }
        for (factory in listOf(direct, base)) {
            val result = assertIs<ShaderResult.Failure<*>>(factory.createAnimatedSession(WaveEffect(), 32f, 24f))
            assertEquals(WaveEffect.ID, assertIs<ShaderError.UnsupportedEffect>(result.error).effectId)
        }
    }

    @Test
    fun wavePublishedConveniencesKeepValuesAndConfigurationEquality() {
        val wave = WaveEffect(3f, 4f, false, 2f)
        val (amplitude, frequency, animate, time) = wave
        assertEquals(listOf(3f, 4f, false, 2f), listOf(amplitude, frequency, animate, time))
        val moved: WaveEffect = wave.withTime(9f)
        assertEquals(9f, moved.time)
        assertEquals(wave, moved)
        assertEquals(wave, wave.copy())
        val edited: WaveEffect = wave.withTypedParameter(WaveEffect.PARAM_AMPLITUDE, ParameterValue.FloatValue(8f))
        assertEquals(8f, edited.amplitude)
        assertEquals(2f, edited.time)
        assertEquals("WaveEffect(amplitude=3.0, frequency=4.0, animate=false, time=2.0)", wave.toString())
    }
}
