package com.debanshu.shaderlab.shaderx.effects

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a sepia tone effect for a vintage photograph look.
 *
 * Uses the standard sepia transformation matrix.
 *
 * @property intensity Blend amount between original (0.0) and sepia (1.0)
 */
public data class SepiaEffect(
    private val intensity: Float = 1f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Sepia"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float intensity;
        
        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            
            // Sepia matrix transformation
            float r = color.r * 0.393 + color.g * 0.769 + color.b * 0.189;
            float g = color.r * 0.349 + color.g * 0.686 + color.b * 0.168;
            float b = color.r * 0.272 + color.g * 0.534 + color.b * 0.131;
            
            half3 sepiaColor = half3(r, g, b);
            half3 result = mix(color.rgb, sepiaColor, intensity);
            return half4(result, color.a);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
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
            FloatUniform("intensity", intensity),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): SepiaEffect =
        when (parameterId) {
            PARAM_INTENSITY -> copy(intensity = value)
            else -> this
        }

    public companion object {
        public const val ID: String = "sepia"
        public const val PARAM_INTENSITY: String = "intensity"
    }
}
