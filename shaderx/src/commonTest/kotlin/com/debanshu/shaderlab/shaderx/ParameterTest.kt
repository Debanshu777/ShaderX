package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterFormatter
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ParameterValue tests

    @Test
    fun parameterValue_floatValue_toFloat_returnsValue() {
        val value = ParameterValue.FloatValue(0.5f)
        assertEquals(0.5f, value.toFloat())
    }

    @Test
    fun parameterValue_colorValue_toFloat_returnsZero() {
        val value = ParameterValue.ColorValue(0xFFFF0000)
        assertEquals(0f, value.toFloat())
    }

    @Test
    fun parameterValue_booleanValue_toFloat_returnsOneOrZero() {
        val trueValue = ParameterValue.BooleanValue(true)
        val falseValue = ParameterValue.BooleanValue(false)

        assertEquals(1f, trueValue.toFloat())
        assertEquals(0f, falseValue.toFloat())
    }

    @Test
    fun parameterValue_fromFloat_createsFloatValue() {
        val value = ParameterValue.fromFloat(0.75f)
        assertTrue(value is ParameterValue.FloatValue)
        assertEquals(0.75f, (value as ParameterValue.FloatValue).value)
    }

    @Test
    fun parameterValue_fromColor_createsColorValue() {
        val value = ParameterValue.fromColor(0xFF00FF00)
        assertTrue(value is ParameterValue.ColorValue)
        assertEquals(0xFF00FF00, (value as ParameterValue.ColorValue).color)
    }

    @Test
    fun parameterValue_fromBoolean_createsBooleanValue() {
        val trueValue = ParameterValue.fromBoolean(true)
        val falseValue = ParameterValue.fromBoolean(false)

        assertTrue(trueValue is ParameterValue.BooleanValue)
        assertTrue(falseValue is ParameterValue.BooleanValue)
        assertEquals(true, (trueValue as ParameterValue.BooleanValue).enabled)
        assertEquals(false, (falseValue as ParameterValue.BooleanValue).enabled)
    }

    // getTypedDefaultValue tests

    @Test
    fun floatParameter_getTypedDefaultValue_returnsFloatValue() {
        val param = FloatParameter("test", "Test", 0f..10f, 5f)
        val value = param.getTypedDefaultValue()

        assertTrue(value is ParameterValue.FloatValue)
        assertEquals(5f, (value as ParameterValue.FloatValue).value)
    }

    @Test
    fun percentageParameter_getTypedDefaultValue_returnsFloatValue() {
        val param = PercentageParameter("test", "Test", 0.75f)
        val value = param.getTypedDefaultValue()

        assertTrue(value is ParameterValue.FloatValue)
        assertEquals(0.75f, (value as ParameterValue.FloatValue).value)
    }

    @Test
    fun toggleParameter_getTypedDefaultValue_returnsBooleanValue() {
        val enabledParam = ToggleParameter("test", "Test", isEnabledByDefault = true)
        val disabledParam = ToggleParameter("test", "Test", isEnabledByDefault = false)

        val enabledValue = enabledParam.getTypedDefaultValue()
        val disabledValue = disabledParam.getTypedDefaultValue()

        assertTrue(enabledValue is ParameterValue.BooleanValue)
        assertTrue(disabledValue is ParameterValue.BooleanValue)
        assertEquals(true, (enabledValue as ParameterValue.BooleanValue).enabled)
        assertEquals(false, (disabledValue as ParameterValue.BooleanValue).enabled)
    }

    @Test
    fun colorParameter_getTypedDefaultValue_returnsColorValue() {
        val param = ColorParameter("test", "Test", 0xFFFF0000)
        val value = param.getTypedDefaultValue()

        assertTrue(value is ParameterValue.ColorValue)
        assertEquals(0xFFFF0000, (value as ParameterValue.ColorValue).color)
    }

    // validateValue tests

    @Test
    fun floatParameter_validateValue_clampsToRange() {
        val param = FloatParameter("test", "Test", 0f..10f, 5f)

        val validValue = param.validateValue(ParameterValue.FloatValue(5f))
        val tooLow = param.validateValue(ParameterValue.FloatValue(-5f))
        val tooHigh = param.validateValue(ParameterValue.FloatValue(15f))
        val colorValue = param.validateValue(ParameterValue.ColorValue(0xFF000000))

        assertTrue(validValue is ParameterValue.FloatValue)
        assertEquals(5f, (validValue as ParameterValue.FloatValue).value)
        assertEquals(0f, (tooLow as ParameterValue.FloatValue).value)
        assertEquals(10f, (tooHigh as ParameterValue.FloatValue).value)
        assertNull(colorValue)
    }

    @Test
    fun toggleParameter_validateValue_convertsBooleanCorrectly() {
        val param = ToggleParameter("test", "Test")

        val boolTrue = param.validateValue(ParameterValue.BooleanValue(true))
        val boolFalse = param.validateValue(ParameterValue.BooleanValue(false))
        val floatHigh = param.validateValue(ParameterValue.FloatValue(0.8f))
        val floatLow = param.validateValue(ParameterValue.FloatValue(0.2f))
        val colorValue = param.validateValue(ParameterValue.ColorValue(0xFF000000))

        assertTrue(boolTrue is ParameterValue.BooleanValue)
        assertEquals(true, (boolTrue as ParameterValue.BooleanValue).enabled)
        assertEquals(false, (boolFalse as ParameterValue.BooleanValue).enabled)
        assertEquals(true, (floatHigh as ParameterValue.BooleanValue).enabled)
        assertEquals(false, (floatLow as ParameterValue.BooleanValue).enabled)
        assertNull(colorValue)
    }

    @Test
    fun colorParameter_validateValue_onlyAcceptsColors() {
        val param = ColorParameter("test", "Test", 0xFFFF0000)

        val colorValue = param.validateValue(ParameterValue.ColorValue(0xFF00FF00))
        val floatValue = param.validateValue(ParameterValue.FloatValue(0.5f))
        val boolValue = param.validateValue(ParameterValue.BooleanValue(true))

        assertTrue(colorValue is ParameterValue.ColorValue)
        assertEquals(0xFF00FF00, (colorValue as ParameterValue.ColorValue).color)
        assertNull(floatValue)
        assertNull(boolValue)
    }

    // Typed formatter tests

    @Test
    fun formatter_formatTyped_handlesFloatValue() {
        val param = PercentageParameter("test", "Test")
        val value = ParameterValue.FloatValue(0.75f)

        assertEquals("75%", ParameterFormatter.formatTyped(param, value))
    }

    @Test
    fun formatter_formatTyped_handlesBooleanValue() {
        val param = ToggleParameter("test", "Test")
        val trueValue = ParameterValue.BooleanValue(true)
        val falseValue = ParameterValue.BooleanValue(false)

        assertEquals("On", ParameterFormatter.formatTyped(param, trueValue))
        assertEquals("Off", ParameterFormatter.formatTyped(param, falseValue))
    }

    @Test
    fun formatter_formatTyped_handlesColorValue() {
        val param = ColorParameter("test", "Test", 0xFFFF0000)
        val value = ParameterValue.ColorValue(0xFF00FF00)  // Green

        assertEquals("#00FF00", ParameterFormatter.formatTyped(param, value))
    }
}
