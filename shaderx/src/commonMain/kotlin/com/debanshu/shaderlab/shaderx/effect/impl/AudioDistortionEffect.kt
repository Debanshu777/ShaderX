package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.ShaderTileMode
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Audio-reactive **image distortion** driven by a live FFT [TextureUniform].
 *
 * Warps the composable's `content` layer (e.g. a photo) using spectrum magnitudes — it does not
 * draw a bar graph. For a spectrum graph, use [AudioVisualizerEffect].
 *
 * Prefer [withFftDataSource] with a stable effect instance for continuous mic/music capture.
 *
 * @property fftData Snapshot spectrum image (used when no live source is set).
 * @property fftSize Number of frequency bins (columns) in the spectrum texture.
 * @property intensity Distortion strength, 0..1.
 * @property animate Whether the effect is animating (drives the per-frame refresh loop).
 * @property time Animation time in seconds (excluded from equality).
 */
@Immutable
public class AudioDistortionEffect(
    public val fftData: ImageBitmap = EMPTY_FFT,
    public val fftSize: Float = DEFAULT_FFT_SIZE,
    public val intensity: Float = 1f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
    private val fftDataSource: (() -> ImageBitmap)? = null,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Audio Distortion"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String =
        """
        uniform shader content;
        uniform shader fftData;
        uniform float2 resolution;
        uniform float fftSize;
        uniform float intensity;
        uniform float time;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;

            float magnitude = fftData.eval(float2(uv.x * fftSize, 0.5)).r;
            float neighbour = fftData.eval(float2(fract(uv.x + 1.0 / fftSize) * fftSize, 0.5)).r;

            float strength = intensity * 0.45;
            float yPush = magnitude * strength * resolution.y;
            float xShear = (neighbour - magnitude) * strength * resolution.x;
            float wobble = sin(uv.y * 12.0 + time * 4.0) * magnitude * strength * 0.15 * resolution.x;

            float2 distorted = fragCoord + float2(xShear + wobble, -yPush);
            float2 sampleCoord = clamp(distorted, float2(0.0), resolution);
            return content.eval(sampleCoord);
        }
        """.trimIndent()

    override val parameterHandlers: Map<String, ParamHandler<*>> =
        mapOf(
            PARAM_INTENSITY to
                floatHandler<AudioDistortionEffect>(
                    spec =
                        PercentageParameter(
                            id = PARAM_INTENSITY,
                            label = "Distortion",
                            defaultValue = 1f,
                        ),
                    read = { it.intensity },
                    write = { e, v ->
                        AudioDistortionEffect(
                            e.fftData,
                            e.fftSize,
                            v,
                            e.animate,
                            e.time,
                            e.fftDataSource,
                        )
                    },
                ),
            PARAM_FFT_SIZE to
                floatHandler<AudioDistortionEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_FFT_SIZE,
                            label = "Frequency Bins",
                            range = 8f..512f,
                            defaultValue = DEFAULT_FFT_SIZE,
                        ),
                    read = { it.fftSize },
                    write = { e, v ->
                        AudioDistortionEffect(
                            e.fftData,
                            v,
                            e.intensity,
                            e.animate,
                            e.time,
                            e.fftDataSource,
                        )
                    },
                ),
            PARAM_ANIMATE to
                toggleHandler<AudioDistortionEffect>(
                    spec =
                        ToggleParameter(
                            id = PARAM_ANIMATE,
                            label = "Animate",
                            isEnabledByDefault = true,
                        ),
                    read = { it.animate },
                    write = { e, v ->
                        AudioDistortionEffect(
                            e.fftData,
                            e.fftSize,
                            e.intensity,
                            v,
                            e.time,
                            e.fftDataSource,
                        )
                    },
                ),
        )

    private fun resolveFftData(): ImageBitmap = fftDataSource?.invoke() ?: fftData

    public fun withFftData(data: ImageBitmap): AudioDistortionEffect =
        AudioDistortionEffect(data, fftSize, intensity, animate, time, fftDataSource = null)

    public fun withFftDataSource(source: () -> ImageBitmap): AudioDistortionEffect =
        AudioDistortionEffect(fftData, fftSize, intensity, animate, time, source)

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("fftSize", fftSize),
            FloatUniform("intensity", intensity),
            FloatUniform("time", time),
            TextureUniform("fftData", resolveFftData(), ShaderTileMode.CLAMP, ShaderTileMode.CLAMP),
        )

    override fun buildFrameUniforms(): List<Uniform> =
        listOf(
            FloatUniform("time", time),
            TextureUniform("fftData", resolveFftData(), ShaderTileMode.CLAMP, ShaderTileMode.CLAMP),
        )

    override fun copyWithTime(newTime: Float): AudioDistortionEffect =
        AudioDistortionEffect(fftData, fftSize, intensity, animate, newTime, fftDataSource)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioDistortionEffect) return false
        return fftSize == other.fftSize &&
            intensity == other.intensity &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = fftSize.hashCode()
        result = 31 * result + intensity.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "audioDistortion"
        public const val PARAM_INTENSITY: String = "intensity"
        public const val PARAM_FFT_SIZE: String = "fftSize"
        public const val PARAM_ANIMATE: String = "animate"
        public const val DEFAULT_FFT_SIZE: Float = 64f

        private val EMPTY_FFT: ImageBitmap = ImageBitmap(1, 1)
    }
}
