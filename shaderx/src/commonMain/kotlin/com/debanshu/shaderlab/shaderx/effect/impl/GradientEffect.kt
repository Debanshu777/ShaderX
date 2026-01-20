package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.ui.graphics.Color
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a gradient overlay effect to the image.
 *
 * Creates a smooth gradient between two colors that blends with the original image.
 * The gradient is calculated based on distance from the bottom-left corner.
 *
 * This effect demonstrates the use of [ColorUniform] for passing colors to shaders
 * with proper color space handling.
 *
 * @property color1 First gradient color in ARGB format (bottom-left)
 * @property color2 Second gradient color in ARGB format (top-right)
 * @property intensity Blend amount between original (0.0) and gradient (1.0)
 */
public data class GradientEffect(
    private val color1: Long = DEFAULT_COLOR_1,
    private val color2: Long = DEFAULT_COLOR_2,
    private val intensity: Float = 0.5f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Gradient"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        layout(color) uniform half4 color1;
        layout(color) uniform half4 color2;
        uniform float intensity;
        
        half4 main(float2 fragCoord) {
            half4 originalColor = content.eval(fragCoord);
            
            // Calculate normalized coordinates
            float2 uv = fragCoord / resolution;
            
            // Calculate gradient based on distance from bottom-left corner
            float mixValue = distance(uv, vec2(0.0, 1.0));
            
            // Interpolate between the two colors
            half4 gradientColor = mix(color1, color2, mixValue);
            
            // Blend gradient with original image
            half3 blendedRgb = mix(originalColor.rgb, originalColor.rgb * gradientColor.rgb, intensity);
            
            return half4(blendedRgb, originalColor.a);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            ColorParameter(
                id = PARAM_COLOR_1,
                label = "Color 1",
                defaultColor = color1,
            ),
            ColorParameter(
                id = PARAM_COLOR_2,
                label = "Color 2",
                defaultColor = color2,
            ),
            PercentageParameter(
                id = PARAM_INTENSITY,
                label = "Intensity",
                defaultValue = intensity,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            ColorUniform("color1", color1),
            ColorUniform("color2", color2),
            FloatUniform("intensity", intensity),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): GradientEffect =
        when (parameterId) {
            PARAM_INTENSITY -> copy(intensity = value)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): GradientEffect =
        when (parameterId) {
            PARAM_COLOR_1 -> {
                when (value) {
                    is ParameterValue.ColorValue -> copy(color1 = value.color)
                    else -> this
                }
            }

            PARAM_COLOR_2 -> {
                when (value) {
                    is ParameterValue.ColorValue -> copy(color2 = value.color)
                    else -> this
                }
            }

            PARAM_INTENSITY -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(intensity = value.value)
                    else -> this
                }
            }

            else -> {
                this
            }
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_COLOR_1 -> ParameterValue.ColorValue(color1)
            PARAM_COLOR_2 -> ParameterValue.ColorValue(color2)
            PARAM_INTENSITY -> ParameterValue.FloatValue(intensity)
            else -> null
        }

    /**
     * Creates a new effect with the first color updated.
     *
     * @param color Color in ARGB Long format
     */
    public fun withColor1(color: Long): GradientEffect = copy(color1 = color)

    /**
     * Creates a new effect with the first color updated.
     *
     * @param color Compose Color value
     */
    public fun withColor1(color: Color): GradientEffect = copy(color1 = color.toArgbLong())

    /**
     * Creates a new effect with the second color updated.
     *
     * @param color Color in ARGB Long format
     */
    public fun withColor2(color: Long): GradientEffect = copy(color2 = color)

    /**
     * Creates a new effect with the second color updated.
     *
     * @param color Compose Color value
     */
    public fun withColor2(color: Color): GradientEffect = copy(color2 = color.toArgbLong())

    public companion object {
        public const val ID: String = "gradient_overlay"
        public const val PARAM_COLOR_1: String = "color1"
        public const val PARAM_COLOR_2: String = "color2"
        public const val PARAM_INTENSITY: String = "intensity"

        /** Coral color - default for color1 */
        public const val DEFAULT_COLOR_1: Long = 0xFFF3A397

        /** Light yellow - default for color2 */
        public const val DEFAULT_COLOR_2: Long = 0xFFF8EE94
    }
}

/**
 * Converts a Compose Color to an ARGB Long format.
 *
 * @return Color as ARGB Long (e.g., 0xFFFF5733)
 */
private fun Color.toArgbLong(): Long {
    val a = (alpha * 255).toInt() and 0xFF
    val r = (red * 255).toInt() and 0xFF
    val g = (green * 255).toInt() and 0xFF
    val b = (blue * 255).toInt() and 0xFF
    return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}
