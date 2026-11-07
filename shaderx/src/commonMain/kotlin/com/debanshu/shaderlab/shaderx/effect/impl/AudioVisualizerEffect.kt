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
 * Spectrum **bar graph** driven by a live FFT [TextureUniform].
 *
 * Renders frequency-bin bars (typically on a solid background). For audio-reactive **image**
 * warping, use [AudioDistortionEffect] instead.
 *
 * Prefer [withFftDataSource] with a stable effect instance for continuous mic/music capture.
 *
 * @property fftData Snapshot spectrum image (used when no live source is set).
 * @property fftSize Number of frequency bins (columns) in the spectrum texture.
 * @property barColorIntensity Brightness of the rendered bars, 0..1.
 * @property animate Whether the effect is animating (drives the per-frame refresh loop).
 * @property time Animation time in seconds (excluded from equality).
 */
@Immutable
public class AudioVisualizerEffect(
    public val fftData: ImageBitmap = EMPTY_FFT,
    public val fftSize: Float = DEFAULT_FFT_SIZE,
    public val barColorIntensity: Float = 1f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
    private val fftDataSource: (() -> ImageBitmap)? = null,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Audio Visualizer"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String get() = SHADER_SOURCE

    override val parameterHandlers: Map<String, ParamHandler<*>> get() = PARAMETER_HANDLERS

    private fun resolveFftData(): ImageBitmap = fftDataSource?.invoke() ?: fftData

    public fun withFftData(data: ImageBitmap): AudioVisualizerEffect =
        AudioVisualizerEffect(data, fftSize, barColorIntensity, animate, time, fftDataSource = null)

    public fun withFftDataSource(source: () -> ImageBitmap): AudioVisualizerEffect =
        AudioVisualizerEffect(fftData, fftSize, barColorIntensity, animate, time, source)

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("fftSize", fftSize),
            FloatUniform("barColorIntensity", barColorIntensity),
            FloatUniform("time", time),
            TextureUniform("fftData", resolveFftData(), ShaderTileMode.CLAMP, ShaderTileMode.CLAMP),
        )

    override fun buildFrameUniforms(): List<Uniform> =
        listOf(
            FloatUniform("time", time),
            TextureUniform("fftData", resolveFftData(), ShaderTileMode.CLAMP, ShaderTileMode.CLAMP),
        )

    override fun copyWithTime(newTime: Float): AudioVisualizerEffect =
        AudioVisualizerEffect(fftData, fftSize, barColorIntensity, animate, newTime, fftDataSource)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioVisualizerEffect) return false
        return fftSize == other.fftSize &&
            barColorIntensity == other.barColorIntensity &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = fftSize.hashCode()
        result = 31 * result + barColorIntensity.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "audioVisualizer"
        public const val PARAM_BAR_COLOR_INTENSITY: String = "barColorIntensity"
        public const val PARAM_FFT_SIZE: String = "fftSize"
        public const val PARAM_ANIMATE: String = "animate"
        public const val DEFAULT_FFT_SIZE: Float = 64f

        private val EMPTY_FFT: ImageBitmap = ImageBitmap(1, 1)

        private val SHADER_SOURCE: String =
            """
            uniform shader content;
            uniform shader fftData;
            uniform float2 resolution;
            uniform float fftSize;
            uniform float barColorIntensity;
            uniform float time;

            half4 main(float2 fragCoord) {
                half4 originalColor = content.eval(fragCoord);
                float2 uv = fragCoord / resolution;

                float magnitude = fftData.eval(float2(uv.x * fftSize, 0.5)).r;

                // Bars grow from the bottom of the frame upwards.
                float barTop = 1.0 - magnitude;
                float inBar = step(barTop, uv.y);
                float barAlpha = inBar * 0.85;

                half3 barColor = half3(
                    0.5 + 0.5 * sin(uv.x * 6.2831 + time),
                    0.5 + 0.5 * sin(uv.x * 6.2831 + time + 2.094),
                    0.5 + 0.5 * sin(uv.x * 6.2831 + time + 4.188)
                ) * barColorIntensity;

                half3 blended = mix(originalColor.rgb, barColor, barAlpha);
                half outAlpha = max(originalColor.a, half(barAlpha));
                return half4(blended, outAlpha);
            }
            """.trimIndent()

        private val PARAMETER_HANDLERS: Map<String, ParamHandler<*>> =
            mapOf(
                PARAM_BAR_COLOR_INTENSITY to
                    floatHandler<AudioVisualizerEffect>(
                        spec =
                            PercentageParameter(
                                id = PARAM_BAR_COLOR_INTENSITY,
                                label = "Bar Intensity",
                                defaultValue = 1f,
                            ),
                        read = { it.barColorIntensity },
                        write = { e, v ->
                            AudioVisualizerEffect(
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
                    floatHandler<AudioVisualizerEffect>(
                        spec =
                            FloatParameter(
                                id = PARAM_FFT_SIZE,
                                label = "Frequency Bins",
                                range = 8f..512f,
                                defaultValue = DEFAULT_FFT_SIZE,
                            ),
                        read = { it.fftSize },
                        write = { e, v ->
                            AudioVisualizerEffect(
                                e.fftData,
                                v,
                                e.barColorIntensity,
                                e.animate,
                                e.time,
                                e.fftDataSource,
                            )
                        },
                    ),
                PARAM_ANIMATE to
                    toggleHandler<AudioVisualizerEffect>(
                        spec =
                            ToggleParameter(
                                id = PARAM_ANIMATE,
                                label = "Animate",
                                isEnabledByDefault = true,
                            ),
                        read = { it.animate },
                        write = { e, v ->
                            AudioVisualizerEffect(
                                e.fftData,
                                e.fftSize,
                                e.barColorIntensity,
                                v,
                                e.time,
                                e.fftDataSource,
                            )
                        },
                    ),
            )
    }
}
