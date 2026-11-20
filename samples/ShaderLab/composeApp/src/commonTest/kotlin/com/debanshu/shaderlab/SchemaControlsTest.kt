package com.debanshu.shaderlab

import com.debanshu.shaderlab.playground.parameterSchema
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterMetadata
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.ui.hasUsableSlider
import com.debanshu.shaderlab.ui.parseControlArgb
import com.debanshu.shaderlab.ui.parseControlFloat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaControlsTest {
    @Test
    fun registeredDeclarationsKeepUnboundedNumbersMetadataAndStrictKinds() {
        val amount = FloatParameterDefinition("controls", "amount", 1.2345678f,
            metadata = ParameterMetadata("Amount", "Physical distance", order = 2, semanticRole = "distance"),
            unit = "px", step = .01f, decimalPlaces = 2)
        val tint = ColorParameterDefinition("controls", "tint", 0x40123456L)
        val enabled = BooleanParameterDefinition("controls", "enabled", true)
        val schema = ParameterSchema("controls", listOf(amount, tint, enabled))
        val definition = ShaderDefinition("controls", program = ShaderProgram("""
            uniform shader content;
            uniform float amount;
            layout(color) uniform half4 tint;
            uniform int enabled;
            half4 main(float2 p) { return content.eval(p); }
        """.trimIndent(), listOf(
            ShaderBinding.float("amount", amount.key),
            ShaderBinding.color("tint", tint.key),
            ShaderBinding.boolean("enabled", enabled.key),
        )), schema = schema)
        assertSame(schema, parameterSchema(definition.effect()))
        assertSame(amount, parameterSchema(definition.animatedEffect()).definition("amount"))
        assertEquals("px", amount.unit)
        assertEquals("distance", amount.metadata.semanticRole)
        assertFalse(amount.hasUsableSlider())
        assertEquals(1.2345678f.toBits(), parseControlFloat("1.2345678", amount).toBits())
        assertEquals(0x40123456L, parseControlArgb("40123456"))
    }

    @Test
    fun numericEditingRejectsNonFiniteOutOfRangeAndLocaleCommasWithoutQuantizing() {
        val bounded = FloatParameterDefinition("controls", "number", 0f, -1f..1f, decimalPlaces = 1)
        assertTrue(bounded.hasUsableSlider())
        assertEquals((-0f).toBits(), parseControlFloat("-0.0", bounded).toBits())
        assertEquals(0.12345679f.toBits(), parseControlFloat("1.2345679e-1", bounded).toBits())
        for (invalid in listOf("NaN", "Infinity", "-Infinity", "1.1", "0,1", "", "-")) {
            assertFailsWith<IllegalArgumentException>(invalid) { parseControlFloat(invalid, bounded) }
        }
        assertFalse(FloatParameterDefinition("controls", "fixed", 1f, 1f..1f).hasUsableSlider())
        assertFalse(FloatParameterDefinition("controls", "wide", 0f, -Float.MAX_VALUE..Float.MAX_VALUE).hasUsableSlider())
    }

    @Test
    fun arbitraryArgbEditingIncludesAlphaAndRejectsAmbiguousRgb() {
        assertEquals(0x00123456L, parseControlArgb("#00123456"))
        assertEquals(0x80AB12CDL, parseControlArgb("80ab12cd"))
        assertEquals(0xFFFFFFFFL, parseControlArgb("FFFFFFFF"))
        for (invalid in listOf("123456", "100000000", "GG123456", "", "-1234567")) {
            assertFailsWith<IllegalArgumentException>(invalid) { parseControlArgb(invalid) }
        }
    }
}
