package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a pixelation effect by reducing image resolution.
 *
 * Creates a retro, mosaic-like appearance.
 *
 * @property pixelSize Size of each pixel block in pixels (minimum 1)
 */
public data class PixelateEffect(
    private val pixelSize: Float = 10f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Pixelate"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float pixelSize;
        
        half4 main(float2 fragCoord) {
            // Snap coordinates to pixel grid
            float2 pixelCoord = floor(fragCoord / pixelSize) * pixelSize + pixelSize * 0.5;
            
            // Clamp to valid range
            pixelCoord = clamp(pixelCoord, float2(0.0), resolution);
            
            return content.eval(pixelCoord);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PixelParameter(
                id = PARAM_PIXEL_SIZE,
                label = "Pixel Size",
                range = 1f..100f,
                defaultValue = pixelSize,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("pixelSize", pixelSize.coerceAtLeast(1f)),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): PixelateEffect =
        when (parameterId) {
            PARAM_PIXEL_SIZE -> copy(pixelSize = value.coerceAtLeast(1f))
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): PixelateEffect =
        when (parameterId) {
            PARAM_PIXEL_SIZE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(pixelSize = value.value.coerceAtLeast(1f))
                    else -> this
                }
            }

            else -> {
                this
            }
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_PIXEL_SIZE -> ParameterValue.FloatValue(pixelSize)
            else -> null
        }

    public companion object {
        public const val ID: String = "pixelation"
        public const val PARAM_PIXEL_SIZE: String = "pixelSize"
    }
}
