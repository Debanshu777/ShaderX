package com.debanshu.shaderlab.shaderx.parameter

/**
 * Represents a typed value for shader parameters.
 *
 * This sealed class provides type-safe handling of different parameter value types
 * used in shader effects.
 */
public sealed class ParameterValue {
    /**
     * A floating-point parameter value.
     */
    public data class FloatValue(public val value: Float) : ParameterValue()

    /**
     * A color parameter value stored as ARGB Long.
     */
    public data class ColorValue(public val color: Long) : ParameterValue()

    /**
     * A boolean parameter value.
     */
    public data class BooleanValue(public val enabled: Boolean) : ParameterValue()

    /**
     * Converts this value to a Float for legacy compatibility.
     * For colors, returns 0f. For booleans, returns 1f or 0f.
     */
    public fun toFloat(): Float = when (this) {
        is FloatValue -> value
        is ColorValue -> 0f
        is BooleanValue -> if (enabled) 1f else 0f
    }

    public companion object {
        /**
         * Creates a ParameterValue from a Float.
         */
        public fun fromFloat(value: Float): ParameterValue = FloatValue(value)

        /**
         * Creates a ParameterValue from a color Long.
         */
        public fun fromColor(color: Long): ParameterValue = ColorValue(color)

        /**
         * Creates a ParameterValue from a Boolean.
         */
        public fun fromBoolean(enabled: Boolean): ParameterValue = BooleanValue(enabled)
    }
}

/**
 * Defines a configurable parameter for a shader effect.
 *
 * Parameters describe the adjustable values that users can modify
 * to customize shader behavior. Each parameter type provides
 * appropriate constraints and formatting.
 *
 * ## Parameter Types
 * - [FloatParameter]: General floating-point values with custom range
 * - [PercentageParameter]: Values from 0.0 to 1.0 (0% to 100%)
 * - [PixelParameter]: Pixel-based values (e.g., blur radius)
 * - [ToggleParameter]: Boolean on/off values (stored as 0.0 or 1.0)
 * - [ColorParameter]: Color values for color picker UIs
 *
 * @see ParameterFormatter for formatting parameter values for display
 */
public sealed interface ParameterSpec {
    /**
     * Unique identifier for this parameter within the effect.
     */
    public val id: String

    /**
     * Human-readable label for display in UI.
     */
    public val label: String

    /**
     * Default value when the effect is first created.
     * For backwards compatibility with Float-based parameters.
     */
    public val defaultValue: Float

    /**
     * Valid range for this parameter's value.
     * For non-float parameters, this may be a nominal range.
     */
    public val range: ClosedFloatingPointRange<Float>

    /**
     * Returns the typed default value for this parameter.
     */
    public fun getTypedDefaultValue(): ParameterValue

    /**
     * Validates and converts an input value to the appropriate type.
     * Returns null if the value cannot be converted.
     */
    public fun validateValue(value: ParameterValue): ParameterValue?
}

/**
 * A general floating-point parameter with customizable range.
 *
 * Use this for values that don't fit other parameter types,
 * such as frequency, scale factors, or angles.
 */
public data class FloatParameter(
    override val id: String,
    override val label: String,
    override val range: ClosedFloatingPointRange<Float>,
    override val defaultValue: Float,
    /** Number of decimal places to display (default: 1) */
    public val decimalPlaces: Int = 1,
) : ParameterSpec {
    override fun getTypedDefaultValue(): ParameterValue = ParameterValue.FloatValue(defaultValue)

    override fun validateValue(value: ParameterValue): ParameterValue? {
        return when (value) {
            is ParameterValue.FloatValue -> {
                val clamped = value.value.coerceIn(range)
                ParameterValue.FloatValue(clamped)
            }
            is ParameterValue.BooleanValue -> ParameterValue.FloatValue(if (value.enabled) 1f else 0f)
            is ParameterValue.ColorValue -> null
        }
    }
}

/**
 * A parameter representing a percentage value (0% to 100%).
 *
 * Internally stored as 0.0 to 1.0, but displayed as percentage.
 * Commonly used for intensity, opacity, or blend amounts.
 */
