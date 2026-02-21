package com.debanshu.asciicamera.shader

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Ian Parberry-style grayscale ASCII art effect.
 *
 * Uses luminance to select from 10 character density levels (0-9).
 * Each character is represented as a 4x4 pixel grid with bitmask values.
 * Luminance: dot(rgb, [0.299, 0.587, 0.114])
 * Link: https://ianparberry.com/art/ascii/shader/
 */
data class AsciiArtEffect(
    private val cellSize: Float = 8f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "ASCII Art"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float cellSize;

        // 4x4 Bayer threshold matrix for ordered dithering (Ian Parberry-style 10 levels)
        float threshold(float2 p) {
            int y = int(p.y);
            int x = int(p.x);
            if (y == 0) {
                if (x == 0) return 0.0; if (x == 1) return 8.0; if (x == 2) return 2.0; return 10.0;
            }
            if (y == 1) {
                if (x == 0) return 12.0; if (x == 1) return 4.0; if (x == 2) return 14.0; return 6.0;
            }
            if (y == 2) {
                if (x == 0) return 3.0; if (x == 1) return 11.0; if (x == 2) return 1.0; return 9.0;
            }
            if (x == 0) return 15.0; if (x == 1) return 7.0; if (x == 2) return 13.0; return 5.0;
        }

        half4 main(float2 fragCoord) {
            float2 cellCoord = floor(fragCoord / cellSize);
            float2 localCoord = fract(fragCoord / cellSize);
            float2 gridPos = floor(localCoord * 4.0);

            float2 sampleCoord = (cellCoord + 0.5) * cellSize;
            sampleCoord = clamp(sampleCoord, float2(0.0), resolution - 1.0);
            half4 color = content.eval(sampleCoord);

            float lum = dot(color.rgb, half3(0.299, 0.587, 0.114));
            float level = clamp((1.0 - lum) * 9.0, 0.0, 9.0);
            float thresh = threshold(gridPos);
            float on = step(thresh, level * 1.6);
            half3 result = half3(on, on, on);
            return half4(result, 1.0);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PixelParameter(
                id = PARAM_CELL_SIZE,
                label = "Cell Size",
                range = 4f..32f,
                defaultValue = cellSize,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("cellSize", cellSize.coerceIn(4f, 32f)),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): AsciiArtEffect =
        when (parameterId) {
            PARAM_CELL_SIZE -> copy(cellSize = value.coerceIn(4f, 32f))
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): AsciiArtEffect =
        when (parameterId) {
            PARAM_CELL_SIZE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(cellSize = value.value.coerceIn(4f, 32f))
                    else -> this
                }
            }

            else -> {
                this
            }
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_CELL_SIZE -> ParameterValue.FloatValue(cellSize)
            else -> null
        }

    companion object {
        const val ID: String = "ascii_art"
        const val PARAM_CELL_SIZE: String = "cellSize"
    }
}
