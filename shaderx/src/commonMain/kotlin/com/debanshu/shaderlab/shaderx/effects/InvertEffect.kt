package com.debanshu.shaderlab.shaderx.effects

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Inverts all colors in the image.
 *
 * Each color channel is inverted: newValue = 1.0 - originalValue
 * Alpha channel is preserved.
 */
public data object InvertEffect : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Invert"

    override val shaderSource: String =
        """
        uniform shader content;
        
        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            return half4(1.0 - color.rgb, color.a);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> = emptyList()

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> = emptyList()

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): InvertEffect = this

    public const val ID: String = "color_inversion"
}
