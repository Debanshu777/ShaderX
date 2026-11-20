package com.debanshu.shaderlab

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel
import kotlin.test.*

class PresetCodecTest {
    @Test fun registeredCustomRoundTripIsTypedOrderedAndExact() {
        val entry = PlaygroundRegistry.standard().entry(CustomDefinitions.controls.id)
        var effect = entry.starter
        for (d in entry.schema.definitions) effect = effect.withTypedParameter(d.key.id, when(d) {
            is FloatParameterDefinition -> ParameterValue.FloatValue(-12345.678f)
            is ColorParameterDefinition -> ParameterValue.ColorValue(0x01123456L)
            is BooleanParameterDefinition -> ParameterValue.BooleanValue(false)
        })
        val encoded = PresetCodec.encode(effect)
        val loaded = PresetCodec.decode(encoded)
        assertEquals(entry.schema.valuesFrom(effect).snapshot(), entry.schema.valuesFrom(loaded).snapshot())
        assertEquals(encoded, PresetCodec.encode(loaded))
        assertTrue(encoded.contains("c 01123456"))
        val model = ShaderLabViewModel()
        model.loadPreset(loaded)
        model.updateTypedParameter(CustomDefinitions.EDITOR_ENABLED.key.id, ParameterValue.BooleanValue(true))
        model.updateTypedParameter(CustomDefinitions.EDITOR_AMOUNT.key.id, ParameterValue.FloatValue(Float.MAX_VALUE))
        assertTrue((model.uiState.value.activeEffect as DefinedShaderEffect)[CustomDefinitions.EDITOR_ENABLED.key])
        model.resetEffectParameters()
        assertEquals(CustomDefinitions.controls.effect(), model.uiState.value.activeEffect)
    }

    @Test fun malformedAndFuturePresetsRejectBeforeReplacement() {
        val good = PresetCodec.encode(CustomDefinitions.controls.effect())
        val lines = good.trimEnd().lines()
        val malformed = listOf(
            "", "x", good.replace("Preset 1", "Preset 2"),
            lines.toMutableList().apply { this[1] = "756e6b6e6f776e" }.joinToString("\n"),
            lines.toMutableList().apply { this[2] = "2" }.joinToString("\n"),
            lines.dropLast(1).joinToString("\n"),
            good + lines.last() + "\n",
            good.replace(" f 0.12345679", " f NaN"),
            good.replace(Regex("c [0-9a-f]{8}"), "c ffffffff0"),
            good.replace("b true", "b 1"),
            good.replace(" f ", " b "),
            "x".repeat(PresetCodec.MAX_BYTES + 1),
            good + ("61 f 1\n".repeat(129)),
            good.replace(lines[1], "ff"),
            good.replace(lines[1], "xz"),
        )
        for (bad in malformed) {
            if (bad == good) continue
            assertFailsWith<IllegalArgumentException>(bad.take(100)) { PresetCodec.decode(bad) }
        }
    }

    @Test fun unknownParameterAndRangeTypeErrorsReject() {
        val entry = PlaygroundRegistry.standard().entry(CustomDefinitions.distortion.id)
        val good = PresetCodec.encode(entry.starter)
        assertFailsWith<IllegalArgumentException> { PresetCodec.decode(good.replace(" f 4.0", " f Infinity")) }
        assertFailsWith<IllegalArgumentException> { PresetCodec.decode(good.replace(" f 4.0", " f 1000.0")) }
        assertFailsWith<IllegalArgumentException> { PresetCodec.decode(good.replace(" f 4.0", " b true")) }
        assertFailsWith<IllegalArgumentException> { PresetCodec.decode(good + "61 f 1\n") }
        assertFailsWith<IllegalArgumentException> { PlaygroundRegistry(listOf(entry, entry)) }
    }
}