public data class PercentageParameter(
    override val id: String,
    override val label: String,
    override val defaultValue: Float = 1f,
) : ParameterSpec {
    override val range: ClosedFloatingPointRange<Float> = 0f..1f

    override fun getTypedDefaultValue(): ParameterValue = ParameterValue.FloatValue(defaultValue)

    override fun validateValue(value: ParameterValue): ParameterValue? {
        return when (value) {
            is ParameterValue.FloatValue -> {
                val clamped = value.value.coerceIn(range)
                ParameterValue.FloatValue(clamped)
            }
            is ParameterValue.BooleanValue -> ParameterValue.FloatValue(if (value.enabled) 1f else 0f)
            is ParameterValue.ColorValue -> null
        }
    }
}

/**
 * A parameter representing a pixel-based value.
 *
 * Use for dimensions like blur radius, offset distances, or sizes.
 * Displayed with "px" suffix.
 */
public data class PixelParameter(
    override val id: String,
    override val label: String,
    override val range: ClosedFloatingPointRange<Float>,
    override val defaultValue: Float,
) : ParameterSpec {
    override fun getTypedDefaultValue(): ParameterValue = ParameterValue.FloatValue(defaultValue)

    override fun validateValue(value: ParameterValue): ParameterValue? {
        return when (value) {
            is ParameterValue.FloatValue -> {
                val clamped = value.value.coerceIn(range)
                ParameterValue.FloatValue(clamped)
            }
            is ParameterValue.BooleanValue -> null
            is ParameterValue.ColorValue -> null
        }
    }
}

/**
 * A boolean parameter represented as on/off toggle.
 *
 * Internally stored as 0.0 (off) or 1.0 (on).
 * Use for enabling/disabling features within an effect.
 */
public data class ToggleParameter(
    override val id: String,
    override val label: String,
    public val isEnabledByDefault: Boolean = false,
) : ParameterSpec {
    override val range: ClosedFloatingPointRange<Float> = 0f..1f
    override val defaultValue: Float = if (isEnabledByDefault) 1f else 0f

    override fun getTypedDefaultValue(): ParameterValue = ParameterValue.BooleanValue(isEnabledByDefault)

    override fun validateValue(value: ParameterValue): ParameterValue? {
        return when (value) {
            is ParameterValue.FloatValue -> ParameterValue.BooleanValue(value.value > 0.5f)
            is ParameterValue.BooleanValue -> value
            is ParameterValue.ColorValue -> null
        }
    }
}

/**
 * A color parameter for UI color pickers.
 *
 * Color values are stored in ARGB format as a Long.
 * Use this for effects that accept color inputs like tint colors,
 * gradient colors, or overlay colors.
 *
 * ## Example
 * ```kotlin
 * ColorParameter(
 *     id = "tintColor",
 *     label = "Tint Color",
 *     defaultColor = 0xFFFF5733  // Coral color
 * )
 * ```
 */
public data class ColorParameter(
    override val id: String,
    override val label: String,
    /** Default color in ARGB format (e.g., 0xFFFF5733) */
    public val defaultColor: Long,
) : ParameterSpec {
    // Range is not applicable for colors, but required by interface
    override val range: ClosedFloatingPointRange<Float> = 0f..1f
    // defaultValue is provided for backwards compatibility
    override val defaultValue: Float = 0f

    override fun getTypedDefaultValue(): ParameterValue = ParameterValue.ColorValue(defaultColor)

    override fun validateValue(value: ParameterValue): ParameterValue? {
        return when (value) {
            is ParameterValue.ColorValue -> value
            is ParameterValue.FloatValue -> null
            is ParameterValue.BooleanValue -> null
        }
    }

    /**
     * Extracts the red component (0.0 to 1.0) from the default color.
     */
    public val defaultRed: Float get() = ((defaultColor shr 16) and 0xFF) / 255f

    /**
     * Extracts the green component (0.0 to 1.0) from the default color.
     */
    public val defaultGreen: Float get() = ((defaultColor shr 8) and 0xFF) / 255f

    /**
     * Extracts the blue component (0.0 to 1.0) from the default color.
     */
    public val defaultBlue: Float get() = (defaultColor and 0xFF) / 255f

    /**
     * Extracts the alpha component (0.0 to 1.0) from the default color.
     */
    public val defaultAlpha: Float get() = ((defaultColor shr 24) and 0xFF) / 255f
}
