package com.debanshu.shaderlab.shaderx.effect

import androidx.compose.runtime.Stable
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Interface for shader effects that use custom AGSL/SkSL shader code.
 *
 * Runtime shaders allow for custom pixel manipulation using shader language.
 * The shader code is compiled at runtime and executed on the GPU.
 *
 * ## Shader Code Format
 * The shader code should follow AGSL (Android Graphics Shading Language) format,
 * which is compatible with SkSL on non-Android platforms:
 *
 * ```glsl
 * uniform shader content;
 * uniform float2 resolution;
 * uniform float intensity;
 *
 * half4 main(float2 fragCoord) {
 *     half4 color = content.eval(fragCoord);
 *     // Apply effect...
 *     return color;
 * }
 * ```
 *
 * ## Additional image inputs
 * Beyond the mandatory `content` layer, an effect may declare extra `uniform shader` inputs and
 * feed them a [androidx.compose.ui.graphics.ImageBitmap] via
 * [com.debanshu.shaderlab.shaderx.uniform.TextureUniform] — a lookup table, a mask, or per-frame
 * data such as audio FFT magnitudes. Child shaders sample in **pixel** coordinates:
 * ```glsl
 * uniform shader content;
 * uniform shader fftData;   // bound via TextureUniform("fftData", bitmap)
 * uniform float fftSize;
 *
 * half4 main(float2 fragCoord) {
 *     half m = fftData.eval(float2(0.5 * fftSize, 0.5)).r;
 *     return content.eval(fragCoord);
 * }
 * ```
 * For textures that change every frame, emit the [com.debanshu.shaderlab.shaderx.uniform.TextureUniform]
 * from [AnimatedShaderEffect.buildFrameUniforms] and exclude the image from equality — see
 * [com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect].
 *
 * @see AnimatedShaderEffect for effects that animate over time
 */
@Stable
public interface RuntimeShaderEffect : ShaderEffect {
    /**
     * The AGSL/SkSL shader source code.
     *
     * Must include a `main(float2 fragCoord)` function that returns `half4`.
     * The `content` shader uniform represents the input image.
     */
    public val shaderSource: String

    /**
     * Builds the list of uniforms to pass to the shader.
     *
     * Called each time the shader is rendered, allowing dynamic uniform values
     * based on the current dimensions and effect parameters.
     *
     * @param width The width of the render target in pixels
     * @param height The height of the render target in pixels
     * @return List of [Uniform] values to pass to the shader
     */
    public fun buildUniforms(width: Float, height: Float): List<Uniform>

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): RuntimeShaderEffect
}
