package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.colorHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * A gradient overlay whose blend direction rotates smoothly over time.
 *
 * Demonstrates the simplest time-based animation pattern: a single `time` uniform
 * driving a periodic mix factor.
 */
@Immutable
public class AnimatedGradientEffect(
    public val startColor: Long = DEFAULT_START_COLOR,
    public val endColor: Long = DEFAULT_END_COLOR,
    public val speed: Float = 1f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Animated Gradient"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        layout(color) uniform half4 startColor;
        layout(color) uniform half4 endColor;
        uniform float speed;
        uniform float time;

        half4 main(float2 fragCoord) {
            half4 originalColor = content.eval(fragCoord);
            float2 uv = fragCoord / resolution;

            float angle = atan(uv.y - 0.5, uv.x - 0.5);
            float mixValue = sin(angle + time * speed) * 0.5 + 0.5;
            mixValue = clamp(mixValue, 0.0, 1.0);

            half4 gradientColor = mix(startColor, endColor, mixValue);
            half3 blendedRgb = mix(originalColor.rgb, gradientColor.rgb, 0.6);
            return half4(blendedRgb, originalColor.a);
        }
        """.trimIndent()

    override val parameterHandlers: Map<String, ParamHandler<*>> =
        mapOf(
            PARAM_START_COLOR to
                colorHandler<AnimatedGradientEffect>(
                    spec =
                        ColorParameter(
                            id = PARAM_START_COLOR,
                            label = "Start Color",
                            defaultColor = DEFAULT_START_COLOR,
                        ),
                    read = { it.startColor },
                    write = { e, v ->
                        AnimatedGradientEffect(
                            v,
                            e.endColor,
                            e.speed,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_END_COLOR to
                colorHandler<AnimatedGradientEffect>(
                    spec =
                        ColorParameter(
                            id = PARAM_END_COLOR,
                            label = "End Color",
                            defaultColor = DEFAULT_END_COLOR,
                        ),
                    read = { it.endColor },
                    write = { e, v ->
                        AnimatedGradientEffect(
                            e.startColor,
                            v,
                            e.speed,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_SPEED to
                floatHandler<AnimatedGradientEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_SPEED,
                            label = "Speed",
                            range = 0.1f..5f,
                            defaultValue = 1f,
                        ),
                    read = { it.speed },
                    write = { e, v ->
                        AnimatedGradientEffect(
                            e.startColor,
                            e.endColor,
                            v,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_ANIMATE to
                toggleHandler<AnimatedGradientEffect>(
                    spec =
                        ToggleParameter(
                            id = PARAM_ANIMATE,
                            label = "Animate",
                            isEnabledByDefault = true,
                        ),
                    read = { it.animate },
                    write = { e, v ->
                        AnimatedGradientEffect(
                            e.startColor,
                            e.endColor,
                            e.speed,
                            v,
                            e.time,
                        )
                    },
                ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            ColorUniform("startColor", startColor),
            ColorUniform("endColor", endColor),
            FloatUniform("speed", speed),
            FloatUniform("time", time),
        )

    override fun copyWithTime(newTime: Float): AnimatedGradientEffect =
        AnimatedGradientEffect(startColor, endColor, speed, animate, newTime)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimatedGradientEffect) return false
        return startColor == other.startColor &&
            endColor == other.endColor &&
            speed == other.speed &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = startColor.hashCode()
        result = 31 * result + endColor.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "animated_gradient"
        public const val PARAM_START_COLOR: String = "startColor"
        public const val PARAM_END_COLOR: String = "endColor"
        public const val PARAM_SPEED: String = "speed"
        public const val PARAM_ANIMATE: String = "animate"
        public const val DEFAULT_START_COLOR: Long = 0xFF7B2FF7
        public const val DEFAULT_END_COLOR: Long = 0xFF00D4FF
    }
}
