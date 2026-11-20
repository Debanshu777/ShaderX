package shaderx.consumer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

// These sources are compiled in independent Android and KMP consumers with no friend paths.
class MinimalEffect : RuntimeShaderEffect {
    override val id = "external_minimal"
    override val displayName = "External minimal"
    override val parameters: List<ParameterSpec> = emptyList()
    override val shaderSource = "uniform shader content; half4 main(float2 p) { return content.eval(p); }"
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = emptyList()
    override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
}

data class EditableEffect(val amount: Float = 0.5f) : RuntimeShaderEffect {
    override val id = "external_editable"
    override val displayName = "External editable"
    override val parameters: List<ParameterSpec> = listOf(FloatParameter("amount", "Amount", 0f..1f, 0.5f))
    override val shaderSource = """
        uniform shader content;
        uniform float amount;
        half4 main(float2 p) { return content.eval(p) * amount; }
    """.trimIndent()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(FloatUniform("amount", amount))
    override fun withTypedParameter(parameterId: String, value: ParameterValue): EditableEffect {
        if (parameterId != "amount") return this
        require(value is ParameterValue.FloatValue)
        return copy(amount = value.value.coerceIn(0f, 1f))
    }
    override fun getParameterValue(parameterId: String) = if (parameterId == "amount") amount else 0f
    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        if (parameterId == "amount") ParameterValue.FloatValue(amount) else null
}

// Time is deliberately excluded from identity, matching the current session contract.
class AnimatedEffect(override val time: Float = 0f, override val isAnimating: Boolean = true) : AnimatedShaderEffect {
    override val id = "external_animated"
    override val displayName = "External animated"
    override val parameters: List<ParameterSpec> = emptyList()
    override val shaderSource = """
        uniform shader content;
        uniform float time;
        half4 main(float2 p) { return content.eval(p) * (0.5 + 0.5 * sin(time)); }
    """.trimIndent()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(FloatUniform("time", time))
    override fun withTime(newTime: Float) = AnimatedEffect(newTime, isAnimating)
    override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
    override fun equals(other: Any?) = other is AnimatedEffect && isAnimating == other.isAnimating
    override fun hashCode() = isAnimating.hashCode()
}

@Composable
fun ConsumerContent(effect: ShaderEffect, factory: ShaderFactory) {
    Box(Modifier.shaderEffect(effect, factory))
}
