package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.ParameterMetadata
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.parameter.legacyFloat
import com.debanshu.shaderlab.shaderx.parameter.legacyPixels
import com.debanshu.shaderlab.shaderx.parameter.legacyToggle
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a wave distortion effect that can animate over time.
 *
 * Creates a wavy, liquid-like appearance.
 *
 * ### Equality contract
 * Two [WaveEffect] instances are equal if their **configuration** parameters are equal,
 * regardless of [time]. This allows animation loops to see the effect as stable across
 * frames — only the uniforms change, not the effect identity.
 *
 * The strict parameter API exposes [SCHEMA] for strict snapshots and edits. Its declarations also
 * supply legacy metadata and uniform names. Frequency is spatial, not playback speed;
 * strict edits preserve the stored [time] through the existing immutable copy behavior.
 * Compose may still recreate its session and restart its clock after configuration changes;
 * playback/session identity is unchanged in this phase.
 *
 * @property amplitude Maximum displacement in pixels
 * @property frequency Number of wave cycles across the image
 * @property animate Whether the effect should animate
 * @property time Current animation time in seconds (excluded from equality / hashCode)
 */
@Immutable
public class WaveEffect(
    public val amplitude: Float = AMPLITUDE.defaultValue,
    public val frequency: Float = FREQUENCY.defaultValue,
    public val animate: Boolean = ANIMATE.defaultValue,
    override val time: Float = 0f,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Wave"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String get() = SHADER_SOURCE

    override val parameterHandlers: Map<String, ParamHandler<*>> get() = PARAMETER_HANDLERS

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform(AMPLITUDE.key.id, amplitude),
            FloatUniform(FREQUENCY.key.id, frequency),
            FloatUniform("time", time),
        )

    // Keep the published 0.1.3 concrete descriptors, including data-class conveniences.
    override fun withTime(newTime: Float): WaveEffect = copyWithTime(newTime)

    override fun withTypedParameter(parameterId: String, value: ParameterValue): WaveEffect =
        super.withTypedParameter(parameterId, value) as WaveEffect

    public operator fun component1(): Float = amplitude
    public operator fun component2(): Float = frequency
    public operator fun component3(): Boolean = animate
    public operator fun component4(): Float = time

    public fun copy(
        amplitude: Float = this.amplitude,
        frequency: Float = this.frequency,
        animate: Boolean = this.animate,
        time: Float = this.time,
    ): WaveEffect = WaveEffect(amplitude, frequency, animate, time)

    override fun toString(): String =
        "WaveEffect(amplitude=$amplitude, frequency=$frequency, animate=$animate, time=$time)"

    override fun copyWithTime(newTime: Float): WaveEffect = WaveEffect(amplitude, frequency, animate, newTime)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveEffect) return false
        return amplitude == other.amplitude && frequency == other.frequency && animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = amplitude.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "wave_distortion"
        public const val PARAM_AMPLITUDE: String = "amplitude"
        public const val PARAM_FREQUENCY: String = "frequency"
        public const val PARAM_ANIMATE: String = "animate"

        /** Maximum displacement in pixels. */
        public val AMPLITUDE: FloatParameterDefinition = FloatParameterDefinition(
            ID, PARAM_AMPLITUDE, 10f, 0f..50f, ParameterMetadata("Amplitude", order = 0), unit = "px",
        )

        /** Spatial wave frequency, independent of the animation clock. */
        public val FREQUENCY: FloatParameterDefinition = FloatParameterDefinition(
            ID, PARAM_FREQUENCY, 5f, 1f..20f, ParameterMetadata("Frequency", order = 1),
        )

        /** Playback enable hint; immutable edits preserve stored effect time, not Compose session identity. */
        public val ANIMATE: BooleanParameterDefinition = BooleanParameterDefinition(
            ID, PARAM_ANIMATE, true, ParameterMetadata("Animate", order = 2, semanticRole = "playback.enabled"),
        )

        /** Shared strict declarations; equality still excludes time. */
        public val SCHEMA: ParameterSchema = ParameterSchema(ID, listOf(AMPLITUDE, FREQUENCY, ANIMATE))

        private val SHADER_SOURCE: String =
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

        private val PARAMETER_HANDLERS: Map<String, ParamHandler<*>> =
            mapOf(
                PARAM_AMPLITUDE to
                    floatHandler<WaveEffect>(
                        spec =
                            AMPLITUDE.legacyPixels(),
                        read = { it.amplitude },
                        write = { e, v -> WaveEffect(v, e.frequency, e.animate, e.time) },
                    ),
                PARAM_FREQUENCY to
                    floatHandler<WaveEffect>(
                        spec =
                            FREQUENCY.legacyFloat(),
                        read = { it.frequency },
                        write = { e, v -> WaveEffect(e.amplitude, v, e.animate, e.time) },
                    ),
                PARAM_ANIMATE to
                    toggleHandler<WaveEffect>(
                        spec =
                            ANIMATE.legacyToggle(),
                        read = { it.animate },
                        write = { e, v -> WaveEffect(e.amplitude, e.frequency, v, e.time) },
                    ),
            )
    }
}
