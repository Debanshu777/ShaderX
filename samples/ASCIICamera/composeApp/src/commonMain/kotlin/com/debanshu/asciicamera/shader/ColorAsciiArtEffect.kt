package com.debanshu.asciicamera.shader

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.IntUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Color ASCII art with CMYK/CMY color separation (Ian Parberry-style).
 *
 * 4-color mode: 2x2 quadrants → R, B, G, Gray (CMYK-like)
 * 3-color mode: 3 columns → R, G, B (CMY)
 * Each channel: (1 - value) * 9 → character level
 *
 * Link: https://ianparberry.com/art/ascii/color/
 */
data class ColorAsciiArtEffect(
    private val cellSize: Float = 8f,
    private val useFourColor: Boolean = true,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Color ASCII"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float cellSize;
        uniform int useFourColor;

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

            half3 rgb = color.rgb;
            float gray = dot(rgb, half3(0.299, 0.587, 0.114));

            half3 result;
            if (useFourColor != 0) {
                float2 quad = floor(localCoord * 2.0);
                float qx = quad.x;
                float qy = quad.y;
                float level;
                half3 channelColor;
                if (qy < 1.0 && qx < 1.0) {
                    level = (1.0 - rgb.r) * 9.0;
                    channelColor = half3(1.0, 0.0, 0.0);
                } else if (qy < 1.0 && qx >= 1.0) {
                    level = (1.0 - rgb.g) * 9.0;
                    channelColor = half3(0.0, 1.0, 0.0);
                } else if (qy >= 1.0 && qx < 1.0) {
                    level = (1.0 - rgb.b) * 9.0;
                    channelColor = half3(0.0, 0.0, 1.0);
                } else {
                    level = (1.0 - gray) * 9.0;
                    channelColor = half3(gray, gray, gray);
                }
                float thresh = threshold(gridPos);
                float on = step(thresh, clamp(level, 0.0, 9.0) * 1.6);
                result = channelColor * on;
            } else {
                float col = localCoord.x * 3.0;
                float level;
                half3 channelColor;
                if (col < 1.0) {
                    level = (1.0 - rgb.r) * 9.0;
                    channelColor = half3(1.0, 0.0, 0.0);
                } else if (col < 2.0) {
                    level = (1.0 - rgb.g) * 9.0;
                    channelColor = half3(0.0, 1.0, 0.0);
                } else {
                    level = (1.0 - rgb.b) * 9.0;
                    channelColor = half3(0.0, 0.0, 1.0);
                }
                float thresh = threshold(gridPos);
                float on = step(thresh, clamp(level, 0.0, 9.0) * 1.6);
                result = channelColor * on;
            }
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
            ToggleParameter(
                id = PARAM_USE_FOUR_COLOR,
                label = "4-Color Mode",
                isEnabledByDefault = true,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("cellSize", cellSize.coerceIn(4f, 32f)),
            IntUniform("useFourColor", if (useFourColor) 1 else 0),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): ColorAsciiArtEffect =
        when (parameterId) {
            PARAM_CELL_SIZE -> copy(cellSize = value.coerceIn(4f, 32f))
            PARAM_USE_FOUR_COLOR -> copy(useFourColor = value > 0.5f)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): ColorAsciiArtEffect =
        when (parameterId) {
            PARAM_CELL_SIZE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(cellSize = value.value.coerceIn(4f, 32f))
                    else -> this
                }
            }

            PARAM_USE_FOUR_COLOR -> {
                when (value) {
                    is ParameterValue.BooleanValue -> copy(useFourColor = value.enabled)
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
            PARAM_USE_FOUR_COLOR -> ParameterValue.BooleanValue(useFourColor)
            else -> null
        }

    companion object {
        const val ID: String = "color_ascii_art"
        const val PARAM_CELL_SIZE: String = "cellSize"
        const val PARAM_USE_FOUR_COLOR: String = "useFourColor"
    }
}
