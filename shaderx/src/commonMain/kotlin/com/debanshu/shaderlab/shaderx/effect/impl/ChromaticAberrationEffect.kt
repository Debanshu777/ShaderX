package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a chromatic aberration effect simulating lens distortion.
 *
 * Separates color channels radially from the center of the image.
 *
 * @property offset Distance in pixels to offset red and blue channels
 */
public data class ChromaticAberrationEffect(
    private val offset: Float = 5f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Chromatic"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float offset;
        
        half4 main(float2 fragCoord) {
            // Calculate direction from center
            float2 center = resolution * 0.5;
            float2 dir = normalize(fragCoord - center);
            
            // Sample each color channel with offset
            float r = content.eval(fragCoord + dir * offset).r;
            float g = content.eval(fragCoord).g;
            float b = content.eval(fragCoord - dir * offset).b;
            float a = content.eval(fragCoord).a;
            
            return half4(r, g, b, a);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PixelParameter(
                id = PARAM_OFFSET,
                label = "Offset",
                range = 0f..20f,
                defaultValue = offset,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("offset", offset),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): ChromaticAberrationEffect =
        when (parameterId) {
            PARAM_OFFSET -> copy(offset = value)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): ChromaticAberrationEffect =
        when (parameterId) {
            PARAM_OFFSET -> when (value) {
                is ParameterValue.FloatValue -> copy(offset = value.value)
                else -> this
            }
            else -> this
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_OFFSET -> ParameterValue.FloatValue(offset)
            else -> null
        }

    public companion object {
        public const val ID: String = "chromatic_aberration"
        public const val PARAM_OFFSET: String = "offset"
    }
}

