package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterFormatter
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import kotlin.test.Test
import kotlin.test.assertEquals

class ParameterTest {

    @Test
    fun floatParameter_hasCorrectProperties() {
        val param = FloatParameter(
            id = "frequency",
            label = "Frequency",
            range = 1f..20f,
            defaultValue = 5f,
            decimalPlaces = 2
        )

        assertEquals("frequency", param.id)
        assertEquals("Frequency", param.label)
        assertEquals(1f..20f, param.range)
        assertEquals(5f, param.defaultValue)
        assertEquals(2, param.decimalPlaces)
    }

    @Test
    fun percentageParameter_hasFixedRange() {
        val param = PercentageParameter(
            id = "intensity",
            label = "Intensity",
            defaultValue = 0.75f
        )

        assertEquals(0f..1f, param.range)
        assertEquals(0.75f, param.defaultValue)
    }

    @Test
    fun pixelParameter_hasCorrectProperties() {
        val param = PixelParameter(
            id = "radius",
            label = "Radius",
            range = 0f..50f,
            defaultValue = 10f
        )

        assertEquals("radius", param.id)
        assertEquals(0f..50f, param.range)
        assertEquals(10f, param.defaultValue)
    }

    @Test
    fun toggleParameter_defaultValue_reflectsEnabled() {
        val enabledParam = ToggleParameter(
            id = "animate",
            label = "Animate",
            isEnabledByDefault = true
        )

        val disabledParam = ToggleParameter(
            id = "animate",
            label = "Animate",
            isEnabledByDefault = false
        )

        assertEquals(1f, enabledParam.defaultValue)
        assertEquals(0f, disabledParam.defaultValue)
        assertEquals(0f..1f, enabledParam.range)
    }

    @Test
    fun formatter_formatsFloatCorrectly() {
        assertEquals("5.0", ParameterFormatter.formatFloat(5f, 1))
        assertEquals("5.00", ParameterFormatter.formatFloat(5f, 2))
        assertEquals("5", ParameterFormatter.formatFloat(5f, 0))
        assertEquals("3.14", ParameterFormatter.formatFloat(3.14159f, 2))
    }

    @Test
    fun formatter_formatsPercentageCorrectly() {
        val param = PercentageParameter("test", "Test")
        assertEquals("75%", ParameterFormatter.format(param, 0.75f))
        assertEquals("100%", ParameterFormatter.format(param, 1f))
        assertEquals("0%", ParameterFormatter.format(param, 0f))
    }

    @Test
    fun formatter_formatsPixelCorrectly() {
        val param = PixelParameter("test", "Test", 0f..100f, 50f)
        assertEquals("50px", ParameterFormatter.format(param, 50f))
        assertEquals("0px", ParameterFormatter.format(param, 0.4f))
    }

    @Test
    fun formatter_formatsToggleCorrectly() {
        val param = ToggleParameter("test", "Test")
        assertEquals("On", ParameterFormatter.format(param, 1f))
        assertEquals("Off", ParameterFormatter.format(param, 0f))
        assertEquals("On", ParameterFormatter.format(param, 0.6f))
        assertEquals("Off", ParameterFormatter.format(param, 0.4f))
    }

    // ColorParameter tests

    @Test
    fun colorParameter_hasCorrectProperties() {
        val param = ColorParameter(
            id = "tintColor",
            label = "Tint Color",
            defaultColor = 0xFFFF5733
        )

        assertEquals("tintColor", param.id)
        assertEquals("Tint Color", param.label)
        assertEquals(0xFFFF5733, param.defaultColor)
    }

    @Test
    fun colorParameter_extractsRedComponent() {
        val param = ColorParameter("test", "Test", 0xFFFF0000)  // Pure red
        assertEquals(1f, param.defaultRed, 0.01f)
        assertEquals(0f, param.defaultGreen, 0.01f)
        assertEquals(0f, param.defaultBlue, 0.01f)
    }

    @Test
    fun colorParameter_extractsGreenComponent() {
        val param = ColorParameter("test", "Test", 0xFF00FF00)  // Pure green
        assertEquals(0f, param.defaultRed, 0.01f)
        assertEquals(1f, param.defaultGreen, 0.01f)
        assertEquals(0f, param.defaultBlue, 0.01f)
    }

    @Test
    fun colorParameter_extractsBlueComponent() {
        val param = ColorParameter("test", "Test", 0xFF0000FF)  // Pure blue
        assertEquals(0f, param.defaultRed, 0.01f)
        assertEquals(0f, param.defaultGreen, 0.01f)
        assertEquals(1f, param.defaultBlue, 0.01f)
    }

    @Test
    fun colorParameter_extractsAlphaComponent() {
        val opaqueParam = ColorParameter("test", "Test", 0xFFFF0000)  // Fully opaque
        val semiTransparentParam = ColorParameter("test", "Test", 0x80FF0000)  // 50% transparent

        assertEquals(1f, opaqueParam.defaultAlpha, 0.01f)
        assertEquals(128f / 255f, semiTransparentParam.defaultAlpha, 0.01f)
    }

    @Test
    fun colorParameter_hasFixedRange() {
        val param = ColorParameter("test", "Test", 0xFFFFFFFF)
        assertEquals(0f..1f, param.range)
    }

    @Test
    fun colorParameter_defaultValueIsZero() {
        // defaultValue is not used for colors, but interface requires it
        val param = ColorParameter("test", "Test", 0xFFFFFFFF)
        assertEquals(0f, param.defaultValue)
    }
}

