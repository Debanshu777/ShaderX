package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a wave distortion effect that can animate over time.
 *
 * Creates a wavy, liquid-like appearance.
 *
 * @property amplitude Maximum displacement in pixels
 * @property frequency Number of wave cycles across the image
 * @property animate Whether the effect should animate
 * @property time Current animation time in seconds
 */
public data class WaveEffect(
    private val amplitude: Float = 10f,
    private val frequency: Float = 5f,
    private val animate: Boolean = true,
    override val time: Float = 0f,
) : AnimatedShaderEffect {
    override val id: String = ID
    override val displayName: String = "Wave"
    override val isAnimating: Boolean = animate

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float amplitude;
        uniform float frequency;
        uniform float time;
        
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            
            // Apply wave distortion
            float xOffset = sin(uv.y * frequency + time) * amplitude;
            float yOffset = cos(uv.x * frequency + time) * amplitude;
            
            float2 distortedCoord = fragCoord + float2(xOffset, yOffset);
            
            // Clamp to valid range
            distortedCoord = clamp(distortedCoord, float2(0.0), resolution);
            
            return content.eval(distortedCoord);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PixelParameter(
                id = PARAM_AMPLITUDE,
                label = "Amplitude",
                range = 0f..50f,
                defaultValue = amplitude,
            ),
            FloatParameter(
                id = PARAM_FREQUENCY,
                label = "Frequency",
                range = 1f..20f,
                defaultValue = frequency,
            ),
            ToggleParameter(
                id = PARAM_ANIMATE,
                label = "Animate",
                isEnabledByDefault = animate,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("amplitude", amplitude),
            FloatUniform("frequency", frequency),
            FloatUniform("time", time),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): WaveEffect =
        when (parameterId) {
            PARAM_AMPLITUDE -> copy(amplitude = value)
            PARAM_FREQUENCY -> copy(frequency = value)
            PARAM_ANIMATE -> copy(animate = value > 0.5f)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): WaveEffect =
        when (parameterId) {
            PARAM_AMPLITUDE -> when (value) {
                is ParameterValue.FloatValue -> copy(amplitude = value.value)
                else -> this
            }
            PARAM_FREQUENCY -> when (value) {
                is ParameterValue.FloatValue -> copy(frequency = value.value)
                else -> this
            }
            PARAM_ANIMATE -> when (value) {
                is ParameterValue.BooleanValue -> copy(animate = value.enabled)
                is ParameterValue.FloatValue -> copy(animate = value.value > 0.5f)
                else -> this
            }
            else -> this
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_AMPLITUDE -> ParameterValue.FloatValue(amplitude)
            PARAM_FREQUENCY -> ParameterValue.FloatValue(frequency)
            PARAM_ANIMATE -> ParameterValue.BooleanValue(animate)
            else -> null
        }

    override fun withTime(newTime: Float): WaveEffect = copy(time = newTime)

    public companion object {
        public const val ID: String = "wave_distortion"
        public const val PARAM_AMPLITUDE: String = "amplitude"
        public const val PARAM_FREQUENCY: String = "frequency"
        public const val PARAM_ANIMATE: String = "animate"
    }
}





