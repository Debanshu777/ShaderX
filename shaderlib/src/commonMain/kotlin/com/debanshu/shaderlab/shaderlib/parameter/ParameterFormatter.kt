package com.debanshu.shaderlab.shaderlib.parameter

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Utility object for formatting parameter values for display.
 *
 * This separates formatting logic from the parameter data classes,
 * allowing parameters to remain serializable and properly implement equals/hashCode.
 */
public object ParameterFormatter {

    /**
     * Formats a parameter value for display based on its type.
     *
     * @param parameter The parameter specification
     * @param value The current value to format
     * @return Formatted string representation
     */
    public fun format(parameter: ParameterSpec, value: Float): String = when (parameter) {
        is FloatParameter -> formatFloat(value, parameter.decimalPlaces)
        is PercentageParameter -> "${(value * 100).toInt()}%"
        is PixelParameter -> "${value.toInt()}px"
        is ToggleParameter -> if (value > 0.5f) "On" else "Off"
    }

    /**
     * Formats a float value with the specified number of decimal places.
     *
     * @param value The value to format
     * @param decimals Number of decimal places (0 for integer display)
     * @return Formatted string
     */
    public fun formatFloat(value: Float, decimals: Int): String {
        val multiplier = 10.0.pow(decimals)
        val rounded = (value * multiplier).roundToInt() / multiplier
        return if (decimals == 0) {
            rounded.toInt().toString()
        } else {
            val str = rounded.toString()
            val dotIndex = str.indexOf('.')
            if (dotIndex == -1) {
                "$str.${"0".repeat(decimals)}"
            } else {
                val currentDecimals = str.length - dotIndex - 1
                if (currentDecimals < decimals) {
                    str + "0".repeat(decimals - currentDecimals)
                } else {
                    str.take(dotIndex + decimals + 1)
                }
            }
        }
    }
}
