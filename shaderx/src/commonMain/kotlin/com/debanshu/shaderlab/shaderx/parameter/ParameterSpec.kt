package com.debanshu.shaderlab.shaderx.parameter

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
     */
    public val defaultValue: Float

    /**
     * Valid range for this parameter's value.
     */
    public val range: ClosedFloatingPointRange<Float>
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
) : ParameterSpec

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
) : ParameterSpec

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
}

/**
 * A color parameter for UI color pickers.
 *
 * Color values are stored in ARGB format as a Long.
 * Use this for effects that accept color inputs like tint colors,
 * gradient colors, or overlay colors.
 *
 * Note: This parameter type uses [defaultColor] instead of [defaultValue]
 * since colors don't fit the Float parameter model. The [defaultValue] and
 * [range] are provided for interface compliance but shouldn't be used directly.
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
    // defaultValue is not used for colors; use defaultColor instead
    override val defaultValue: Float = 0f

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
