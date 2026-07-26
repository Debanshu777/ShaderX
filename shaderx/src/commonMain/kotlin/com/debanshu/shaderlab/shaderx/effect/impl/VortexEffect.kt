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
 * Spiral/twirl distortion that rotates pixels around the center over time.
 *
 * Converts UV to polar coordinates, applies a time-varying rotation weighted by distance,
 * then samples the original content at the transformed position.
 */
@Immutable
public class VortexEffect(
    public val strength: Float = 1f,
    public val radius: Float = 0.5f,
    public val speed: Float = 1f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Vortex"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float strength;
        uniform float radius;
        uniform float speed;
        uniform float time;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - float2(0.5, 0.5);
            float dist = length(centered);

            float angle = atan(centered.y, centered.x);
            float twirl = strength * (1.0 - smoothstep(radius, 0.0, dist)) * sin(time * speed);
            angle += twirl;

            float2 twisted = float2(cos(angle), sin(angle)) * dist + float2(0.5, 0.5);
            float2 sampleCoord = twisted * resolution;
            sampleCoord = clamp(sampleCoord, float2(0.0), resolution);

            return content.eval(sampleCoord);
        }
        """.trimIndent()

    override val parameterHandlers: Map<String, ParamHandler<*>> =
        mapOf(
            PARAM_STRENGTH to
                floatHandler<VortexEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_STRENGTH,
                            label = "Strength",
                            range = 0.1f..5f,
                            defaultValue = 1f,
                        ),
                    read = { it.strength },
                    write = { e, v -> VortexEffect(v, e.radius, e.speed, e.animate, e.time) },
                ),
            PARAM_RADIUS to
                floatHandler<VortexEffect>(
                    spec =
                        PercentageParameter(
                            id = PARAM_RADIUS,
                            label = "Radius",
                            defaultValue = 0.5f,
                        ),
                    read = { it.radius },
                    write = { e, v -> VortexEffect(e.strength, v, e.speed, e.animate, e.time) },
                ),
            PARAM_SPEED to
                floatHandler<VortexEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_SPEED,
                            label = "Speed",
                            range = 0.1f..5f,
                            defaultValue = 1f,
                        ),
                    read = { it.speed },
                    write = { e, v ->
                        VortexEffect(
                            e.strength,
                            e.radius,
                            v,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_ANIMATE to
                toggleHandler<VortexEffect>(
                    spec =
                        ToggleParameter(
                            id = PARAM_ANIMATE,
                            label = "Animate",
                            isEnabledByDefault = true,
                        ),
                    read = { it.animate },
                    write = { e, v -> VortexEffect(e.strength, e.radius, e.speed, v, e.time) },
                ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("strength", strength),
            FloatUniform("radius", radius),
            FloatUniform("speed", speed),
            FloatUniform("time", time),
        )

    override fun copyWithTime(newTime: Float): VortexEffect = VortexEffect(strength, radius, speed, animate, newTime)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VortexEffect) return false
        return strength == other.strength &&
            radius == other.radius &&
            speed == other.speed &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = strength.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "vortex"
        public const val PARAM_STRENGTH: String = "strength"
        public const val PARAM_RADIUS: String = "radius"
        public const val PARAM_SPEED: String = "speed"
        public const val PARAM_ANIMATE: String = "animate"
    }
}
