package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a vignette effect that darkens the edges of an image.
 *
 * Creates a smooth gradient from center to edges.
 *
 * @property radius Distance from center where darkening begins (0.0 to 1.0)
 * @property intensity Strength of the darkening effect (0.0 to 1.0)
 */
public data class VignetteEffect(
    private val radius: Float = 0.5f,
    private val intensity: Float = 0.5f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Vignette"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float radius;
        uniform float intensity;
        
        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            
            // Normalize coordinates to center
            float2 uv = fragCoord / resolution;
            float2 center = float2(0.5, 0.5);
            float dist = distance(uv, center);
            
            // Calculate vignette factor
            float vignette = smoothstep(radius, radius - intensity, dist);
            
            return half4(color.rgb * vignette, color.a);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PercentageParameter(
                id = PARAM_RADIUS,
                label = "Radius",
                defaultValue = radius,
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
            FloatUniform("radius", radius),
            FloatUniform("intensity", intensity),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): VignetteEffect =
        when (parameterId) {
            PARAM_RADIUS -> copy(radius = value)
            PARAM_INTENSITY -> copy(intensity = value)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): VignetteEffect =
        when (parameterId) {
            PARAM_RADIUS -> when (value) {
                is ParameterValue.FloatValue -> copy(radius = value.value)
                else -> this
            }
            PARAM_INTENSITY -> when (value) {
                is ParameterValue.FloatValue -> copy(intensity = value.value)
                else -> this
            }
            else -> this
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_RADIUS -> ParameterValue.FloatValue(radius)
            PARAM_INTENSITY -> ParameterValue.FloatValue(intensity)
            else -> null
        }

    public companion object {
        public const val ID: String = "vignette"
        public const val PARAM_RADIUS: String = "radius"
        public const val PARAM_INTENSITY: String = "intensity"
    }
}

