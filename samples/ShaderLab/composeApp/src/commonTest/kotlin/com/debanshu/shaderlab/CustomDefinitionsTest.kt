package com.debanshu.shaderlab

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.viewmodel.ShaderLabTab
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CustomDefinitionsTest {
    @Test
    fun generatedToggleAdaptsExactUiSignalsWithoutLegacyFloatCoercion() {
        val enabled = BooleanParameterDefinition("sample_toggle", "enabled", true)
        val definition = ShaderDefinition("sample_toggle", program = ShaderProgram("""
            uniform shader content;
            uniform int enabled;
            half4 main(float2 p) { return content.eval(p) * float(enabled); }
        """.trimIndent(), listOf(ShaderBinding.boolean("enabled", enabled.key))),
            schema = ParameterSchema("sample_toggle", listOf(enabled)))
        val model = ShaderLabViewModel()
        model.setActiveEffect(definition.effect())
        model.updateTypedParameter("enabled", com.debanshu.shaderlab.shaderx.parameter.ParameterValue.BooleanValue(false))
        assertFalse((model.uiState.value.activeEffect as DefinedShaderEffect)[enabled.key])
        assertFailsWith<IllegalArgumentException> { model.updateEffectParameter("enabled", .25f) }
        model.updateTypedParameter("enabled", com.debanshu.shaderlab.shaderx.parameter.ParameterValue.BooleanValue(true))
        assertTrue((model.uiState.value.activeEffect as DefinedShaderEffect)[enabled.key])
    }
    @Test
    fun registryReusesDeclarationsWithIndependentInstancesAndCorrectTabs() {
        val first = CustomDefinitions.effects()
        val second = CustomDefinitions.effects()
        assertEquals(3, first.map { it.id }.distinct().size)
        assertNotSame(first[0], second[0])
        assertSame((first[0] as DefinedShaderEffect).definition, (second[0] as DefinedShaderEffect).definition)
        val model = ShaderLabViewModel()
        assertEquals(listOf(first[0], first[2]), model.effectsForTab(first, ShaderLabTab.STATIC))
        assertEquals(listOf(first[1]), model.effectsForTab(first, ShaderLabTab.ANIMATED))
    }

    @Test
    fun generatedTintControlsPreserveAlphaAndResetToDeclarationDefault() {
        val model = ShaderLabViewModel()
        model.setActiveEffect(CustomDefinitions.tint.effect())
        model.updateEffectColorParameter(CustomDefinitions.TINT_COLOR.key.id, 0x20123456L)
        assertEquals(0x20123456L, (model.uiState.value.activeEffect as DefinedShaderEffect)[CustomDefinitions.TINT_COLOR.key])
        model.resetEffectParameters()
        assertEquals(CustomDefinitions.tint.effect(), model.uiState.value.activeEffect)
    }

    @Test
    fun generatedDistortionControlsClampResetAndToggleIndependentPlayback() {
        val model = ShaderLabViewModel()
        model.setActiveEffect(CustomDefinitions.distortion.animatedEffect())
        model.updateEffectParameter(CustomDefinitions.DISTORTION_AMOUNT.key.id, 99f)
        val edited = model.uiState.value.activeEffect as DefinedAnimatedShaderEffect
        assertEquals(32f, edited[CustomDefinitions.DISTORTION_AMOUNT.key])
        assertTrue(edited.isAnimating)
        model.toggleAnimation()
        assertFalse((model.uiState.value.activeEffect as DefinedAnimatedShaderEffect).isAnimating)
        model.resetEffectParameters()
        val reset = model.uiState.value.activeEffect as DefinedAnimatedShaderEffect
        assertEquals(CustomDefinitions.DISTORTION_AMOUNT.defaultValue, reset[CustomDefinitions.DISTORTION_AMOUNT.key])
        assertFalse(reset.isAnimating, "Resetting parameter defaults must preserve explicit playback state")
        model.toggleAnimation()
        assertTrue((model.uiState.value.activeEffect as DefinedAnimatedShaderEffect).isAnimating)
    }
}
