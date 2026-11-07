package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Classic demoscene plasma effect with organic, psychedelic color movement.
 *
 * Layers multiple sine waves with different frequencies, all phase-shifted by [time].
 */
@Immutable
public class PlasmaEffect(
    public val scale: Float = 4f,
    public val speed: Float = 1f,
    public val colorIntensity: Float = 1f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Plasma"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String get() = SHADER_SOURCE

    override val parameterHandlers: Map<String, ParamHandler<*>> get() = PARAMETER_HANDLERS

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("scale", scale),
            FloatUniform("speed", speed),
            FloatUniform("colorIntensity", colorIntensity),
            FloatUniform("time", time),
        )

    override fun copyWithTime(newTime: Float): PlasmaEffect = PlasmaEffect(scale, speed, colorIntensity, animate, newTime)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlasmaEffect) return false
        return scale == other.scale &&
            speed == other.speed &&
            colorIntensity == other.colorIntensity &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + colorIntensity.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "plasma"
        public const val PARAM_SCALE: String = "scale"
        public const val PARAM_SPEED: String = "speed"
        public const val PARAM_COLOR_INTENSITY: String = "colorIntensity"
        public const val PARAM_ANIMATE: String = "animate"

        private val SHADER_SOURCE: String =
            """
            uniform shader content;
            uniform float2 resolution;
            uniform float scale;
            uniform float speed;
            uniform float colorIntensity;
            uniform float time;

            const float PI = 3.14159265;

            half4 main(float2 fragCoord) {
                half4 originalColor = content.eval(fragCoord);
                float2 uv = fragCoord / resolution;
                float x = uv.x * scale;
                float y = uv.y * scale;

                float v = sin(x + time * speed)
                        + sin(y + time * speed)
                        + sin((x + y) * scale * 0.5 + time * speed)
                        + sin(sqrt(x * x + y * y) * scale + time * speed);

                v *= 0.25;
                half3 plasmaColor = half3(
                    sin(v * PI * colorIntensity),
                    sin(v * PI * colorIntensity + 2.094),
                    sin(v * PI * colorIntensity + 4.188)
                );
                plasmaColor = plasmaColor * 0.5 + 0.5;

                half3 blended = mix(originalColor.rgb, plasmaColor, 0.7);
                return half4(blended, originalColor.a);
            }
            """.trimIndent()

        private val PARAMETER_HANDLERS: Map<String, ParamHandler<*>> =
            mapOf(
                PARAM_SCALE to
                    floatHandler<PlasmaEffect>(
                        spec =
                            FloatParameter(
                                id = PARAM_SCALE,
                                label = "Scale",
                                range = 1f..10f,
                                defaultValue = 4f,
                            ),
                        read = { it.scale },
                        write = { e, v ->
                            PlasmaEffect(
                                v,
                                e.speed,
                                e.colorIntensity,
                                e.animate,
                                e.time,
                            )
                        },
                    ),
                PARAM_SPEED to
                    floatHandler<PlasmaEffect>(
                        spec =
                            FloatParameter(
                                id = PARAM_SPEED,
                                label = "Speed",
                                range = 0.1f..5f,
                                defaultValue = 1f,
                            ),
                        read = { it.speed },
                        write = { e, v ->
                            PlasmaEffect(
                                e.scale,
                                v,
                                e.colorIntensity,
                                e.animate,
                                e.time,
                            )
                        },
                    ),
                PARAM_COLOR_INTENSITY to
                    floatHandler<PlasmaEffect>(
                        spec =
                            PercentageParameter(
                                id = PARAM_COLOR_INTENSITY,
                                label = "Color Intensity",
                                defaultValue = 1f,
                            ),
                        read = { it.colorIntensity },
                        write = { e, v -> PlasmaEffect(e.scale, e.speed, v, e.animate, e.time) },
                    ),
                PARAM_ANIMATE to
                    toggleHandler<PlasmaEffect>(
                        spec =
                            ToggleParameter(
                                id = PARAM_ANIMATE,
                                label = "Animate",
                                isEnabledByDefault = true,
                            ),
                        read = { it.animate },
                        write = { e, v ->
                            PlasmaEffect(
                                e.scale,
                                e.speed,
                                e.colorIntensity,
                                v,
                                e.time,
                            )
                        },
                    ),
            )
    }
}
